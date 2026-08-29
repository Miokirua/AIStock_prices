package com.example.kuiklychart.chart.candle

import com.example.kuiklychart.chart.base.BaseChartEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas

/**
 * K线（蜡烛图）组件。
 *
 * 使用方式（DSL）：
 * ```
 * CandleStickChart {
 *     attr {
 *         bars = listOf(
 *             CandleData("2026-08-27", 1300f, 1302.8f, 1314.45f, 1295f, 21731L),
 *             ...
 *         )
 *         xAxis { labels = listOf("08-20", "08-24", "08-25") }
 *         upColor = Color(0xFFE0322E)   // 涨 - 红
 *         downColor = Color(0xFF0A9C5C) // 跌 - 绿
 *         showVolume = true
 *     }
 * }
 * ```
 */
class CandleStickChart : ComposeView<CandleStickChartAttr, BaseChartEvent>() {

    override fun createEvent(): BaseChartEvent {
        return BaseChartEvent()
    }

    override fun createAttr(): CandleStickChartAttr {
        return CandleStickChartAttr()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            Canvas({
                attr {
                    flex(1f)
                }
            }) { context, w, h ->
                CandleStickChartRenderer(ctx.attr, w, h).render(context)
            }
        }
    }
}

/**
 * K线图 DSL 扩展，将 [CandleStickChart] 添加到视图树。
 *
 * 用法：在任意 `ViewContainer` 作用域内 `CandleStickChart { attr { ... } }`
 */
fun ViewContainer<*, *>.CandleStickChart(init: CandleStickChart.() -> Unit) {
    addChild(CandleStickChart(), init)
}
