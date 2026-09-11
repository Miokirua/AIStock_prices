package com.example.kuiklychart.chart.base

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.TextAlign
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * 绘图区域（数据绘制区），由画布尺寸减去内边距得到。
 */
data class ChartArea(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * 热区矩形（用于命中检测，如「重置缩放」按钮）。
 */
data class ChartRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom
}

/**
 * Y 轴值域与步长。
 */
data class ValueRange(
    val min: Float,
    val max: Float,
    val step: Float
) {
    val span: Float get() = max - min

    /** 是否包含指定值（含端点） */
    fun contains(value: Float): Boolean = value >= min && value <= max
}

/**
 * 图表坐标计算与绘制工具。
 */
object ChartMath {

    /** 计算 Y 轴值域：自动对 min/max 取整，保证 [min, max] 能被 step 整除 */
    fun computeValueRange(values: List<Float>, axisMin: Float?, axisMax: Float?, axisStep: Float?): ValueRange {
        var dataMin = values.minOrNull() ?: 0f
        var dataMax = values.maxOrNull() ?: 1f
        if (dataMin == dataMax) {
            val pad = if (dataMin == 0f) 1f else abs(dataMin) * 0.1f
            dataMin -= pad
            dataMax += pad
        }
        val step = axisStep ?: niceStep(dataMax - dataMin)
        val min = if (axisMin != null) axisMin else niceFloor(dataMin, step)
        val max = if (axisMax != null) axisMax else niceCeil(dataMax, step)
        return ValueRange(min, max, step)
    }

    /** 计算友好步长（1/2/5 的整数倍），目标约 5 个刻度 */
    fun niceStep(span: Float, targetDivisions: Int = 5): Float {
        if (span <= 0f) return 1f
        val rawStep = span / targetDivisions
        val magnitude = 10f.pow(floor(log10(rawStep)))
        val normalized = rawStep / magnitude
        val niceNormalized = when {
            normalized < 1.5f -> 1f
            normalized < 3f -> 2f
            normalized < 7f -> 5f
            else -> 10f
        }
        return niceNormalized * magnitude
    }

    /** 向下取整到 step 的整数倍 */
    fun niceFloor(value: Float, step: Float): Float = floor(value / step) * step

    /** 向上取整到 step 的整数倍 */
    fun niceCeil(value: Float, step: Float): Float = ceil(value / step) * step

    /** 数值映射到 Y 像素坐标（画布坐标系，y 向下增长） */
    fun valueToY(value: Float, range: ValueRange, area: ChartArea): Float {
        val ratio = if (range.span == 0f) 0f else (value - range.min) / range.span
        return area.bottom - ratio * area.height
    }

    /** 数据点索引映射到 X 像素坐标（等距分布） */
    fun indexToX(index: Int, count: Int, area: ChartArea): Float {
        if (count <= 1) return area.left + area.width / 2f
        val ratio = index.toFloat() / (count - 1).toFloat()
        return area.left + ratio * area.width
    }

    /** 格式化数值：整数步长时省略小数 */
    fun formatNumber(value: Float, maxDecimals: Int = 2): String {
        if (value == floor(value) && abs(value) < 1e9f) {
            return value.toLong().toString()
        }
        var s = value.toString()
        val dotIndex = s.indexOf('.')
        if (dotIndex >= 0 && s.length - dotIndex - 1 > maxDecimals) {
            s = s.substring(0, dotIndex + maxDecimals + 1)
        }
        return s
    }
}

/** 收集所有系列的数据值，用于计算 Y 轴范围 */
fun collectDataValues(dataSets: List<ChartDataSet>): List<Float> {
    val values = ArrayList<Float>()
    dataSets.forEach { set ->
        set.points.forEach { values.add(it.value) }
    }
    return values
}

/** 解析 X 轴刻度文案：优先 xAxis.labels，其次数据点 label，最后使用索引 */
fun resolveXLabels(xAxis: XAxisConfig, dataSets: List<ChartDataSet>): List<String> {
    if (xAxis.labels.isNotEmpty()) return xAxis.labels
    val first = dataSets.firstOrNull() ?: return emptyList()
    if (first.points.any { it.label.isNotEmpty() }) {
        return first.points.map { it.label }
    }
    return first.points.indices.map { it.toString() }
}

/** 获取系列颜色：优先数据集自带，其次主题默认色 */
fun seriesColor(dataSets: List<ChartDataSet>, index: Int): Color {
    val set = dataSets.getOrNull(index) ?: return ChartTheme.DefaultSeriesColors[0]
    return set.color ?: ChartTheme.DefaultSeriesColors[index % ChartTheme.DefaultSeriesColors.size]
}

// ==================== CanvasContext 扩展绘制函数 ====================

/** 填充背景色 */
fun CanvasContext.drawBackground(color: Color, width: Float, height: Float) {
    if (color.hexColor == Color.TRANSPARENT.hexColor) return
    save()
    beginPath()
    moveTo(0f, 0f)
    lineTo(width, 0f)
    lineTo(width, height)
    lineTo(0f, height)
    closePath()
    fillStyle(color)
    fill()
    restore()
}

/**
 * 绘制网格线与坐标轴刻度。
 *
 * @param area 绘图区域
 * @param xAxis X 轴配置
 * @param yAxis Y 轴配置
 * @param range Y 轴值域
 * @param xLabels X 轴刻度文案
 */
fun CanvasContext.drawGridAndAxes(
    area: ChartArea,
    xAxis: XAxisConfig,
    yAxis: YAxisConfig,
    range: ValueRange,
    xLabels: List<String>
) {
    // 1. 横向网格线 + Y 轴刻度
    if (yAxis.showGridLines || yAxis.showLabels) {
        var v = range.min
        val epsilon = range.step * 0.001f
        while (v <= range.max + epsilon) {
            val y = ChartMath.valueToY(v, range, area)
            if (yAxis.showGridLines) {
                beginPath()
                moveTo(area.left, y)
                lineTo(area.right, y)
                strokeStyle(yAxis.gridColor)
                lineWidth(yAxis.gridWidth)
                stroke()
            }
            if (yAxis.showLabels) {
                val text = yAxis.formatter?.invoke(v) ?: ChartMath.formatNumber(v, 2)
                font(yAxis.labelSize)
                textAlign(TextAlign.RIGHT)
                fillStyle(yAxis.labelColor)
                fillText(text, area.left - 6f, y + yAxis.labelSize * 0.35f)
            }
            v += range.step
        }
    }

    // 2. X 轴刻度文案
    if (xAxis.showLabels && xLabels.isNotEmpty()) {
        val count = xLabels.size
        for (i in 0 until count) {
            val x = ChartMath.indexToX(i, count, area)
            font(xAxis.labelSize)
            textAlign(TextAlign.CENTER)
            fillStyle(xAxis.labelColor)
            fillText(xLabels[i], x, area.bottom + xAxis.labelSize + 4f)
        }
    }

    // 3. 坐标轴线
    if (xAxis.showAxisLine || yAxis.showAxisLine) {
        val color = yAxis.axisColor
        val lineWidth = yAxis.axisWidth
        beginPath()
        if (yAxis.showAxisLine) {
            moveTo(area.left, area.top)
            lineTo(area.left, area.bottom)
        }
        if (xAxis.showAxisLine) {
            moveTo(area.left, area.bottom)
            lineTo(area.right, area.bottom)
        }
        strokeStyle(color)
        lineWidth(lineWidth)
        stroke()
    }
}

/**
 * 绘制图例（顶部横向排列）。
 *
 * @param area 绘图区域
 * @param dataSets 数据集
 * @param offsetY 图例相对绘图区顶部的偏移
 */
fun CanvasContext.drawLegend(area: ChartArea, dataSets: List<ChartDataSet>, offsetY: Float = -12f) {
    if (dataSets.isEmpty()) return
    val colorSize = 8f
    var x = area.left
    val baseline = area.top + offsetY + colorSize
    for (i in dataSets.indices) {
        val set = dataSets[i]
        val color = seriesColor(dataSets, i)
        // 色块
        beginPath()
        moveTo(x, baseline - colorSize)
        lineTo(x + colorSize, baseline - colorSize)
        lineTo(x + colorSize, baseline)
        lineTo(x, baseline)
        closePath()
        fillStyle(color)
        fill()
        // 文案
        font(10f)
        textAlign(TextAlign.LEFT)
        fillStyle(ChartTheme.TextColor)
        val label = set.label
        fillText(label, x + colorSize + 4f, baseline)
        val labelWidth = measureText(label).width
        x += colorSize + 4f + labelWidth + 12f
    }
}
