package com.example.kuiklychart.chart.bar

import com.example.kuiklychart.chart.base.ChartArea
import com.example.kuiklychart.chart.base.ChartMath
import com.example.kuiklychart.chart.base.ChartSelection
import com.example.kuiklychart.chart.base.ValueRange
import com.example.kuiklychart.chart.base.collectDataValues
import com.example.kuiklychart.chart.base.drawBackground
import com.example.kuiklychart.chart.base.drawGridAndAxes
import com.example.kuiklychart.chart.base.drawLegend
import com.example.kuiklychart.chart.base.resolveXLabels
import com.example.kuiklychart.chart.base.seriesColor
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.TextAlign
import kotlin.math.max
import kotlin.math.min

/** 柱状条矩形（用于命中检测） */
private data class BarRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    val width: Float get() = right - left
}

/**
 * 柱状图渲染器：负责将数据绘制到 Canvas 上。
 *
 * 绘制顺序：背景 → 网格/坐标轴 → 柱状条 → 数值标签 → 选中高亮。
 * 支持多系列分组柱状图。
 */
class BarChartRenderer(
    private val attr: BarChartAttr,
    private val width: Float,
    private val height: Float
) {

    private val area: ChartArea by lazy {
        val p = attr.padding
        ChartArea(p.left, p.top, width - p.right, height - p.bottom)
    }

    private val valueRange: ValueRange by lazy {
        ChartMath.computeValueRange(
            collectDataValues(attr.dataSets),
            attr.yAxis.min,
            attr.yAxis.max,
            attr.yAxis.step
        )
    }

    private val xLabels: List<String> by lazy {
        resolveXLabels(attr.xAxis, attr.dataSets)
    }

    /** 最大数据点数 */
    private val maxPointCount: Int
        get() = attr.dataSets.maxOfOrNull { it.points.size } ?: 0

    /** 系列数量 */
    private val seriesCount: Int
        get() = attr.dataSets.size

    fun render(context: CanvasContext, selection: ChartSelection) {
        context.drawBackground(attr.backgroundColor, width, height)
        if (attr.dataSets.isEmpty()) return
        context.drawGridAndAxes(area, attr.xAxis, attr.yAxis, valueRange, xLabels)
        if (attr.showLegend) {
            context.drawLegend(area, attr.dataSets)
        }

        for (s in 0 until seriesCount) {
            for (i in attr.dataSets[s].points.indices) {
                val rect = barRect(s, i) ?: continue
                drawBar(context, s, i, rect)
            }
        }

        if (attr.showValueLabels) {
            for (s in 0 until seriesCount) {
                for (i in attr.dataSets[s].points.indices) {
                    val rect = barRect(s, i) ?: continue
                    drawValueLabel(context, s, i, rect)
                }
            }
        }

        if (selection.pointIndex >= 0) {
            drawHighlight(context, selection)
        }
    }

    /** 点击命中检测 */
    fun hitTest(touchX: Float, touchY: Float): ChartSelection? {
        for (s in 0 until seriesCount) {
            val points = attr.dataSets[s].points
            for (i in points.indices) {
                val rect = barRect(s, i) ?: continue
                val tol = attr.hitTolerance
                if (touchX >= rect.left - tol && touchX <= rect.right + tol &&
                    touchY >= rect.top - tol && touchY <= rect.bottom + tol
                ) {
                    return ChartSelection(s, i)
                }
            }
        }
        return null
    }

    // ==================== 私有绘制逻辑 ====================

    /** 计算第 s 个系列第 i 个点的柱状条矩形；数据缺失返回 null */
    private fun barRect(setIndex: Int, pointIndex: Int): BarRect? {
        val count = maxPointCount
        if (count == 0 || seriesCount == 0) return null
        val value = attr.dataSets[setIndex].points.getOrNull(pointIndex)?.value ?: return null

        val slotCenterX = ChartMath.indexToX(pointIndex, count, area)
        val barW = actualBarWidth()
        val groupWidth = barW * seriesCount + attr.barSpacing * (seriesCount - 1)
        val groupLeft = slotCenterX - groupWidth / 2f
        val barLeft = groupLeft + setIndex * (barW + attr.barSpacing)

        // 基线：0 在值域内则用 0，否则用值域下界
        val zeroClipped = if (valueRange.min <= 0f && valueRange.max >= 0f) 0f else valueRange.min
        val baselineY = ChartMath.valueToY(zeroClipped, valueRange, area)
        val valueY = ChartMath.valueToY(value, valueRange, area)
        val barTop = min(baselineY, valueY)
        val barBottom = max(baselineY, valueY)

        return BarRect(barLeft, barTop, barLeft + barW, barBottom)
    }

    private fun actualBarWidth(): Float {
        attr.barWidth?.let { return it }
        val count = max(1, maxPointCount)
        val slot = area.width / count
        return (slot * 0.8f) / max(1, seriesCount)
    }

    private fun barColor(setIndex: Int, pointIndex: Int): Color {
        if (attr.barColors.isNotEmpty()) {
            return attr.barColors[pointIndex % attr.barColors.size]
        }
        return seriesColor(attr.dataSets, setIndex)
    }

    private fun drawBar(context: CanvasContext, setIndex: Int, pointIndex: Int, rect: BarRect) {
        val color = barColor(setIndex, pointIndex)
        context.save()
        roundedRectPath(context, rect.left, rect.top, rect.width, max(0f, rect.bottom - rect.top), attr.cornerRadius)
        context.fillStyle(color)
        context.fill()
        context.restore()
    }

    private fun drawValueLabel(context: CanvasContext, setIndex: Int, pointIndex: Int, rect: BarRect) {
        val value = attr.dataSets[setIndex].points[pointIndex].value
        val text = attr.yAxis.formatter?.invoke(value) ?: ChartMath.formatNumber(value, 2)
        context.save()
        context.font(attr.valueLabelSize)
        context.textAlign(TextAlign.CENTER)
        context.fillStyle(attr.valueLabelColor)
        context.fillText(text, rect.centerX, rect.top - 4f)
        context.restore()
    }

    private fun drawHighlight(context: CanvasContext, selection: ChartSelection) {
        val rect = barRect(selection.seriesIndex, selection.pointIndex) ?: return
        context.save()
        roundedRectPath(context, rect.left, rect.top, rect.width, max(0f, rect.bottom - rect.top), attr.cornerRadius)
        context.fillStyle(attr.highlightColor.opacity(0.2f))
        context.fill()
        context.strokeStyle(attr.highlightColor)
        context.lineWidth(2f)
        context.stroke()
        context.restore()
    }

    /** 描出圆角矩形路径 */
    private fun roundedRectPath(
        context: CanvasContext,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        r: Float
    ) {
        context.beginPath()
        if (r <= 0f || w < 2f * r || h < 2f * r) {
            context.moveTo(x, y)
            context.lineTo(x + w, y)
            context.lineTo(x + w, y + h)
            context.lineTo(x, y + h)
            context.closePath()
        } else {
            context.moveTo(x + r, y)
            context.lineTo(x + w - r, y)
            context.quadraticCurveTo(x + w, y, x + w, y + r)
            context.lineTo(x + w, y + h)
            context.lineTo(x, y + h)
            context.lineTo(x, y + r)
            context.quadraticCurveTo(x, y, x + r, y)
            context.closePath()
        }
    }
}

