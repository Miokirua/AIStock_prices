package com.example.aistock_prices.stock.ui

import com.tencent.kuikly.core.base.Color
import kotlin.math.abs
import kotlin.math.round

/** A股配色：涨红跌绿 */
object StockColors {
    val UP = Color(0xFFE0322E)      // 涨 - 红
    val DOWN = Color(0xFF0A9C5C)    // 跌 - 绿
    val FLAT = Color(0xFF9E9E9E)    // 平 - 灰
    val TEXT_MAIN = Color(0xFF1A1A1A)
    val TEXT_SUB = Color(0xFF8A8A8A)
    val BG_PAGE = Color(0xFFF5F6F8)
    val CARD = Color(0xFFFFFFFF)
    val ACCENT = Color(0xFF3370FF)

    /** 根据涨跌返回颜色 */
    fun ofChange(v: Double): Color {
        return when {
            v > 0.0 -> UP
            v < 0.0 -> DOWN
            else -> FLAT
        }
    }
}

/** 纯 Kotlin 数值格式化（跨端安全，避免 java.util.Locale） */
object StockFormat {

    /** 保留2位小数，如 1293.79 */
    fun price(v: Double): String = format2(v)

    /** 带符号保留2位小数，如 +1.23% */
    fun percent(v: Double): String {
        val sign = if (v > 0) "+" else if (v < 0) "-" else ""
        return "$sign${format2(abs(v))}%"
    }

    /** 带符号，如 +1.49 */
    fun change(v: Double): String {
        val sign = if (v > 0) "+" else if (v < 0) "-" else ""
        return "$sign${format2(abs(v))}"
    }

    /** 成交量格式化：手 -> 万手/亿手 */
    fun volume(hands: Long): String {
        return when {
            hands >= 100_000_000 -> "${format2(hands / 100_000_000.0)}亿手"
            hands >= 10_000 -> "${format2(hands / 10_000.0)}万手"
            else -> "${hands}手"
        }
    }

    /** 成交额格式化：万元 -> 亿/万 */
    fun amount(wan: Double): String {
        return when {
            wan >= 10_000 -> "${format2(wan / 10_000.0)}亿"
            else -> "${format2(wan)}万"
        }
    }

    private fun format2(v: Double): String {
        val neg = v < 0
        val scaled = round(abs(v) * 100).toLong()
        val intPart = scaled / 100
        val decPart = scaled % 100
        val sign = if (neg) "-" else ""
        return "$sign$intPart.${decPart.toString().padStart(2, '0')}"
    }
}
