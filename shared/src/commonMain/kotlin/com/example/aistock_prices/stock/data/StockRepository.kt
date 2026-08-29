package com.example.aistock_prices.stock.data

import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 行情数据仓储：基于腾讯免费行情接口（HTTPS）。
 *
 * - 实时行情：https://qt.gtimg.cn/q=sh600519,sz000001 （非 JSON 文本，~ 分隔，字段见 parseQuote）
 * - 分时数据：https://web.ifzq.gtimg.cn/appstock/app/minute/query?code=sh600519 （JSON）
 * - 日K线：  https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=sh600519,day,,,60,qfq （JSON）
 *
 * 所有回包均走 Kuikly NetworkModule；非 JSON 回包会被框架包装为 {data:"原始文本"}。
 */
object StockRepository {

    private const val QUOTE_URL = "https://qt.gtimg.cn/q="
    private const val MINUTE_URL = "https://web.ifzq.gtimg.cn/appstock/app/minute/query"
    private const val KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"

    /** 批量拉取实时行情 */
    fun fetchQuotes(network: NetworkModule, codes: List<String>, callback: (List<StockQuote>) -> Unit) {
        if (codes.isEmpty()) {
            callback(emptyList())
            return
        }
        val url = QUOTE_URL + codes.joinToString(",")
        network.requestGet(url, JSONObject()) { data, success, _, _ ->
            if (!success) {
                callback(emptyList())
                return@requestGet
            }
            val raw = data.optString("data")
            val result = mutableListOf<StockQuote>()
            raw.split(";").forEach { seg ->
                parseQuote(seg)?.let { result.add(it) }
            }
            callback(result)
        }
    }

    /** 拉取当日分时数据 */
    fun fetchMinute(network: NetworkModule, code: String, callback: (List<MinutePoint>) -> Unit) {
        val param = JSONObject().apply { put("code", code) }
        network.requestGet(MINUTE_URL, param) { data, success, _, _ ->
            if (!success) {
                callback(emptyList())
                return@requestGet
            }
            val stock = data.optJSONObject("data")?.optJSONObject(code)
            val arr = stock?.optJSONObject("data")?.optJSONArray("data")
            val points = mutableListOf<MinutePoint>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.let { raw ->
                        parseMinutePoint(raw)?.let { points.add(it) }
                    }
                }
            }
            callback(points)
        }
    }

    /** 拉取日K线（count 根，前复权） */
    fun fetchKLine(network: NetworkModule, code: String, count: Int = 60, callback: (List<KLineBar>) -> Unit) {
        val param = JSONObject().apply { put("param", "$code,day,,,$count,qfq") }
        network.requestGet(KLINE_URL, param) { data, success, _, _ ->
            if (!success) {
                callback(emptyList())
                return@requestGet
            }
            val stock = data.optJSONObject("data")?.optJSONObject(code)
            val arr = stock?.optJSONArray("qfqday")
            val bars = mutableListOf<KLineBar>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optJSONArray(i)?.let { item ->
                        parseKLineBar(item)?.let { bars.add(it) }
                    }
                }
            }
            callback(bars)
        }
    }

    // ---------------- 解析 ----------------

    /**
     * qt.gtimg.cn 单条回包形如：
     * v_sh600519="1~贵州茅台~600519~1293.79~1292.30~1289.00~10064~...~0.12~...";
     * 字段以 ~ 分隔，关键下标（0 基）：
     * [1]名称 [2]代码 [3]最新价 [4]昨收 [5]今开 [6]成交量(手)
     * [30]时间 [31]涨跌额 [32]涨跌幅% [33]最高 [34]最低
     * [37]成交额(万) [38]换手率% [43]振幅% [51]均价
     */
    private fun parseQuote(seg: String): StockQuote? {
        val eq = seg.indexOf('=')
        if (eq <= 0) return null
        // 兼容前导不可见字符：用正则直接匹配 sh600519 / sz000001 等代码模式
        val code = Regex("""[a-z]{2}\d{6}""").find(seg.substringBefore('='))?.value ?: return null
        val content = seg.substring(eq + 1).trim().trim('"', ' ')
        val f = content.split("~")
        if (f.size < 52) return null

        fun d(i: Int): Double = f.getOrNull(i)?.trim()?.toDoubleOrNull() ?: 0.0
        fun l(i: Int): Long = d(i).toLong()

        return StockQuote(
            code = code,
            name = Watchlist.nameOf(code),
            price = d(3),
            prevClose = d(4),
            open = d(5),
            high = d(33),
            low = d(34),
            change = d(31),
            changePercent = d(32),
            volume = l(6),
            amount = d(37),
            turnover = d(38),
            amplitude = d(43),
            avgPrice = d(51),
            time = f.getOrNull(30)?.trim().orEmpty()
        )
    }

    /** 分时点："0930 1289.00 81 10440900.00" */
    private fun parseMinutePoint(raw: String): MinutePoint? {
        val parts = raw.trim().split(" ")
        if (parts.size < 4) return null
        val time = parts[0]
        val price = parts[1].toDoubleOrNull() ?: return null
        val volume = parts[2].toLongOrNull() ?: 0L
        val amount = parts[3].toDoubleOrNull() ?: 0.0
        return MinutePoint(time, price, volume, amount)
    }

    /** K线项：[date, open, close, high, low, volume] */
    private fun parseKLineBar(item: com.tencent.kuikly.core.nvi.serialization.json.JSONArray): KLineBar? {
        val date = item.optString(0) ?: return null
        val open = item.optString(1)?.toDoubleOrNull() ?: return null
        val close = item.optString(2)?.toDoubleOrNull() ?: return null
        val high = item.optString(3)?.toDoubleOrNull() ?: return null
        val low = item.optString(4)?.toDoubleOrNull() ?: return null
        val volume = item.optString(5)?.toLongOrNull() ?: 0L
        return KLineBar(date, open, close, high, low, volume)
    }
}
