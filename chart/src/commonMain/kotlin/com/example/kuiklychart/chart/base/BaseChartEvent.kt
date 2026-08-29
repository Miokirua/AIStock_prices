package com.example.kuiklychart.chart.base

import com.tencent.kuikly.core.base.ComposeEvent

/**
 * 图表组件通用事件。
 *
 * 使用方式：
 * ```
 * LineChart {
 *     event {
 *         onDataPointClick = { seriesIndex, pointIndex, point ->
 *             // 处理点击
 *         }
 *         onChartReady = {
 *             // 图表渲染完成
 *         }
 *     }
 * }
 * ```
 */
open class BaseChartEvent : ComposeEvent() {

    /**
     * 点击数据点回调。
     *
     * @param seriesIndex 系列索引
     * @param pointIndex 数据点索引
     * @param point 被点击的数据点
     */
    var onDataPointClick: ((seriesIndex: Int, pointIndex: Int, point: ChartDataPoint) -> Unit)? = null

    /** 图表首次渲染完成回调 */
    var onChartReady: (() -> Unit)? = null
}
