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
    private const val KEY_ACTIVE_PRESET = "ai_active_preset"

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

    /** 当前生效的预设名（用于列表高亮「当前」标记；无则为空串） */
    fun activePresetName(sp: SharedPreferencesModule): String = sp.getItem(KEY_ACTIVE_PRESET)

    /** 设置当前生效预设名（空串 = 清除标记） */
    fun setActivePreset(sp: SharedPreferencesModule, name: String) {
        sp.setItem(KEY_ACTIVE_PRESET, name.trim())
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
                    model = o.optString("model") ?: "",
                    // 兼容存量数据：failed 的预设强制视为未启用（旧版本可能误存 enabled=true）
                    enabled = o.optBoolean("enabled", true) && !o.optBoolean("failed", false),
                    failed = o.optBoolean("failed", false)
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
                    put("enabled", p.enabled)
                    put("failed", p.failed)
                }
            )
        }
        sp.setItem(KEY_PRESETS, JSONObject().apply { put("list", arr) }.toString())
    }

    /** 当前启用中的预设：AI 服务是否可用的判定基准（停用/删除后即视为未配置） */
    fun activePreset(sp: SharedPreferencesModule): AiPreset? = loadPresets(sp).firstOrNull { it.enabled }

    /**
     * 保存预设：以「预设名」为唯一键——不同预设名（即使 URL/Key/模型相同）作为新预设新增，
     * 仅同名时更新（列表层已保证同名互斥，这里兜底替换）。
     * @param enabled 是否启用该预设（连接成功才应传 true；false 时不设为当前生效配置）
     */
    fun upsertPreset(sp: SharedPreferencesModule, config: AiConfig, name: String? = null, enabled: Boolean = true) {
        val list = loadPresets(sp).toMutableList()
        val preset = AiPreset(
            name = name?.trim()?.takeIf { it.isNotBlank() }
                ?: config.model.trim().ifBlank { "未命名" },
            baseUrl = config.baseUrl.trim(),
            apiKey = config.apiKey.trim(),
            model = config.model.trim(),
            enabled = enabled
        )
        val idx = list.indexOfFirst { it.name == preset.name }
        if (idx >= 0) list[idx] = preset else list.add(preset)
        // 互斥：新预设启用时关闭其他所有预设（保证同时最多一个开启）
        if (enabled) disableOtherEnabled(list, preset.name)
        savePresets(sp, list)
        if (enabled) {
            saveConfig(sp, AiConfig(preset.baseUrl, preset.apiKey, preset.model))
            setActivePreset(sp, preset.name)
        }
    }

    /** 删除预设（按预设名唯一匹配）；若删除的是当前生效预设则同步清除生效标记 */
    fun removePreset(sp: SharedPreferencesModule, preset: AiPreset) {
        val list = loadPresets(sp).filterNot { it.name == preset.name }
        savePresets(sp, list)
        if (activePresetName(sp) == preset.name) {
            setActivePreset(sp, "")
        }
    }

    /**
     * 更新指定预设（按预设名匹配）。
     * 连接失败（failed）的预设不允许启用：强制 enabled=false、不写入当前生效配置，
     * 若原本是当前生效则清除标记。
     */
    fun updatePreset(sp: SharedPreferencesModule, oldName: String, newPreset: AiPreset) {
        val list = loadPresets(sp).toMutableList()
        val idx = list.indexOfFirst { it.name == oldName }
        if (newPreset.failed) {
            val saved = newPreset.copy(enabled = false)
            if (idx >= 0) list[idx] = saved else list.add(saved)
            savePresets(sp, list)
            if (activePresetName(sp) == oldName || activePresetName(sp) == saved.name) {
                setActivePreset(sp, "")
            }
            return
        }
        if (idx >= 0) {
            list[idx] = newPreset
        } else {
            list.add(newPreset)
        }
        // 互斥：更新后的预设若为启用态，关闭其他全部预设
        if (newPreset.enabled) disableOtherEnabled(list, newPreset.name)
        savePresets(sp, list)
        saveConfig(sp, AiConfig(newPreset.baseUrl, newPreset.apiKey, newPreset.model))
        setActivePreset(sp, newPreset.name)
    }

    /**
     * 互斥保证：将 [keepName] 之外所有已启用的预设置停用，
     * 使整个列表任何时刻最多只有一个预设处于启用态。
     */
    private fun disableOtherEnabled(list: MutableList<AiPreset>, keepName: String) {
        list.forEachIndexed { i, p ->
            if (p.name != keepName && p.enabled) list[i] = p.copy(enabled = false)
        }
    }

    /** 切换预设启用状态；启用时自动关闭其他预设，并将该预设设为当前生效配置 */
    fun setPresetEnabled(sp: SharedPreferencesModule, name: String, enabled: Boolean) {
        val list = loadPresets(sp).toMutableList()
        val idx = list.indexOfFirst { it.name == name }
        if (idx < 0) return
        val p = list[idx]
        // 连接失败的预设不允许启用
        if (enabled && p.failed) return
        if (enabled) {
            // 互斥：开启当前预设，同时关闭其他全部预设
            disableOtherEnabled(list, name)
            list[idx] = p.copy(enabled = true)
            savePresets(sp, list)
            saveConfig(sp, AiConfig(p.baseUrl, p.apiKey, p.model))
            setActivePreset(sp, p.name)
        } else {
            list[idx] = p.copy(enabled = false)
            savePresets(sp, list)
            if (activePresetName(sp) == p.name) {
                setActivePreset(sp, "")
            }
        }
    }

    /**
     * 标记预设连接校验失败（true=标红；false=清除标红）。
     * 连接失败时强制停用该预设并清除「当前生效」标记（失败配置不能作为生效配置）。
     */
    fun setPresetFailed(sp: SharedPreferencesModule, name: String, failed: Boolean) {
        val list = loadPresets(sp).toMutableList()
        val idx = list.indexOfFirst { it.name == name }
        if (idx < 0) return
        if (failed) {
            list[idx] = list[idx].copy(failed = true, enabled = false)
            if (activePresetName(sp) == name) setActivePreset(sp, "")
        } else {
            list[idx] = list[idx].copy(failed = false)
        }
        savePresets(sp, list)
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

    /**
     * 连接测试：请求 {base}/models 判断 URL/Key 是否可用。
     * 回调：(是否连接成功, 错误信息)；成功时错误信息为 null。
     */
    fun testConnection(
        network: NetworkModule,
        baseUrl: String,
        apiKey: String,
        callback: (Boolean, String?) -> Unit
    ) {
        fetchModels(network, baseUrl, apiKey) { models, err ->
            if (err == null) callback(true, null)
            else callback(false, err)
        }
    }

    // ==================== LLM 调用 ====================

    /** AI 问答 system prompt:约定 Markdown 输出与股票卡片标记格式 */
    private const val CHAT_SYSTEM_PROMPT =
        "你是一名专业的A股分析助手,用户会和你讨论具体股票或指数。" +
                "回复请使用 Markdown 格式(支持标题/加粗/列表/表格等)。" +
                "【stock 标记规则】仅当用户的本轮消息中明确提到具体股票/指数的名称或 6 位代码时," +
                "才在回复末尾用如下格式的代码块点名它(客户端会将其渲染为实时行情卡片+迷你走势图):\n" +
                "```stock\nsh600519 贵州茅台\n```\n" +
                "代码需带市场前缀(sh/sz/bj/hk),一行一个,格式为\"代码 名称\"。" +
                "若提到多只则分行放多个。\n" +
                "【重要】闲聊/问候/方法论提问/未提及具体股票或指数时,**不要**输出 stock 代码块——不要为了「占位」而凭空列股票。" +
                "用户未提供实时行情时,可基于你的知识回答,但要注明\"基于公开信息,非实时行情\"。"

    /**
     * 通用多轮对话（整体返回，非流式）。
     * 回调：(内容, 错误信息)；成功时 error 为 null。
     */
    fun chat(
        network: NetworkModule,
        config: AiConfig,
        history: List<ChatMessage>,
        callback: (String, String?) -> Unit
    ) {
        val messages = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", CHAT_SYSTEM_PROMPT)
                }
            )
            // 携带最近 30 条上下文（含当前提问）。
            // role 映射：context(数据上下文，K线追问注入) -> system，不占用 user/assistant 槽位；
            // user 内容若为旧版本内嵌的"（数据上下文：…）"尾巴则剥离后发送（新版本已改独立 context 消息）。
            history.takeLast(30).forEach { m ->
                if (m.content.isNotBlank()) {
                    val role = when (m.role) {
                        "assistant" -> "assistant"
                        "user" -> "user"
                        "context" -> "system"
                        else -> null
                    }
                    if (role != null) {
                        val content = if (role == "user") stripContextSuffix(m.content) else m.content
                        if (content.isNotBlank()) {
                            put(
                                JSONObject().apply {
                                    put("role", role)
                                    put("content", content)
                                }
                            )
                        }
                    }
                }
            }
        }
        val body = JSONObject().apply {
            put("model", config.model)
            put("messages", messages)
            put("temperature", 0.7)
        }
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            put("Authorization", "Bearer ${config.apiKey}")
        }
        network.httpRequest(chatUrl(config.baseUrl), true, body, headers, null, 60) { data, success, _, _ ->
            if (!success) {
                callback("", "请求失败，请检查网络或 API 配置")
                return@httpRequest
            }
            val content = data.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (content.isBlank()) {
                callback("", "服务返回内容为空")
            } else {
                callback(content, null)
            }
        }
    }

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
            val parsed = parseResult(content, quote, kline)
            if (parsed != null) {
                callback(parsed)
            } else {
                // 解析失败：保留 AI 的 Markdown 正文展示，结构化字段用本地规则兜底（联动不丢）
                callback(localFallback(quote, kline).copy(markdown = content, source = AiAnalysisResult.SOURCE_AI))
            }
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
                        "你是一名专业的A股技术分析助手。请基于用户提供的行情数据，用中文输出一份结构化JSON分析，" +
                                "并将整份JSON放在一个 ```json 代码围栏中，围栏之外不要输出任何内容。" +
                                "JSON格式（字段务必齐全）：" +
                                "{\"trend\":\"看多/看空/震荡等标签\",\"trendDesc\":\"趋势判断说明\",\"suggestion\":\"操作建议\"," +
                                "\"buyPoints\":[\"买入参考点位1\"],\"sellPoints\":[\"卖出参考点位1\"]," +
                                "\"risks\":[\"风险提醒1\"],\"riskLevel\":\"低/中/高（必须三选一）\",\"summary\":\"行情总结\"," +
                                "\"markdown\":\"一段完整的Markdown深度分析正文（含小标题、加粗、列表，供用户阅读）\"," +
                                "\"keyLevels\":[{\"price\":1520.0,\"type\":\"support\",\"label\":\"短线支撑\",\"desc\":\"近20日低点附近\"}]," +
                                "\"metrics\":[{\"key\":\"turnover\",\"name\":\"换手率\",\"comment\":\"交投活跃，注意高位放量\"}]}" +
                                "说明：keyLevels 为数值型关键价位，type 仅取 support(支撑) 或 resistance(压力)，" +
                                "price 用具体价格数字，label 简短、desc 一句话；" +
                                "metrics 是对具体指标的注解，key 仅限 pe/turnover/amplitude/volume/amount/avgPrice/high/low/open/prevClose，" +
                                "name 用中文名、comment 一句解读；markdown 字段必须包含完整可读的分析正文。"
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

    /** 解析模型返回的 JSON 分析内容（支持 ```json 围栏剥离；含 keyLevels/metrics/markdown） */
    private fun parseResult(content: String, quote: StockQuote, kline: List<KLineBar>): AiAnalysisResult? {
        if (content.isBlank()) return null
        var json: JSONObject? = try {
            JSONObject(content.trim())
        } catch (e: Throwable) {
            null
        }
        if (json == null) {
            val fenced = extractJsonContent(content)
            if (fenced != null) {
                json = try {
                    JSONObject(fenced)
                } catch (e: Throwable) {
                    null
                }
            }
        }
        if (json == null) return null

        val keyLevels = json.optJSONArray("keyLevels")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val price = o.optDouble("price") ?: 0.0
                if (price <= 0) return@mapNotNull null
                KeyLevel(
                    price = price,
                    type = o.optString("type", "support"),
                    label = o.optString("label", ""),
                    desc = o.optString("desc", "")
                )
            }
        } ?: emptyList()

        val metrics = json.optJSONArray("metrics")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val key = o.optString("key", "")
                if (key.isBlank()) return@mapNotNull null
                MetricInsight(
                    key = key,
                    name = o.optString("name", key),
                    comment = o.optString("comment", "")
                )
            }
        } ?: emptyList()

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
            source = AiAnalysisResult.SOURCE_AI,
            markdown = json.optString("markdown", ""),
            keyLevels = keyLevels,
            metrics = metrics
        )
    }

    /** 从模型返回中剥离 ```json / ``` 围栏，返回内部 JSON 文本；无围栏返回 null */
    private fun extractJsonContent(content: String): String? {
        val t = content.trim()
        val fence = "```"
        val start = t.indexOf(fence)
        if (start < 0) return null
        var bodyStart = start + fence.length
        // 首围栏后可能紧跟 "json" 语言标识，跳到其后换行
        val nl = t.indexOf('\n', bodyStart)
        if (nl >= 0 && nl < bodyStart + 12) {
            bodyStart = nl + 1
        }
        val end = t.indexOf(fence, bodyStart)
        if (end < 0) return null
        return t.substring(bodyStart, end).trim()
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

        // 结构化关键价位：均价支撑 + 近20日高低点
        val keyLevels = mutableListOf<KeyLevel>()
        if (avg > 0) {
            keyLevels.add(KeyLevel(avg, KeyLevel.TYPE_SUPPORT, "均价支撑", "今日均价附近"))
        }
        if (kline.isNotEmpty()) {
            val low20 = kline.takeLast(20).minOfOrNull { it.low } ?: quote.low
            val high20 = kline.takeLast(20).maxOfOrNull { it.high } ?: quote.high
            keyLevels.add(KeyLevel(low20, KeyLevel.TYPE_SUPPORT, "近20日支撑", "近20日低点附近"))
            keyLevels.add(KeyLevel(high20, KeyLevel.TYPE_RESISTANCE, "近20日压力", "近20日高点附近"))
        }

        // 结构化指标注解：换手率/振幅/涨跌
        val metrics = mutableListOf<MetricInsight>()
        if (quote.turnover >= 10.0) {
            metrics.add(MetricInsight("turnover", "换手率", "交投过热，谨防冲高回落"))
        } else if (quote.turnover >= 5.0) {
            metrics.add(MetricInsight("turnover", "换手率", "交投活跃，资金关注度上升"))
        }
        if (quote.amplitude >= 5.0) {
            metrics.add(MetricInsight("amplitude", "振幅", "波动明显加剧，注意仓位控制"))
        } else if (quote.amplitude >= 3.0) {
            metrics.add(MetricInsight("amplitude", "振幅", "波动中等，短线可波段操作"))
        }
        if (pct < 0) {
            metrics.add(MetricInsight("changePercent", "涨跌幅", "股价跌破昨收，短线情绪偏弱"))
        }

        // Markdown 正文（降级链路的可读展示）
        val markdown = buildString {
            append("## ${quote.name} 行情解读\n\n")
            append("**趋势**：$trend。$trendDesc\n\n")
            append("**操作建议**：$suggestion\n\n")
            append("**行情总结**：$summary\n\n")
            if (buyPoints.isNotEmpty()) {
                append("**买入参考**：\n")
                buyPoints.forEach { append("- $it\n") }
                append("\n")
            }
            if (sellPoints.isNotEmpty()) {
                append("**卖出参考**：\n")
                sellPoints.forEach { append("- $it\n") }
                append("\n")
            }
            if (risks.isNotEmpty()) {
                append("**风险提示**：\n")
                risks.forEach { append("- $it\n") }
            }
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
            source = AiAnalysisResult.SOURCE_RULE,
            markdown = markdown,
            keyLevels = keyLevels,
            metrics = metrics
        )
    }
}
