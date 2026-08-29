package com.example.kuiklychart.chart.line

import com.example.kuiklychart.chart.base.BaseChartEvent
import com.example.kuiklychart.chart.base.ChartSelection
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Canvas

/**
 * 线性图组件。
 *
 * 使用方式（DSL）：
 * ```
 * LineChart {
 *     attr {
 *         dataSets = listOf(
 *             ChartDataSet(points = listOf(ChartDataPoint(10f, "1月"), ...), label = "销量")
 *         )
 *         xAxis { labels = listOf("1月", "2月", "3月") }
 *         yAxis { min = 0f; max = 30f; showGridLines = true }
 *         smooth = true
 *         showDots = true
 *     }
 *     event {
 *         onDataPointClick = { seriesIndex, pointIndex, point ->
 *             // 处理数据点点击
 *         }
 *     }
 * }
 * ```
 */
class LineChart : ComposeView<LineChartAttr, BaseChartEvent>() {

    /** 最近一次绘制的画布尺寸，供点击命中检测使用 */
    private var canvasWidth = 0f
    private var canvasHeight = 0f

    /** 当前选中状态（observable，变更后自动重绘） */
    private var selection by observable(ChartSelection())

    /** 是否已通知 onChartReady */
    private var readyNotified = false

    override fun createEvent(): BaseChartEvent {
        return BaseChartEvent()
    }

    override fun createAttr(): LineChartAttr {
        return LineChartAttr()
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
                        val renderer = LineChartRenderer(ctx.attr, ctx.canvasWidth, ctx.canvasHeight)
                        val hit = renderer.hitTest(it.x, it.y)
                        if (hit != null) {
                            ctx.selection = hit
                            val point = ctx.attr.dataSets
                                .getOrNull(hit.seriesIndex)
                                ?.points
                                ?.getOrNull(hit.pointIndex)
                            if (point != null) {
                                ctx.event.onDataPointClick?.invoke(hit.seriesIndex, hit.pointIndex, point)
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
                val renderer = LineChartRenderer(ctx.attr, w, h)
                renderer.render(context, ctx.selection)
                if (!ctx.readyNotified) {
                    ctx.readyNotified = true
                    ctx.event.onChartReady?.invoke()
                }
            }
        }
    }
}

/**
 * 线性图 DSL 扩展，将 [LineChart] 添加到视图树。
 *
 * 用法：在任意 `ViewContainer` 作用域内 `LineChart { attr { ... } }`
 */
fun ViewContainer<*, *>.LineChart(init: LineChart.() -> Unit) {
    addChild(LineChart(), init)
}
