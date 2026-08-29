package com.example.kuiklychart.chart.candle

import com.example.kuiklychart.chart.base.ChartArea
import com.example.kuiklychart.chart.base.ChartMath
import com.example.kuiklychart.chart.base.ValueRange
import com.example.kuiklychart.chart.base.drawBackground
import com.example.kuiklychart.chart.base.drawGridAndAxes
import com.tencent.kuikly.core.views.CanvasContext
import kotlin.math.abs
import kotlin.math.max

/**
 * K线（蜡烛图）渲染器。
 *
 * 布局：上方为价格区（蜡烛 + 网格坐标轴），下方为成交量副图。
 * 绘制顺序：背景 → 网格/坐标轴 → 蜡烛（影线 + 实体）→ 成交量柱。
 */
class CandleStickChartRenderer(
    private val attr: CandleStickChartAttr,
    private val width: Float,
    private val height: Float
) {

    /** 整块绘图区（扣除内边距） */
    private val area: ChartArea by lazy {
        val p = attr.padding
        ChartArea(p.left, p.top, width - p.right, height - p.bottom)
    }

    /** 成交量副图区域（底部 25%） */
    private val volumeArea: ChartArea? by lazy {
        if (attr.showVolume) {
            val h = area.height * 0.25f
            ChartArea(area.left, area.bottom - h, area.right, area.bottom)
        } else {
            null
        }
    }

    /** 价格区（上方 75%） */
    private val priceArea: ChartArea by lazy {
        if (volumeArea != null) {
            ChartArea(area.left, area.top, area.right, volumeArea!!.top - 4f)
        } else {
            area
        }
    }

    /** Y 轴值域（基于 high/low） */
    private val valueRange: ValueRange by lazy {
        val values = attr.bars.flatMap { listOf(it.high, it.low) }
        ChartMath.computeValueRange(values, attr.yAxis.min, attr.yAxis.max, attr.yAxis.step)
    }

    /** X 轴刻度文案：优先自定义，其次日期 */
    private val xLabels: List<String> by lazy {
        if (attr.xAxis.labels.isNotEmpty()) attr.xAxis.labels
        else attr.bars.map { it.date }
    }

    /** 主渲染入口 */
    fun render(context: CanvasContext) {
        context.drawBackground(attr.backgroundColor, width, height)
        if (attr.bars.isEmpty()) return
        context.drawGridAndAxes(priceArea, attr.xAxis, attr.yAxis, valueRange, xLabels)
        drawCandles(context)
        volumeArea?.let { drawVolume(context, it) }
    }

    // ==================== 私有绘制逻辑 ====================

    private fun drawCandles(context: CanvasContext) {
        val count = attr.bars.size
        val slotWidth = priceArea.width / count
        val candleWidth = slotWidth * attr.candleWidthRatio
        val half = candleWidth / 2f

        for (i in 0 until count) {
            val bar = attr.bars[i]
            val isUp = bar.close >= bar.open
            val color = if (isUp) attr.upColor else attr.downColor
            val x = ChartMath.indexToX(i, count, priceArea)

            // 影线（最高价 -> 最低价）
            val highY = ChartMath.valueToY(bar.high, valueRange, priceArea)
            val lowY = ChartMath.valueToY(bar.low, valueRange, priceArea)
            context.save()
            context.beginPath()
            context.moveTo(x, highY)
            context.lineTo(x, lowY)
            context.strokeStyle(color)
            context.lineWidth(1f)
            context.stroke()

            // 实体（开盘价 <-> 收盘价）
            val openY = ChartMath.valueToY(bar.open, valueRange, priceArea)
            val closeY = ChartMath.valueToY(bar.close, valueRange, priceArea)
            val topY = minOf(openY, closeY)
            val bodyH = max(abs(closeY - openY), 1f)
            context.beginPath()
            context.moveTo(x - half, topY)
            context.lineTo(x + half, topY)
            context.lineTo(x + half, topY + bodyH)
            context.lineTo(x - half, topY + bodyH)
            context.closePath()
            context.fillStyle(color)
            context.fill()
            context.restore()
        }
    }

    private fun drawVolume(context: CanvasContext, vArea: ChartArea) {
        val maxVol = attr.bars.maxOfOrNull { it.volume }?.toFloat() ?: return
        if (maxVol <= 0f) return
        val count = attr.bars.size
        val slotWidth = vArea.width / count
        val barWidth = slotWidth * attr.candleWidthRatio
        val half = barWidth / 2f

        for (i in 0 until count) {
            val bar = attr.bars[i]
            val h = vArea.height * (bar.volume.toFloat() / maxVol)
            if (h <= 0.5f) continue
            val x = ChartMath.indexToX(i, count, vArea)
            val color = if (bar.close >= bar.open) {
                attr.upColor.opacity(0.55f)
            } else {
                attr.downColor.opacity(0.55f)
            }
            context.save()
            context.beginPath()
            context.moveTo(x - half, vArea.bottom)
            context.lineTo(x + half, vArea.bottom)
            context.lineTo(x + half, vArea.bottom - h)
            context.lineTo(x - half, vArea.bottom - h)
            context.closePath()
            context.fillStyle(color)
            context.fill()
            context.restore()
        }
    }
}
