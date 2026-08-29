package com.example.kuiklychart.chart.base

import com.tencent.kuikly.core.base.Color

/**
 * 坐标轴通用配置。
 *
 * 所有字段为 null 时由图表自动计算，可通过 DSL 覆盖：
 * ```
 * yAxis {
 *     min = 0f
 *     max = 100f
 *     step = 20f
 *     showGridLines = true
 * }
 * ```
 */
open class AxisConfig {
    /** Y 轴最小值，null 表示根据数据自动计算（X 轴忽略） */
    var min: Float? = null
    /** Y 轴最大值，null 表示根据数据自动计算（X 轴忽略） */
    var max: Float? = null
    /** 坐标轴步长，null 表示自动计算 */
    var step: Float? = null
    /** 是否显示网格线 */
    var showGridLines: Boolean = true
    /** 网格线颜色 */
    var gridColor: Color = ChartTheme.GridColor
    /** 网格线宽度 */
    var gridWidth: Float = 1f
    /** 是否显示坐标轴刻度文字 */
    var showLabels: Boolean = true
    /** 刻度文字颜色 */
    var labelColor: Color = ChartTheme.LabelColor
    /** 刻度文字字号 */
    var labelSize: Float = ChartTheme.DefaultLabelSize
    /** 是否显示坐标轴线 */
    var showAxisLine: Boolean = true
    /** 坐标轴线颜色 */
    var axisColor: Color = ChartTheme.AxisColor
    /** 坐标轴线宽度 */
    var axisWidth: Float = 1f
}

/**
 * X 轴配置。
 *
 * @property labels 自定义 X 轴刻度文案；为空时优先使用数据点自带 label，再使用索引
 */
class XAxisConfig : AxisConfig() {
    var labels: List<String> = emptyList()

    /** DSL 配置入口：`xAxis { labels = listOf(...) }` */
    operator fun invoke(builder: XAxisConfig.() -> Unit): XAxisConfig {
        builder()
        return this
    }
}

/**
 * Y 轴配置。
 *
 * @property formatter 数值格式化回调，例如 `{ v -> "${v.toInt()}%" }`
 */
class YAxisConfig : AxisConfig() {
    var formatter: ((Float) -> String)? = null

    /** DSL 配置入口：`yAxis { min = 0f; max = 100f }` */
    operator fun invoke(builder: YAxisConfig.() -> Unit): YAxisConfig {
        builder()
        return this
    }
}
