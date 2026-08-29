package com.example.kuiklychart.chart.bar

import com.example.kuiklychart.chart.base.BaseChartAttr
import com.example.kuiklychart.chart.base.ChartTheme
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * 柱状图组件属性。
 *
 * 用法：
 * ```
 * BarChart {
 *     attr {
 *         dataSets = listOf(ChartDataSet(points = ...))
 *         barColors = listOf(Color(0xFFF56C6C), Color(0xFF4F8FF7))
 *         showValueLabels = true
 *         cornerRadius = 4f
 *     }
 * }
 * ```
 */
class BarChartAttr : BaseChartAttr() {

    /** 柱宽（像素）；为 null 时根据绘图区宽度自动计算 */
    var barWidth: Float? by observable(null)

    /** 柱间距（像素） */
    var barSpacing: Float by observable(4f)

    /** 柱状条颜色列表，按数据点索引取色（支持每根柱子单独自定义颜色） */
    var barColors: List<Color> by observable(emptyList())

    /** 是否在柱顶显示数值标签 */
    var showValueLabels: Boolean by observable(false)

    /** 数值标签颜色 */
    var valueLabelColor: Color by observable(ChartTheme.TextColor)

    /** 数值标签字号 */
    var valueLabelSize: Float by observable(10f)

    /** 柱子圆角半径（像素） */
    var cornerRadius: Float by observable(0f)

    /** 选中高亮颜色 */
    var highlightColor: Color by observable(ChartTheme.HighlightColor)

    /** 点击命中容差（像素） */
    var hitTolerance: Float by observable(16f)
}
