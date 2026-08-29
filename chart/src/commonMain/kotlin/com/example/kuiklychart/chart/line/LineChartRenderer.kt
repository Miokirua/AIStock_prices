package com.example.kuiklychart.chart.line

import com.example.kuiklychart.chart.base.ChartArea
import com.example.kuiklychart.chart.base.ChartDataPoint
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
import kotlin.math.PI

/**
 * 线性图渲染器：负责将数据绘制到 Canvas 上。
 *
 * 绘制顺序：背景 → 网格/坐标轴 → 面积填充 → 折线 → 数据点 → 选中高亮。
 */
class LineChartRenderer(
    private val attr: LineChartAttr,
    private val width: Float,
    private val height: Float
) {

    /** 绘图区域（扣除内边距） */
    private val area: ChartArea by lazy {
        val p = attr.padding
        ChartArea(p.left, p.top, width - p.right, height - p.bottom)
    }

    /** Y 轴值域 */
    private val valueRange: ValueRange by lazy {
        ChartMath.computeValueRange(
            collectDataValues(attr.dataSets),
            attr.yAxis.min,
            attr.yAxis.max,
            attr.yAxis.step
        )
    }

    /** X 轴刻度文案 */
    private val xLabels: List<String> by lazy {
        resolveXLabels(attr.xAxis, attr.dataSets)
    }

    /** 主渲染入口 */
    fun render(context: CanvasContext, selection: ChartSelection) {
        context.drawBackground(attr.backgroundColor, width, height)
        if (attr.dataSets.isEmpty()) return
        context.drawGridAndAxes(area, attr.xAxis, attr.yAxis, valueRange, xLabels)
        if (attr.showLegend) {
            context.drawLegend(area, attr.dataSets)
        }

        // 先画填充，再画折线
        if (attr.fillArea) {
            for (s in attr.dataSets.indices) {
                drawFillArea(context, s)
            }
        }
        for (s in attr.dataSets.indices) {
            drawLine(context, s)
        }
        if (attr.showDots) {
            for (s in attr.dataSets.indices) {
                drawDots(context, s)
            }
        }
        if (selection.pointIndex >= 0) {
            drawHighlight(context, selection)
        }
    }

    /** 点击命中检测：返回命中的 (系列索引, 点索引)，未命中返回 null */
    fun hitTest(touchX: Float, touchY: Float): ChartSelection? {
        for (s in attr.dataSets.indices) {
            val points = dataPoints(s)
            for (i in points.indices) {
                val dx = xFor(s, i) - touchX
                val dy = yFor(s, i) - touchY
                if (dx * dx + dy * dy <= attr.hitTolerance * attr.hitTolerance) {
                    return ChartSelection(s, i)
                }
            }
        }
        return null
    }

    // ==================== 私有绘制逻辑 ====================

    private fun dataPoints(setIndex: Int): List<ChartDataPoint> = attr.dataSets[setIndex].points

    private fun xFor(setIndex: Int, pointIndex: Int): Float {
        return ChartMath.indexToX(pointIndex, dataPoints(setIndex).size, area)
    }

    private fun yFor(setIndex: Int, pointIndex: Int): Float {
        return ChartMath.valueToY(dataPoints(setIndex)[pointIndex].value, valueRange, area)
    }

    /** 描出折线路径（支持平滑曲线） */
    private fun traceLinePath(context: CanvasContext, setIndex: Int) {
        val points = dataPoints(setIndex)
        context.moveTo(xFor(setIndex, 0), yFor(setIndex, 0))
        if (attr.smooth && points.size >= 3) {
            // 中点二次贝塞尔平滑
            for (i in 1 until points.size - 1) {
                val mx = (xFor(setIndex, i) + xFor(setIndex, i + 1)) / 2f
                val my = (yFor(setIndex, i) + yFor(setIndex, i + 1)) / 2f
                context.quadraticCurveTo(xFor(setIndex, i), yFor(setIndex, i), mx, my)
            }
            val last = points.size - 1
            context.quadraticCurveTo(
                xFor(setIndex, last - 1), yFor(setIndex, last - 1),
                xFor(setIndex, last), yFor(setIndex, last)
            )
        } else {
            for (i in 1 until points.size) {
                context.lineTo(xFor(setIndex, i), yFor(setIndex, i))
            }
        }
    }

    private fun drawLine(context: CanvasContext, setIndex: Int) {
        if (dataPoints(setIndex).size < 2) return
        val color = attr.lineColor ?: seriesColor(attr.dataSets, setIndex)
        context.save()
        context.beginPath()
        traceLinePath(context, setIndex)
        context.strokeStyle(color)
        context.lineWidth(attr.lineWidth)
        context.lineCapRound()
        context.stroke()
        context.restore()
    }

    private fun drawFillArea(context: CanvasContext, setIndex: Int) {
        val points = dataPoints(setIndex)
        if (points.size < 2) return
        val color = attr.lineColor ?: seriesColor(attr.dataSets, setIndex)
        val fill = attr.fillColor ?: color.opacity(0.2f)
        context.save()
        context.beginPath()
        traceLinePath(context, setIndex)
        context.lineTo(xFor(setIndex, points.size - 1), area.bottom)
        context.lineTo(xFor(setIndex, 0), area.bottom)
        context.closePath()
        context.fillStyle(fill)
        context.fill()
        context.restore()
    }

    private fun drawDots(context: CanvasContext, setIndex: Int) {
        val points = dataPoints(setIndex)
        val color = attr.dotColor ?: seriesColor(attr.dataSets, setIndex)
        for (i in points.indices) {
            context.save()
            context.beginPath()
            context.arc(xFor(setIndex, i), yFor(setIndex, i), attr.dotRadius, 0f, 2f * PI.toFloat(), false)
            context.fillStyle(Color.WHITE)
            context.fill()
            context.strokeStyle(color)
            context.lineWidth(1.5f)
            context.stroke()
            context.restore()
        }
    }

    private fun drawHighlight(context: CanvasContext, selection: ChartSelection) {
        if (selection.seriesIndex !in attr.dataSets.indices) return
        val points = dataPoints(selection.seriesIndex)
        if (selection.pointIndex !in points.indices) return
        val x = xFor(selection.seriesIndex, selection.pointIndex)
        val y = yFor(selection.seriesIndex, selection.pointIndex)

        // 外圈淡色
        context.save()
        context.beginPath()
        context.arc(x, y, attr.highlightRadius + 3f, 0f, 2f * PI.toFloat(), false)
        context.fillStyle(attr.highlightColor.opacity(0.25f))
        context.fill()

        // 高亮实心圆
        context.beginPath()
        context.arc(x, y, attr.highlightRadius, 0f, 2f * PI.toFloat(), false)
        context.fillStyle(attr.highlightColor)
        context.fill()
        context.restore()
    }
}
