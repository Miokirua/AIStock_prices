package com.example.kuiklychart.chart.bar

import com.example.kuiklychart.chart.base.BaseChartEvent
import com.example.kuiklychart.chart.base.ChartSelection
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Canvas

/**
 * 柱状图组件。
 *
 * 使用方式（DSL）：
 * ```
 * BarChart {
 *     attr {
 *         dataSets = listOf(
 *             ChartDataSet(points = listOf(ChartDataPoint(30f, "A"), ...), label = "收入")
 *         )
 *         barColors = listOf(Color(0xFF4F8FF7), Color(0xFFF56C6C))
 *         showValueLabels = true
 *         cornerRadius = 4f
 *     }
 *     event {
 *         onDataPointClick = { seriesIndex, pointIndex, point ->
 *             // 处理柱状条点击
 *         }
 *     }
 * }
 * ```
 */
class BarChart : ComposeView<BarChartAttr, BaseChartEvent>() {

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

    override fun createAttr(): BarChartAttr {
        return BarChartAttr()
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
                        val renderer = BarChartRenderer(ctx.attr, ctx.canvasWidth, ctx.canvasHeight)
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
                            ctx.selection = ChartSelection()
                        }
                    }
                }
            }) { context, w, h ->
                ctx.canvasWidth = w
                ctx.canvasHeight = h
                val renderer = BarChartRenderer(ctx.attr, w, h)
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
 * 柱状图 DSL 扩展，将 [BarChart] 添加到视图树。
 *
 * 用法：在任意 `ViewContainer` 作用域内 `BarChart { attr { ... } }`
 */
fun ViewContainer<*, *>.BarChart(init: BarChart.() -> Unit) {
    addChild(BarChart(), init)
}
