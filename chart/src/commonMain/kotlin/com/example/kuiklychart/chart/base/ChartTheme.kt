package com.example.kuiklychart.chart.base

import com.tencent.kuikly.core.base.Color

/**
 * 图表默认主题，集中管理默认颜色、字号、内边距等常量。
 */
object ChartTheme {

    /** 默认系列颜色（多系列时按序取用） */
    val DefaultSeriesColors: List<Color> = listOf(
        Color(0xFF4F8FF7), // 蓝
        Color(0xFF67C23A), // 绿
        Color(0xFFE6A23C), // 橙
        Color(0xFFF56C6C), // 红
        Color(0xFF909399), // 灰
        Color(0xFF6C5CE7)  // 紫
    )

    /** 主文本色 */
    val TextColor: Color = Color(0xFF333333)
    /** 辅助文本色（坐标轴刻度） */
    val LabelColor: Color = Color(0xFF999999)
    /** 网格线颜色 */
    val GridColor: Color = Color(0x1A000000)
    /** 坐标轴线颜色 */
    val AxisColor: Color = Color(0xFFE0E0E0)
    /** 高亮/选中颜色 */
    val HighlightColor: Color = Color(0xFFF56C6C)
    /** 数据点默认颜色 */
    val DefaultDotColor: Color = Color(0xFFFFFFFF)

    /** 默认刻度字号 */
    const val DefaultLabelSize = 10f

    /** 默认绘图区内边距 */
    const val DefaultPaddingLeft = 44f
    const val DefaultPaddingTop = 20f
    const val DefaultPaddingRight = 16f
    const val DefaultPaddingBottom = 24f
}
