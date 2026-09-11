package com.example.kuiklychart.chart.candle

import com.example.kuiklychart.chart.base.BaseChartAttr
import com.example.kuiklychart.chart.base.CandleData
import com.example.kuiklychart.chart.base.PriceLevel
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * K线（蜡烛图）组件属性。
 *
 * 使用方式（DSL）：
 * ```
 * CandleStickChart {
 *     attr {
 *         bars = listOf(
 *             CandleData("2026-08-27", 1300f, 1302.8f, 1314.45f, 1295f, 21731L),
 *             ...
 *         )
 *         xAxis { labels = listOf("08-20", "08-21", "08-24", ...) }
 *         yAxis { min = ...; max = ... }
 *         upColor = Color(0xFFE0322E)   // 涨 - 红
 *         downColor = Color(0xFF0A9C5C) // 跌 - 绿
 *         showVolume = true             // 显示成交量副图
 *     }
 * }
 * ```
 */
class CandleStickChartAttr : BaseChartAttr() {

    /** K线数据列表 */
    var bars: List<CandleData> by observable(emptyList())

    /** 阳线颜色（涨 - 红） */
    var upColor: Color by observable(Color(0xFFE0322E))

    /** 阴线颜色（跌 - 绿） */
    var downColor: Color by observable(Color(0xFF0A9C5C))

    /** 蜡烛实体宽度占每根槽位的比例 */
    var candleWidthRatio: Float by observable(0.6f)

    /** 是否显示成交量副图 */
    var showVolume: Boolean by observable(true)

    /** 价格参考线（支撑/压力位） */
    var priceLevels: List<PriceLevel> by observable(emptyList())

    // ==================== 交互（缩放 / 平移 / 长按读数） ====================

    /**
     * 是否启用缩放、平移与长按读数交互。
     * 关闭后组件行为与 v1.9.27 之前一致（仅点击选中）。
     */
    var interactive: Boolean by observable(true)

    /** 缩放时允许的最少可见 K 线根数（防止无限放大） */
    var minVisibleCount: Int by observable(10)

    /**
     * 双击缩放档位（可见根数，从大到小）。
     * 双击时在当前档位与「全部」之间循环：全部 → 档位1 → 档位2 → … → 全部。
     * 档位值 >= 总根数时自动跳过。
     */
    var zoomSteps: List<Int> by observable(listOf(60, 30, 15))

    /**
     * 按【全量数据索引】生成 X 轴刻度文案。
     * 启用缩放后必须提供——否则缩放时 X 轴标签仍是全量生成的，会与实际可见区间错位。
     * 未提供时回退到 [com.example.kuiklychart.chart.base.XAxisConfig.labels]。
     */
    var xLabelAt: ((Int) -> String)? = null

    /** 十字准线颜色 */
    var crosshairColor: Color by observable(Color(0xFF8A8F99))

    /** 数值浮标面板背景色（半透明） */
    var crosshairPanelBg: Color by observable(Color(0xD91F2937))

    /** 数值浮标面板文字色 */
    var crosshairPanelText: Color by observable(Color(0xFFFFFFFF))

    /** 提示文字（可见区间、重置按钮）颜色 */
    var hintTextColor: Color by observable(Color(0xFF98A2B3))

    /** 提示文字描边底色（避免压在网格线上看不清） */
    var hintChipBg: Color by observable(Color(0x00000000))

    /** 缩放后是否显示右上角「重置」按钮 */
    var showResetButton: Boolean by observable(true)
}
