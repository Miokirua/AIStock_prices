package com.example.aistock_prices.stock.data

/**
 * 自选股元数据：腾讯行情代码（带市场前缀）+ 本地维护的中文名称。
 * 说明：qt.gtimg.cn 实时接口回包为 GBK 编码，而 Kuikly 网络层固定 UTF-8 解码，
 * 中文名称会乱码，因此名称一律以本地维护为准（仅解析 ASCII 数字字段）。
 */
data class StockMeta(
    val code: String,   // 腾讯代码，如 sh600519 / sz000001
    val name: String    // 本地维护的中文名称
) {
    val symbol: String get() = code.removePrefix("sh").removePrefix("sz").removePrefix("hk")
}

/** 实时行情快照 */
data class StockQuote(
    val code: String,
    val name: String,
    val price: Double,        // 最新价
    val prevClose: Double,    // 昨收
    val open: Double,         // 今开
    val high: Double,         // 最高
    val low: Double,          // 最低
    val change: Double,       // 涨跌额
    val changePercent: Double, // 涨跌幅 %
    val volume: Long,         // 成交量（手）
    val amount: Double,       // 成交额（万元）
    val turnover: Double,     // 换手率 %
    val amplitude: Double,    // 振幅 %
    val avgPrice: Double,     // 均价
    val time: String          // 行情时间 yyyyMMddHHmmss
) {
    /** 纯数字代码，如 600519 */
    val symbol: String get() = code.removePrefix("sh").removePrefix("sz").removePrefix("hk")
}

/** 分时数据点 */
data class MinutePoint(
    val time: String,   // HHmm
    val price: Double,
    val volume: Long,   // 手
    val amount: Double  // 元
)

/** 日K线 */
data class KLineBar(
    val date: String,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Long // 手
)

/** 内置默认自选股（首次启动展示，用户可增删） */
object Watchlist {
    private const val KEY_STOCKS = "watchlist_stocks"

    private val defaults = listOf(
        StockMeta("sh600519", "贵州茅台"),
        StockMeta("sz300750", "宁德时代"),
        StockMeta("sz002594", "比亚迪"),
        StockMeta("sh600036", "招商银行"),
        StockMeta("sz000001", "平安银行"),
        StockMeta("sz000858", "五粮液"),
        StockMeta("sh601318", "中国平安"),
        StockMeta("sh600900", "长江电力"),
        StockMeta("sh601012", "隆基绿能"),
        StockMeta("sz300059", "东方财富")
    )

    /** 当前自选列表：未保存过时返回内置默认 */
    fun stocks(sp: com.tencent.kuikly.core.module.SharedPreferencesModule): List<StockMeta> {
        val raw = sp.getItem(KEY_STOCKS)
        if (raw.isBlank()) return defaults
        return parse(raw)
    }

    /** 添加（已存在返回 false） */
    fun add(sp: com.tencent.kuikly.core.module.SharedPreferencesModule, meta: StockMeta): Boolean {
        val list = stocks(sp)
        if (list.any { it.code == meta.code }) return false
        save(sp, list + meta)
        return true
    }

    /** 删除 */
    fun remove(sp: com.tencent.kuikly.core.module.SharedPreferencesModule, code: String): Boolean {
        val list = stocks(sp)
        val newList = list.filter { it.code != code }
        if (newList.size == list.size) return false
        save(sp, newList)
        return true
    }

    private fun save(sp: com.tencent.kuikly.core.module.SharedPreferencesModule, list: List<StockMeta>) {
        val arr = com.tencent.kuikly.core.nvi.serialization.json.JSONArray()
        list.forEach { meta ->
            arr.put(
                com.tencent.kuikly.core.nvi.serialization.json.JSONObject().apply {
                    put("code", meta.code)
                    put("name", meta.name)
                }
            )
        }
        sp.setItem(KEY_STOCKS, com.tencent.kuikly.core.nvi.serialization.json.JSONObject().apply { put("list", arr) }.toString())
    }

    private fun parse(raw: String): List<StockMeta> {
        return try {
            val obj = com.tencent.kuikly.core.nvi.serialization.json.JSONObject(raw)
            val arr = obj.optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val code = o.optString("code") ?: return@mapNotNull null
                if (code.isBlank()) return@mapNotNull null
                StockMeta(code, o.optString("name").ifBlank { code })
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /** 归一化用户输入：600519 -> sh600519；带前缀原样返回 */
    fun normalizeCode(input: String): String {
        val code = input.trim().lowercase()
        if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("bj")) {
            return code
        }
        if (code.length != 6 || code.any { !it.isDigit() }) return code
        return when (code[0]) {
            '6' -> "sh$code"
            '0', '3' -> "sz$code"
            '4', '8' -> "bj$code"
            else -> "sh$code"
        }
    }

    fun nameOf(code: String): String {
        return defaults.firstOrNull { it.code == code }?.name ?: code
    }
}
