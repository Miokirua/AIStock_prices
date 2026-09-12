package com.example.kuiklychart.chart.base

import com.tencent.kuikly.core.base.Color

/**
 * 图表数据点。
 *
 * @param value 数据点的数值
 * @param label 数据点的标签（X 轴刻度文案），可留空使用 X 轴配置的 labels
 */
data class ChartDataPoint(
    val value: Float,
    val label: String = ""
)

/**
 * 图表数据集（一个系列）。
 *
 * @param points 数据点列表
 * @param label 系列名称（用于图例）
 * @param color 系列颜色，为空时使用主题默认色
 */
class ChartDataSet(
    val points: List<ChartDataPoint>,
    val label: String = "",
    val color: Color? = null
)

/**
 * 图表绘图区内边距，即数据区域与画布边缘之间预留的空间（用于放置坐标轴刻度）。
 */
class ChartPadding(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * 图表选中状态：记录被点击选中的系列索引与数据点索引。
 */
data class ChartSelection(
    val seriesIndex: Int = -1,
    val pointIndex: Int = -1
)

/**
 * K线（蜡烛图）数据。
 *
 * @param date 日期，如 2026-08-27
 * @param open 开盘价
 * @param close 收盘价
 * @param high 最高价
 * @param low 最低价
 * @param volume 成交量（手）
 */
data class CandleData(
    val date: String = "",
    val open: Float,
    val close: Float,
    val high: Float,
    val low: Float,
    val volume: Long = 0L
)

/**
 * 价格参考线（支撑/压力位），绘制在 K 线图上。
 *
 * @param price 价位数值
 * @param label 右侧标签，如「近20日支撑」
 * @param color 线条与标签颜色
 * @param dashed 是否虚线（预留字段，当前按实线绘制）
 */
data class PriceLevel(
    val price: Float,
    val label: String = "",
    val color: Color = Color(0xFFE6A23C),
    val dashed: Boolean = false,
    /** 线宽：用户手动标记的关键位用更粗的线，便于从 AI 给出的参考线里区分出来 */
    val width: Float = 1f
)
