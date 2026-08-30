package com.example.aistock_prices.stock.ai

import com.example.aistock_prices.stock.data.KLineBar
import com.example.aistock_prices.stock.data.MinutePoint
import com.example.aistock_prices.stock.data.StockQuote
import com.example.aistock_prices.stock.ui.StockFormat
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * AI 分析服务：
 * - 配置的读取/保存（SharedPreferencesModule 持久化，Key 运行时在应用内输入）
 * - 调用 OpenAI 兼容的 chat/completions 接口
 * - LLM 不可用时降级为本地规则生成（保证演示链路不中断）
 */
object AiAnalysisService {

    private const val KEY_BASE_URL = "ai_base_url"
    private const val KEY_API_KEY = "ai_api_key"
    private const val KEY_MODEL = "ai_model"
    private const val KEY_PRESETS = "ai_presets"

    // ==================== 配置存取 ====================

    fun loadConfig(sp: SharedPreferencesModule): AiConfig {
        return AiConfig(
            baseUrl = sp.getItem(KEY_BASE_URL),
            apiKey = sp.getItem(KEY_API_KEY),
            model = sp.getItem(KEY_MODEL)
        )
    }

    fun saveConfig(sp: SharedPreferencesModule, config: AiConfig) {
        sp.setItem(KEY_BASE_URL, config.baseUrl.trim())
        sp.setItem(KEY_API_KEY, config.apiKey.trim())
        sp.setItem(KEY_MODEL, config.model.trim())
    }

    // ==================== 预设存取 ====================

    fun loadPresets(sp: SharedPreferencesModule): List<AiPreset> {
        val raw = sp.getItem(KEY_PRESETS)
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name") ?: return@mapNotNull null
                if (name.isBlank()) return@mapNotNull null
                AiPreset(
                    name = name,
                    baseUrl = o.optString("baseUrl") ?: "",
                    apiKey = o.optString("apiKey") ?: "",
                    model = o.optString("model") ?: ""
                )
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun savePresets(sp: SharedPreferencesModule, list: List<AiPreset>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("name", p.name)
                    put("baseUrl", p.baseUrl)
                    put("apiKey", p.apiKey)
                    put("model", p.model)
                }
            )
        }
        sp.setItem(KEY_PRESETS, JSONObject().apply { put("list", arr) }.toString())
    }

    /** 保存配置时自动以模型名创建/更新预设（同 baseUrl+apiKey 则更新；可指定预设名） */
    fun upsertPreset(sp: SharedPreferencesModule, config: AiConfig, name: String? = null) {
        val list = loadPresets(sp).toMutableList()
        val idx = list.indexOfFirst { it.baseUrl == config.baseUrl && it.apiKey == config.apiKey }
        val preset = AiPreset(
            name = name?.trim()?.takeIf { it.isNotBlank() }
                ?: config.model.trim().ifBlank { "未命名" },
            baseUrl = config.baseUrl.trim(),
            apiKey = config.apiKey.trim(),
            model = config.model.trim()
        )
        if (idx >= 0) list[idx] = preset else list.add(preset)
        savePresets(sp, list)
    }

    /** 删除预设 */
    fun removePreset(sp: SharedPreferencesModule, preset: AiPreset) {
        val list = loadPresets(sp).filterNot {
            it.baseUrl == preset.baseUrl && it.apiKey == preset.apiKey && it.name == preset.name
        }
        savePresets(sp, list)
    }

    /** 更新指定预设（按预设名匹配；保存后同样写入当前生效配置） */
    fun updatePreset(sp: SharedPreferencesModule, oldName: String, newPreset: AiPreset) {
        val list = loadPresets(sp).toMutableList()
        val idx = list.indexOfFirst { it.name == oldName }
        if (idx >= 0) {
            list[idx] = newPreset
        } else {
            list.add(newPreset)
        }
        savePresets(sp, list)
        saveConfig(sp, AiConfig(newPreset.baseUrl, newPreset.apiKey, newPreset.model))
    }

    // ==================== 自动读取模型 ====================

    /** 从 baseUrl 推导 models 接口地址（兼容用户粘贴完整 chat/completions 地址） */
    fun modelsUrl(base: String): String {
        var url = base.trim().trimEnd('/')
        if (url.endsWith("/chat/completions")) {
            url = url.removeSuffix("/chat/completions")
        }
        return url + "/models"
    }

    /** 从 baseUrl 推导 chat/completions 接口地址（兼容用户只填 v1 根地址） */
    fun chatUrl(base: String): String {
        var url = base.trim().trimEnd('/')
        if (!url.endsWith("/chat/completions")) {
            url += "/chat/completions"
        }
        return url
    }

    /**
     * 调用 GET {base}/models 自动读取可用模型列表。
     * 回调参数：(模型列表, 错误信息)；成功时错误信息为 null，失败时携带可读原因（HTTP 状态码/网络错误/格式不符）。
     */
    fun fetchModels(
        network: NetworkModule,
        baseUrl: String,
        apiKey: String,
        callback: (List<String>, String?) -> Unit
    ) {
        val url = modelsUrl(baseUrl)
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            put("Authorization", "Bearer $apiKey")
        }
        network.httpRequest(url, false, JSONObject(), headers, null, 30) { data, success, errorMsg, response ->
            if (!success) {
                val status = response.statusCode
                val msg = when {
                    status != null && status > 0 -> "HTTP $status" + if (errorMsg.isNotBlank()) "：$errorMsg" else ""
                    errorMsg.isNotBlank() -> errorMsg
                    else -> "网络请求失败（可能无法连接服务器）"
                }
                callback(emptyList(), msg)
                return@httpRequest
            }
            val arr = data.optJSONArray("data")
            if (arr == null) {
                val raw = data.toString().replace("\n", " ").take(100)
                callback(emptyList(), "响应格式非 OpenAI 兼容（缺少 data 数组）：$raw")
                return@httpRequest
            }
            val models = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id") }
            if (models.isEmpty()) callback(emptyList(), "服务端返回的模型列表为空")
            else callback(models, null)
        }
    }

    // ==================== LLM 调用 ====================

    /**
     * 调用 LLM 生成分析；成功且解析出结构化 JSON 则回调 [AiAnalysisResult]，
     * 否则降级为本地规则结果（source = 本地规则）。
     */
    fun analyze(
        network: NetworkModule,
        config: AiConfig,
        quote: StockQuote,
        minute: List<MinutePoint>,
        kline: List<KLineBar>,
        callback: (AiAnalysisResult) -> Unit
    ) {
        val body = buildRequestBody(config, quote, minute, kline)
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            put("Authorization", "Bearer ${config.apiKey}")
        }
        network.httpRequest(chatUrl(config.baseUrl), true, body, headers, null, 60) { data, success, _, _ ->
            if (!success) {
                callback(localFallback(quote, kline))
                return@httpRequest
            }
            val content = data.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            parseResult(content)?.let {
                callback(it)
            } ?: callback(localFallback(quote, kline))
        }
    }

    private fun buildRequestBody(
        config: AiConfig,
        quote: StockQuote,
        minute: List<MinutePoint>,
        kline: List<KLineBar>
    ): JSONObject {
        val messages = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put(
                        "content",
                        "你是一名专业的A股技术分析助手。请基于用户提供的行情数据，用中文输出结构化JSON分析，" +
                                "不要输出JSON以外的任何内容。JSON格式：" +
                                "{\"trend\":\"看多/看空/震荡等标签\",\"trendDesc\":\"趋势判断说明\",\"suggestion\":\"操作建议\"," +
                                "\"buyPoints\":[\"买入参考点位1\",\"买入参考点位2\"],\"sellPoints\":[\"卖出参考点位1\"]," +
                                "\"risks\":[\"风险提醒1\",\"风险提醒2\"],\"riskLevel\":\"低/中/高（对当前风险的综合评级，必须三选一）\"," +
                                "\"summary\":\"行情总结\"}"
                    )
                }
            )
            put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", buildUserPrompt(quote, minute, kline))
                }
            )
        }
        return JSONObject().apply {
            put("model", config.model)
            put("messages", messages)
            put("temperature", 0.7)
        }
    }

    private fun buildUserPrompt(
        quote: StockQuote,
        minute: List<MinutePoint>,
        kline: List<KLineBar>
    ): String {
        val sb = StringBuilder()
        sb.append("股票：${quote.name}(${quote.symbol})\n")
        sb.append("最新价：${StockFormat.price(quote.price)}，涨跌额：${StockFormat.change(quote.change)}，" +
                "涨跌幅：${StockFormat.percent(quote.changePercent)}\n")
        sb.append("今开：${StockFormat.price(quote.open)}，昨收：${StockFormat.price(quote.prevClose)}，" +
                "最高：${StockFormat.price(quote.high)}，最低：${StockFormat.price(quote.low)}\n")
        sb.append("成交量：${StockFormat.volume(quote.volume)}，成交额：${StockFormat.amount(quote.amount)}，" +
                "换手率：${StockFormat.price(quote.turnover)}%，振幅：${StockFormat.price(quote.amplitude)}%，均价：${StockFormat.price(quote.avgPrice)}\n")
        if (minute.isNotEmpty()) {
            val first = minute.first()
            val last = minute.last()
            sb.append("分时：${first.time} ${StockFormat.price(first.price)} → ${last.time} ${StockFormat.price(last.price)}，" +
                    "共${minute.size}个采样点\n")
        }
        if (kline.isNotEmpty()) {
            val recent = kline.takeLast(10)
            sb.append("近10日收盘：")
            sb.append(recent.joinToString(", ") { "${it.date.substring(5)}:${StockFormat.price(it.close)}" })
            sb.append("\n")
            val lows = kline.map { it.low }
            val highs = kline.map { it.high }
            sb.append("近${kline.size}日最低：${StockFormat.price(lows.min())}，最高：${StockFormat.price(highs.max())}\n")
        }
        sb.append("\n请输出买卖参考点位（用具体价格）、操作提示、趋势判断、风险提醒和行情总结。")
        return sb.toString()
    }

    /** 解析模型返回的 JSON 分析内容 */
    private fun parseResult(content: String): AiAnalysisResult? {
        if (content.isBlank()) return null
        val json = try {
            JSONObject(content)
        } catch (e: Throwable) {
            return null
        }
        return AiAnalysisResult(
            trend = json.optString("trend", ""),
            trendDesc = json.optString("trendDesc", ""),
            suggestion = json.optString("suggestion", ""),
            buyPoints = json.optJSONArray("buyPoints")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it) }
            } ?: emptyList(),
            sellPoints = json.optJSONArray("sellPoints")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it) }
            } ?: emptyList(),
            risks = json.optJSONArray("risks")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it) }
            } ?: emptyList(),
            summary = json.optString("summary", ""),
            riskLevel = json.optString("riskLevel", AiAnalysisResult.RISK_MID),
            source = AiAnalysisResult.SOURCE_AI
        )
    }

    // ==================== 本地规则降级 ====================

    /** 基于真实行情数据的本地规则生成（确定性，不依赖网络） */
    fun localFallback(quote: StockQuote, kline: List<KLineBar>): AiAnalysisResult {
        val pct = quote.changePercent
        val trend = when {
            pct >= 2.0 -> "强势看多"
            pct > 0.0 -> "温和看多"
            pct <= -2.0 -> "弱势看空"
            pct < 0.0 -> "温和看空"
            else -> "区间震荡"
        }
        val trendDesc = "${quote.name}当前报${StockFormat.price(quote.price)}，" +
                "较昨收${StockFormat.percent(pct)}（A股红涨绿跌），" +
                "今日振幅${StockFormat.price(quote.amplitude)}%。"

        val avg = quote.avgPrice
        val buyPoints = mutableListOf<String>()
        if (avg > 0) {
            buyPoints.add("回踩均价${StockFormat.price(avg)}附近可分批关注")
        }
        if (kline.isNotEmpty()) {
            val low20 = kline.takeLast(20).minOfOrNull { it.low } ?: quote.low
            buyPoints.add("若回落至近20日低点${StockFormat.price(low20)}附近企稳，可考虑低吸")
        }
        if (buyPoints.isEmpty()) buyPoints.add("关注今日低点${StockFormat.price(quote.low)}附近的支撑")

        val sellPoints = mutableListOf<String>()
        if (kline.isNotEmpty()) {
            val high20 = kline.takeLast(20).maxOfOrNull { it.high } ?: quote.high
            sellPoints.add("反弹至近20日高点${StockFormat.price(high20)}附近可考虑减仓")
        }
        sellPoints.add("若放量跌破昨收${StockFormat.price(quote.prevClose)}，注意短期回调风险")
        if (sellPoints.isEmpty()) sellPoints.add("关注上方压力位${StockFormat.price(quote.high)}")

        val risks = mutableListOf<String>()
        if (quote.amplitude >= 5.0) risks.add("今日振幅${StockFormat.price(quote.amplitude)}%，波动明显加剧，注意仓位控制")
        if (quote.turnover >= 10.0) risks.add("换手率${StockFormat.price(quote.turnover)}%，交投过热，谨防冲高回落")
        if (pct < 0) risks.add("股价跌破昨收，短线情绪偏弱，追高风险较大")
        if (risks.isEmpty()) risks.add("当前量价平稳，无明显极端风险信号，但仍需关注大盘系统性波动")

        val summary = "${quote.name}今日开于${StockFormat.price(quote.open)}，" +
                "最高${StockFormat.price(quote.high)}，最低${StockFormat.price(quote.low)}，" +
                "收于${StockFormat.price(quote.price)}，较昨收${StockFormat.percent(pct)}，" +
                "成交${StockFormat.volume(quote.volume)}，换手${StockFormat.price(quote.turnover)}%。" +
                if (pct > 0) "量价配合下短线偏强，注意高位获利了结节奏。" else "弱势整理中宜观望，等待企稳信号。"

        val suggestion = when {
            pct >= 2.0 -> "短线偏强，可持股待涨，回踩不破均价线可加仓，切忌追高"
            pct > 0.0 -> "温和上行，持股为主，回调至均价线附近可关注"
            pct <= -2.0 -> "弱势明显，建议观望或减仓，等待止跌信号"
            pct < 0.0 -> "短线承压，控制仓位，暂不急于抄底"
            else -> "多空平衡，建议区间操作，高抛低吸"
        }

        // 风险等级推导：振幅/换手率/跌幅综合
        val riskLevel = when {
            quote.amplitude >= 5.0 || quote.turnover >= 10.0 -> AiAnalysisResult.RISK_HIGH
            quote.amplitude >= 3.0 || quote.turnover >= 6.0 -> AiAnalysisResult.RISK_MID
            else -> AiAnalysisResult.RISK_LOW
        }

        return AiAnalysisResult(
            trend = trend,
            trendDesc = trendDesc,
            suggestion = suggestion,
            buyPoints = buyPoints,
            sellPoints = sellPoints,
            risks = risks,
            summary = summary,
            riskLevel = riskLevel,
            source = AiAnalysisResult.SOURCE_RULE
        )
    }
}
