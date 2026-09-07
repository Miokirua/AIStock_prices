package com.example.kuiklychart.chart.candle

import com.example.kuiklychart.chart.base.BaseChartAttr
import com.example.kuiklychart.chart.base.CandleData
import com.example.kuiklychart.chart.base.PriceLevel
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * K线（蜡烛图）组件属性。
 *
 * 使用方式（DSL）：
 * ```
 * CandleStickChart {
 *     attr {
 *         bars = listOf(
 *             CandleData("2026-08-27", 1300f, 1302.8f, 1314.45f, 1295f, 21731L),
 *             ...
 *         )
 *         xAxis { labels = listOf("08-20", "08-21", "08-24", ...) }
 *         yAxis { min = ...; max = ... }
 *         upColor = Color(0xFFE0322E)   // 涨 - 红
 *         downColor = Color(0xFF0A9C5C) // 跌 - 绿
 *         showVolume = true             // 显示成交量副图
 *     }
 * }
 * ```
 */
class CandleStickChartAttr : BaseChartAttr() {

    /** K线数据列表 */
    var bars: List<CandleData> by observable(emptyList())

    /** 阳线颜色（涨 - 红） */
    var upColor: Color by observable(Color(0xFFE0322E))

    /** 阴线颜色（跌 - 绿） */
    var downColor: Color by observable(Color(0xFF0A9C5C))

    /** 蜡烛实体宽度占每根槽位的比例 */
    var candleWidthRatio: Float by observable(0.6f)

    /** 是否显示成交量副图 */
    var showVolume: Boolean by observable(true)

    /** 价格参考线（支撑/压力位） */
    var priceLevels: List<PriceLevel> by observable(emptyList())
}
