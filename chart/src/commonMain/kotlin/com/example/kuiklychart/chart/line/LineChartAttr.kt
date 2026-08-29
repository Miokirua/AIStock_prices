package com.example.kuiklychart.chart.line

import com.example.kuiklychart.chart.base.BaseChartAttr
import com.example.kuiklychart.chart.base.ChartTheme
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * 线性图组件属性。
 *
 * 用法：
 * ```
 * LineChart {
 *     attr {
 *         dataSets = listOf(ChartDataSet(points = ...))
 *         xAxis { labels = listOf("1月", "2月") }
 *         yAxis { min = 0f; max = 100f }
 *         smooth = true
 *         showDots = true
 *         lineColor = Color(0xFF4F8FF7)
 *         lineWidth = 3f
 *     }
 * }
 * ```
 */
class LineChartAttr : BaseChartAttr() {

    /** 是否使用平滑曲线（贝塞尔插值） */
    var smooth: Boolean by observable(true)

    /** 线条宽度（像素） */
    var lineWidth: Float by observable(2f)

    /** 线条颜色；为 null 时使用系列颜色/主题默认色 */
    var lineColor: Color? by observable(null)

    /** 是否显示数据点 */
    var showDots: Boolean by observable(true)

    /** 数据点半径（像素） */
    var dotRadius: Float by observable(3f)

    /** 数据点填充色；为 null 时使用白色底 + 系列色描边 */
    var dotColor: Color? by observable(null)

    /** 是否填充折线下方区域（面积效果） */
    var fillArea: Boolean by observable(false)

    /** 填充颜色；为 null 时使用系列色的 20% 透明度 */
    var fillColor: Color? by observable(null)

    /** 选中高亮颜色 */
    var highlightColor: Color by observable(ChartTheme.HighlightColor)

    /** 选中高亮圆半径（像素） */
    var highlightRadius: Float by observable(6f)

    /** 点击命中容差（像素），即离数据点多近算命中 */
    var hitTolerance: Float by observable(20f)
}
