package com.example.aistock_prices.stock.data

import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 行情本地缓存：将最近一次成功获取的数据快照写入 SharedPreferences，
 * 页面进入时先展示缓存（防止行情接口临时失效导致空白），再后台拉取新数据覆盖。
 */
object StockCache {

    private const val KEY_QUOTES = "cache_quotes"
    private const val KEY_MINUTE_PREFIX = "cache_minute_"
    private const val KEY_KLINE_PREFIX = "cache_kline_"

    // ==================== 报价列表 ====================

    fun saveQuotes(sp: SharedPreferencesModule, list: List<StockQuote>) {
        val arr = JSONArray()
        list.forEach { q ->
            arr.put(
                JSONObject().apply {
                    put("code", q.code)
                    put("name", q.name)
                    put("price", q.price)
                    put("prevClose", q.prevClose)
                    put("open", q.open)
                    put("high", q.high)
                    put("low", q.low)
                    put("change", q.change)
                    put("changePercent", q.changePercent)
                    put("volume", q.volume)
                    put("amount", q.amount)
                    put("turnover", q.turnover)
                    put("amplitude", q.amplitude)
                    put("avgPrice", q.avgPrice)
                    put("time", q.time)
                }
            )
        }
        sp.setItem(KEY_QUOTES, JSONObject().apply { put("list", arr) }.toString())
    }

    fun loadQuotes(sp: SharedPreferencesModule): List<StockQuote> {
        val raw = sp.getItem(KEY_QUOTES)
        if (raw.isBlank()) return emptyList()
        return try {
            val obj = JSONObject(raw)
            val arr = obj.optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                StockQuote(
                    code = o.optString("code") ?: return@mapNotNull null,
                    name = o.optString("name") ?: "",
                    price = o.optDouble("price") ?: 0.0,
                    prevClose = o.optDouble("prevClose") ?: 0.0,
                    open = o.optDouble("open") ?: 0.0,
                    high = o.optDouble("high") ?: 0.0,
                    low = o.optDouble("low") ?: 0.0,
                    change = o.optDouble("change") ?: 0.0,
                    changePercent = o.optDouble("changePercent") ?: 0.0,
                    volume = o.optLong("volume") ?: 0L,
                    amount = o.optDouble("amount") ?: 0.0,
                    turnover = o.optDouble("turnover") ?: 0.0,
                    amplitude = o.optDouble("amplitude") ?: 0.0,
                    avgPrice = o.optDouble("avgPrice") ?: 0.0,
                    time = o.optString("time") ?: ""
                )
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    // ==================== 分时 ====================

    fun saveMinute(sp: SharedPreferencesModule, code: String, points: List<MinutePoint>) {
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("time", p.time)
                    put("price", p.price)
                    put("volume", p.volume)
                    put("amount", p.amount)
                }
            )
        }
        sp.setItem(KEY_MINUTE_PREFIX + code, JSONObject().apply { put("list", arr) }.toString())
    }

    fun loadMinute(sp: SharedPreferencesModule, code: String): List<MinutePoint> {
        val raw = sp.getItem(KEY_MINUTE_PREFIX + code)
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                MinutePoint(
                    time = o.optString("time") ?: "",
                    price = o.optDouble("price") ?: 0.0,
                    volume = o.optLong("volume") ?: 0L,
                    amount = o.optDouble("amount") ?: 0.0
                )
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    // ==================== K线（按周期分别缓存） ====================

    /**
     * K 线缓存键。日K沿用旧键（`cache_kline_<code>`）以兼容已落盘的缓存，
     * 周/月K追加周期后缀，避免切周期时读到另一个周期的数据。
     */
    private fun klineKey(code: String, period: KLinePeriod): String =
        if (period == KLinePeriod.DAY) KEY_KLINE_PREFIX + code
        else KEY_KLINE_PREFIX + code + "_" + period.param

    fun saveKLine(
        sp: SharedPreferencesModule,
        code: String,
        bars: List<KLineBar>,
        period: KLinePeriod = KLinePeriod.DAY
    ) {
        val arr = JSONArray()
        bars.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("date", b.date)
                    put("open", b.open)
                    put("close", b.close)
                    put("high", b.high)
                    put("low", b.low)
                    put("volume", b.volume)
                }
            )
        }
        sp.setItem(klineKey(code, period), JSONObject().apply { put("list", arr) }.toString())
    }

    fun loadKLine(
        sp: SharedPreferencesModule,
        code: String,
        period: KLinePeriod = KLinePeriod.DAY
    ): List<KLineBar> {
        val raw = sp.getItem(klineKey(code, period))
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                KLineBar(
                    date = o.optString("date") ?: "",
                    open = o.optDouble("open") ?: 0.0,
                    close = o.optDouble("close") ?: 0.0,
                    high = o.optDouble("high") ?: 0.0,
                    low = o.optDouble("low") ?: 0.0,
                    volume = o.optLong("volume") ?: 0L
                )
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }
}
