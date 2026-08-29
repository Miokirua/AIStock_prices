package com.example.kuiklychart.chart.base

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * 图表组件通用属性，线性图/柱状图等图表共享的基础配置。
 *
 * 使用方式（声明式 DSL）：
 * ```
 * LineChart {
 *     attr {
 *         dataSets = listOf(...)
 *         xAxis { labels = listOf("1月", "2月") }
 *         yAxis { min = 0f; max = 100f }
 *     }
 * }
 * ```
 */
open class BaseChartAttr : ComposeAttr() {

    /** 数据集列表，支持多系列 */
    var dataSets: List<ChartDataSet> by observable(emptyList())

    /** X 轴配置 */
    var xAxis: XAxisConfig by observable(XAxisConfig())

    /** Y 轴配置 */
    var yAxis: YAxisConfig by observable(YAxisConfig())

    /** 绘图区内边距（默认已为坐标轴刻度预留空间） */
    var padding: ChartPadding by observable(
        ChartPadding(
            left = ChartTheme.DefaultPaddingLeft,
            top = ChartTheme.DefaultPaddingTop,
            right = ChartTheme.DefaultPaddingRight,
            bottom = ChartTheme.DefaultPaddingBottom
        )
    )

    /** 是否显示图例 */
    var showLegend: Boolean by observable(false)

    /** 图表背景色 */
    var backgroundColor: Color by observable(Color.TRANSPARENT)

    /** 是否启用入场动画（阶段二扩展，当前占位） */
    var isAnimationEnabled: Boolean by observable(false)
}
