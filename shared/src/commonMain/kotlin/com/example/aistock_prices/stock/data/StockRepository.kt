package com.example.aistock_prices.stock.data

import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 行情数据仓储：基于腾讯免费行情接口（HTTPS）。
 *
 * - 实时行情：https://qt.gtimg.cn/utf8/q=sh600519,sz000001 （非 JSON 文本，~ 分隔，字段见 parseQuote；utf8 路径返回 UTF-8，中文名称不乱码）
 * - 分时数据：https://web.ifzq.gtimg.cn/appstock/app/minute/query?code=sh600519 （JSON）
 * - 日K线：  https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=sh600519,day,,,60,qfq （JSON）
 *           param 第二位换 week / month 即为周K / 月K，回包字段名相应为 qfqweek / qfqmonth
 *
 * 所有回包均走 Kuikly NetworkModule；非 JSON 回包会被框架包装为 {data:"原始文本"}。
 */
object StockRepository {

    private const val QUOTE_URL = "https://qt.gtimg.cn/utf8/q="
    private const val MINUTE_URL = "https://web.ifzq.gtimg.cn/appstock/app/minute/query"
    private const val KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"
    private const val SEARCH_URL = "https://proxy.finance.qq.com/ifzqgtimg/appstock/smartbox/search/get"

    /** 本站只做 A 股，搜索结果里港股 / 美股 / 权证等一律过滤掉 */
    private val A_SHARE_MARKETS = setOf("sh", "sz", "bj")

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

    /** 拉取 K 线（count 根，前复权）。[period] 支持日/周/月 */
    fun fetchKLine(
        network: NetworkModule,
        code: String,
        count: Int = 60,
        period: KLinePeriod = KLinePeriod.DAY,
        callback: (List<KLineBar>) -> Unit
    ) {
        val param = JSONObject().apply { put("param", "$code,${period.param},,,$count,qfq") }
        network.requestGet(KLINE_URL, param) { data, success, _, _ ->
            if (!success) {
                callback(emptyList())
                return@requestGet
            }
            val stock = data.optJSONObject("data")?.optJSONObject(code)
            // 实测字段名带 qfq 前缀（qfqday / qfqweek / qfqmonth）；
            // 兜底不加前缀的 day/week/month，接口若调整命名也不至于整块空白
            val arr = stock?.optJSONArray(period.responseKey) ?: stock?.optJSONArray(period.param)
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

    /**
     * 按关键词搜索股票（中文名 / 拼音 / 代码均可）。
     *
     * 接口是腾讯行情页的搜索联想（smartbox），回包 `data.stock` 为二维数组：
     * `[market, code, name, pinyin, type]`，如 `["sh","600519","贵州茅台","","GP-A"]`。
     * 实测 `pinyin` 恒为空串故不使用；`type` 形如 `GP-A`（主板）/ `GP-A-CYB`（创业板）/
     * `GP-A-KCB`（科创板），`GP` 则是港美股 —— 只保留 A 股可交易标的。
     *
     * ⚠️ 关键词必须 URL 编码：接口靠 query 传参，中文直接拼进 URL 会失败。见 [percentEncode]。
     */
    fun searchStocks(
        network: NetworkModule,
        keyword: String,
        callback: (List<StockSearchItem>) -> Unit
    ) {
        val kw = keyword.trim()
        if (kw.isEmpty()) {
            callback(emptyList())
            return
        }
        network.requestGet(SEARCH_URL + "?q=" + percentEncode(kw), JSONObject()) { data, success, _, _ ->
            if (!success) {
                callback(emptyList())
                return@requestGet
            }
            val arr = data.optJSONObject("data")?.optJSONArray("stock")
            val result = mutableListOf<StockSearchItem>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val row = arr.optJSONArray(i) ?: continue
                    val market = row.optString(0) ?: continue
                    val symbol = row.optString(1) ?: continue
                    val name = row.optString(2) ?: continue
                    val type = row.optString(4) ?: ""
                    if (market !in A_SHARE_MARKETS) continue
                    // A 股标的 type 以 GP 开头；权证（QZ）、基金（JJ）等一并排除
                    if (!type.startsWith("GP")) continue
                    if (symbol.isBlank() || name.isBlank()) continue
                    result.add(StockSearchItem(market + symbol, name, marketLabel(market, type)))
                }
            }
            callback(result)
        }
    }

    /** 由 market + type 推导用户可读的市场标注 */
    private fun marketLabel(market: String, type: String): String = when {
        type.contains("CYB") -> "创业板"
        type.contains("KCB") -> "科创板"
        market == "bj" -> "北交所"
        market == "sh" -> "沪A"
        market == "sz" -> "深A"
        else -> market
    }

    /**
     * UTF-8 百分号编码。
     *
     * commonMain 里拿不到 `java.net.URLEncoder`，而 Kuikly 自带的 `BridgeModule.urlEncode`
     * 依赖宿主实现（本仓库 iOS `HRBridgeModule.m` 是空壳，三端未必都有）——所以自己按字节编码。
     * 只放行 RFC 3986 的 unreserved 字符，其余（含中文的多字节 UTF-8）逐字节转 `%XX`。
     */
    private fun percentEncode(raw: String): String {
        val hex = "0123456789ABCDEF"
        val sb = StringBuilder()
        for (b in raw.encodeToByteArray()) {
            val v = b.toInt() and 0xFF
            val ch = v.toChar()
            // ⚠️ 必须先判 v < 128：高位字节 toChar() 后是 Latin-1 字符，isLetterOrDigit() 会误判为 true
            if (v < 128 && (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' || ch == '~')) {
                sb.append(ch)
            } else {
                sb.append('%')
                sb.append(hex[v shr 4])
                sb.append(hex[v and 0x0F])
            }
        }
        return sb.toString()
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
        // 字段数阈值：个股回包约 60+ 字段；指数回包（如 sh000001）字段略少，
        // 只需保证核心字段（名称/价格/昨收/涨跌/高低价）存在即可，其余越界字段由 d()/l() 兜底为 0
        if (f.size < 35) return null

        fun d(i: Int): Double = f.getOrNull(i)?.trim()?.toDoubleOrNull() ?: 0.0
        fun l(i: Int): Long = d(i).toLong()

        // utf8 接口下中文名可正常解析；异常时兜底为本地映射/代码
        val name = f.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() } ?: Watchlist.nameOf(code)
        return StockQuote(
            code = code,
            name = name,
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
        // ⚠️ 历史 K 线的成交量是浮点字符串（如 "622186.000"），只有当日那根是整数串。
        // 用 toLongOrNull() 会让除当日外的所有量都变成 0（K 线量能副图空白）。
        val volume = item.optString(5)?.toDoubleOrNull()?.toLong() ?: 0L
        return KLineBar(date, open, close, high, low, volume)
    }
}
