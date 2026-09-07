package com.example.kuiklychart.chart.candle

import com.example.kuiklychart.chart.base.BaseChartEvent
import com.example.kuiklychart.chart.base.ChartDataPoint
import com.example.kuiklychart.chart.base.ChartSelection
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.handler.observable
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
 *         priceLevels = listOf(PriceLevel(1300f, "支撑", Color(...)))
 *     }
 *     event {
 *         onDataPointClick = { seriesIndex, pointIndex, point -> ... }
 *     }
 * }
 * ```
 */
class CandleStickChart : ComposeView<CandleStickChartAttr, BaseChartEvent>() {

    /** 最近一次绘制的画布尺寸，供点击命中检测使用 */
    private var canvasWidth = 0f
    private var canvasHeight = 0f

    /** 当前选中状态（observable，变更后自动重绘） */
    private var selection by observable(ChartSelection())

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
                event {
                    click {
                        val renderer = CandleStickChartRenderer(ctx.attr, ctx.canvasWidth, ctx.canvasHeight)
                        val hit = renderer.hitTest(it.x, it.y)
                        if (hit != null) {
                            ctx.selection = hit
                            val bar = ctx.attr.bars.getOrNull(hit.pointIndex)
                            if (bar != null) {
                                ctx.event.onDataPointClick?.invoke(0, hit.pointIndex, ChartDataPoint(bar.close, bar.date))
                            }
                        } else {
                            // 未命中时清除选中
                            ctx.selection = ChartSelection()
                        }
                    }
                }
            }) { context, w, h ->
                ctx.canvasWidth = w
                ctx.canvasHeight = h
                val renderer = CandleStickChartRenderer(ctx.attr, w, h)
                renderer.render(context, ctx.selection)
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
