package com.example.kuiklychart.chart.candle

import com.example.kuiklychart.chart.base.CandleData
import com.example.kuiklychart.chart.base.ChartArea
import com.example.kuiklychart.chart.base.ChartMath
import com.example.kuiklychart.chart.base.ChartRect
import com.example.kuiklychart.chart.base.ChartSelection
import com.example.kuiklychart.chart.base.PriceLevel
import com.example.kuiklychart.chart.base.ValueRange
import com.example.kuiklychart.chart.base.drawBackground
import com.example.kuiklychart.chart.base.drawGridAndAxes
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.TextAlign
import kotlin.math.abs
import kotlin.math.max

/**
 * K线（蜡烛图）渲染器。
 *
 * 布局：上方为价格区（蜡烛 + 网格坐标轴），下方为成交量副图。
 * 绘制顺序：背景 → 网格/坐标轴 → 蜡烛（影线 + 实体）→ 成交量柱 → 参考线 → 选中高亮 → 十字光标。
 *
 * 支持【可见窗口】：[startIndex] 起共 [visibleCount] 根（<=0 表示全部）。
 * 缩放/平移时 Y 值域与 X 轴刻度都基于可见窗口重算，这样放大后纵坐标会贴合区间。
 *
 * @param startIndex 可见窗口起始索引（全量索引；越界会被钳制）
 * @param visibleCount 可见根数（<=0 表示全部）
 */
class CandleStickChartRenderer(
    private val attr: CandleStickChartAttr,
    private val width: Float,
    private val height: Float,
    startIndex: Int = 0,
    visibleCount: Int = 0
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

    /** 全量数据根数 */
    private val total: Int = attr.bars.size

    /** 钳制后的可见根数 */
    private val count: Int

    /** 钳制后的起始索引 */
    private val start: Int

    /** 当前可见窗口的数据切片 */
    private val bars: List<CandleData>

    init {
        val minCount = attr.minVisibleCount.coerceAtLeast(2)
        val c = if (visibleCount <= 0) total else visibleCount
        val clampedCount = if (total <= 0) 0 else c.coerceIn(minCount.coerceAtMost(total), total)
        val clampedStart = if (total <= 0) 0 else startIndex.coerceIn(0, (total - clampedCount).coerceAtLeast(0))
        count = clampedCount
        start = clampedStart
        bars = if (count > 0) attr.bars.subList(start, start + count) else emptyList()
    }

    /** Y 轴值域（基于【可见窗口】的 high/low） */
    private val valueRange: ValueRange by lazy {
        val values = bars.flatMap { listOf(it.high, it.low) }
        ChartMath.computeValueRange(values, attr.yAxis.min, attr.yAxis.max, attr.yAxis.step)
    }

    /** X 轴刻度文案：优先用 xLabelAt 按可见窗口生成，其次自定义 labels，最后日期 */
    private val xLabels: List<String> by lazy { buildXLabels() }

    /**
     * 主渲染入口。
     *
     * @param selection 点击选中的 K 线（全量索引）
     * @param crosshairIndex 十字光标所在的全量索引（-1 表示不显示）
     * @param locked 十字光标是否处于锁定态（长按后保持）
     */
    fun render(
        context: CanvasContext,
        selection: ChartSelection,
        crosshairIndex: Int = -1,
        locked: Boolean = false
    ) {
        context.drawBackground(attr.backgroundColor, width, height)
        if (total <= 0 || count <= 0) return
        context.drawGridAndAxes(priceArea, attr.xAxis, attr.yAxis, valueRange, xLabels)
        drawCandles(context)
        volumeArea?.let { drawVolume(context, it) }
        drawPriceLevels(context)
        if (selection.pointIndex >= 0) {
            drawSelection(context, selection)
        }
        drawRangeHint(context, crosshairIndex)
        if (crosshairIndex in start until (start + count)) {
            drawCrosshair(context, crosshairIndex, locked)
        }
    }

    /** 点击命中检测：按 X 坐标反算 K 线索引（返回【全量】索引），未命中返回 null */
    fun hitTest(touchX: Float, touchY: Float): ChartSelection? {
        if (total <= 0 || count <= 0) return null
        if (touchX < area.left || touchX > area.right) return null
        if (touchY < area.top || touchY > area.bottom) return null
        val seg = if (count > 1) area.width / (count - 1) else area.width
        val idx = ((touchX - area.left) / seg + 0.5f).toInt().coerceIn(0, count - 1)
        return ChartSelection(0, start + idx)
    }

    /** 由 X 坐标反算【全量】索引（不做区域限制，供手势跟随使用） */
    fun indexAtX(touchX: Float): Int {
        if (total <= 0 || count <= 0) return -1
        val seg = if (count > 1) area.width / (count - 1) else area.width
        val local = ((touchX - area.left) / seg + 0.5f).toInt().coerceIn(0, count - 1)
        return start + local
    }

    /** 每根 K 线占的像素宽度（供手势把位移换算成根数） */
    fun slotWidth(): Float {
        if (count <= 0) return 0f
        return if (count > 1) area.width / (count - 1) else area.width
    }

    /** 绘图区宽度（供手势换算） */
    fun plotWidth(): Float = area.width

    /** 右上角按钮行的纵向范围（画在绘图区上方） */
    private fun chipTop(): Float = area.top - CHIP_H - 2f
    private fun chipBottom(): Float = area.top - 2f

    /** 「＋标记」按钮热区；有数据且开启时返回（始终显示，用于标记十字光标价/最新价） */
    fun markChipRect(): ChartRect? {
        if (!attr.interactive || !attr.showMarkButton) return null
        if (total <= 0 || count <= 0) return null
        return ChartRect(area.right - MARK_CHIP_W, chipTop(), area.right, chipBottom())
    }

    /** 点「＋标记」时要把哪个价格回调出去：十字光标优先，否则最后一根收盘价 */
    fun markPrice(crosshairIndex: Int): Float {
        val idx = if (crosshairIndex in attr.bars.indices) crosshairIndex else attr.bars.lastIndex
        return attr.bars.getOrNull(idx)?.close ?: 0f
    }

    /** 「重置缩放」按钮热区；仅在已缩放时返回非空（排在「标记」左侧，避免重叠） */
    fun resetChipRect(): ChartRect? {
        if (!attr.interactive || !attr.showResetButton) return null
        if (total <= 0 || count >= total) return null
        val right = area.right - (if (markChipRect() != null) MARK_CHIP_W + CHIP_GAP else 0f)
        return ChartRect(right - RESET_CHIP_W, chipTop(), right, chipBottom())
    }

    // ==================== 私有绘制逻辑 ====================

    /** 生成 X 轴刻度文案：按可见窗口均匀取最多 5 个索引 */
    private fun buildXLabels(): List<String> {
        val provider = attr.xLabelAt
        if (provider == null) return attr.xAxis.labels
        if (count <= 0) return emptyList()
        val n = minOf(5, count)
        if (n <= 1) return listOf(provider(start))
        val out = ArrayList<String>(n)
        for (i in 0 until n) {
            val local = (i.toFloat() * (count - 1) / (n - 1)).toInt()
            out.add(provider(start + local))
        }
        return out
    }

    /** 绘制价格参考线（支撑/压力位） */
    private fun drawPriceLevels(context: CanvasContext) {
        if (attr.priceLevels.isEmpty()) return
        for (level in attr.priceLevels) {
            if (level.price <= 0) continue
            val y = ChartMath.valueToY(level.price, valueRange, priceArea)
            if (y < priceArea.top || y > priceArea.bottom) continue
            val color = level.color
            context.save()
            context.beginPath()
            context.moveTo(priceArea.left, y)
            context.lineTo(priceArea.right, y)
            // 线宽由数据决定：用户手动标记的关键位传更粗的 width，一眼能从 AI 参考线里认出来
            context.strokeStyle(color.opacity(if (level.width > 1f) 0.95f else 0.75f))
            context.lineWidth(level.width)
            context.stroke()
            context.restore()
            if (level.label.isNotBlank()) {
                context.save()
                context.font(9f)
                context.textAlign(TextAlign.RIGHT)
                context.fillStyle(color)
                context.fillText(level.label, priceArea.right - 4f, y - 2f)
                context.restore()
            }
        }
    }

    /** 高亮选中的 K 线槽位 */
    private fun drawSelection(context: CanvasContext, selection: ChartSelection) {
        val global = selection.pointIndex
        if (global < start || global >= start + count) return
        val local = global - start
        val slotWidth = priceArea.width / count
        val x = ChartMath.indexToX(local, count, priceArea)
        val half = slotWidth / 2f
        context.save()
        context.beginPath()
        context.moveTo(x - half, area.top)
        context.lineTo(x + half, area.top)
        context.lineTo(x + half, area.bottom)
        context.lineTo(x - half, area.bottom)
        context.closePath()
        context.fillStyle(Color(0x14000000))
        context.fill()
        context.restore()
    }

    /**
     * 十字光标 + 数值浮标：竖线（按索引）、横线（按收盘价）、
     * 顶部日期条、右侧价格条，以及跟随在竖线一侧的 OHLCV 面板。
     */
    private fun drawCrosshair(context: CanvasContext, globalIndex: Int, locked: Boolean) {
        val local = globalIndex - start
        val bar = bars.getOrNull(local) ?: return
        val x = ChartMath.indexToX(local, count, priceArea)
        val y = ChartMath.valueToY(bar.close, valueRange, priceArea)
        val lineColor = attr.crosshairColor

        // 1) 竖线：贯穿价格区与成交量区
        context.save()
        context.beginPath()
        context.setLineDash(listOf(3f, 3f))
        context.moveTo(x, area.top)
        context.lineTo(x, area.bottom)
        context.strokeStyle(lineColor)
        context.lineWidth(1f)
        context.stroke()
        context.restore()

        // 2) 横线：收盘价位置
        context.save()
        context.beginPath()
        context.setLineDash(listOf(3f, 3f))
        context.moveTo(area.left, y)
        context.lineTo(area.right, y)
        context.strokeStyle(lineColor)
        context.lineWidth(1f)
        context.stroke()
        context.restore()

        // 3) 中心小圆点（锁定态实心，跟随态空心）
        context.save()
        context.beginPath()
        context.arc(x, y, if (locked) 3f else 2.5f, 0f, 360f, false)
        context.fillStyle(if (locked) lineColor else attr.crosshairPanelBg)
        context.fill()
        context.strokeStyle(lineColor)
        context.lineWidth(1f)
        context.stroke()
        context.restore()

        // 4) 顶部日期条（跟随竖线，左右夹紧）
        val dateText = bar.date
        if (dateText.isNotBlank()) {
            context.save()
            context.font(9.5f)
            val tw = context.measureText(dateText).width + 10f
            val cx = x.coerceIn(area.left + tw / 2f, area.right - tw / 2f)
            drawChip(context, cx - tw / 2f, area.top - 15f, tw, 14f, lineColor, dateText, attr.crosshairPanelText)
            context.restore()
        }

        // 5) 右侧价格条（贴绘图区右沿内侧）
        val priceText = fmt2(bar.close)
        context.save()
        context.font(9.5f)
        val pw = context.measureText(priceText).width + 10f
        val py = (y - 7f).coerceIn(priceArea.top, priceArea.bottom - 14f)
        drawChip(context, area.right - pw, py, pw, 14f, lineColor, priceText, attr.crosshairPanelText)
        context.restore()

        // 6) OHLCV 数值面板：默认放竖线右侧，靠近右沿时翻到左侧
        val panelW = 104f
        val panelH = 62f
        val onRight = x < area.left + (area.width - panelW) / 2f
        val px = if (onRight) x + 8f else x - 8f - panelW
        val pxx = px.coerceIn(area.left + 2f, area.right - panelW - 2f)
        val pyy = (priceArea.top + 18f).coerceAtMost(priceArea.bottom - panelH - 2f)
        drawOhlcPanel(context, pxx, pyy, panelW, panelH, bar)
    }

    /** 圆角小标签（日期条 / 价格条） */
    private fun drawChip(
        context: CanvasContext,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        bg: Color,
        text: String,
        textColor: Color
    ) {
        context.save()
        context.beginPath()
        context.moveTo(x, y)
        context.lineTo(x + w, y)
        context.lineTo(x + w, y + h)
        context.lineTo(x, y + h)
        context.closePath()
        context.fillStyle(bg)
        context.fill()
        context.textAlign(TextAlign.CENTER)
        context.fillStyle(textColor)
        context.fillText(text, x + w / 2f, y + h * 0.72f)
        context.restore()
    }

    /** OHLCV 数值面板：两列五行小字 */
    private fun drawOhlcPanel(
        context: CanvasContext,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        bar: CandleData
    ) {
        context.save()
        context.beginPath()
        context.moveTo(x, y)
        context.lineTo(x + w, y)
        context.lineTo(x + w, y + h)
        context.lineTo(x, y + h)
        context.closePath()
        context.fillStyle(attr.crosshairPanelBg)
        context.fill()

        val up = bar.close >= bar.open
        val lineColor = if (up) attr.upColor else attr.downColor
        val rows = listOf(
            Pair("开", fmt2(bar.open)),
            Pair("高", fmt2(bar.high)),
            Pair("低", fmt2(bar.low)),
            Pair("收", fmt2(bar.close)),
            Pair("量", formatVolume(bar.volume))
        )
        context.font(9.5f)
        var ry = y + 13f
        for ((label, value) in rows) {
            context.textAlign(TextAlign.LEFT)
            context.fillStyle(attr.hintTextColor)
            context.fillText(label, x + 7f, ry)
            val isClose = label == "收"
            // 数值必须右对齐：否则以左对齐从右沿起画会溢出面板被裁掉
            context.textAlign(TextAlign.RIGHT)
            context.fillStyle(if (isClose) lineColor else attr.crosshairPanelText)
            context.fillText(value, x + w - 7f, ry)
            ry += 10f
        }
        context.restore()
    }

    /**
     * 固定两位小数。
     * 读数面板必须定宽格式——ChartMath.formatNumber 会把 4.60 输出成 "4.6"，
     * 与 4.81 之类的值混排会导致一列数字长度不齐。
     */
    private fun fmt2(v: Float): String {
        val neg = v < 0f
        val abs = if (neg) -v else v
        val scaled = (abs * 100f + 0.5f).toInt()
        val i = scaled / 100
        val f = scaled % 100
        val fs = if (f < 10) "0$f" else "$f"
        return if (neg) "-$i.$fs" else "$i.$fs"
    }

    /** 成交量简写（手 → 万/亿），避免面板被长数字撑爆 */
    private fun formatVolume(volume: Long): String {
        val v = volume.toFloat()
        return when {
            v >= 1e8f -> "${ChartMath.formatNumber(v / 1e8f, 2)}亿"
            v >= 1e4f -> "${ChartMath.formatNumber(v / 1e4f, 2)}万"
            else -> ChartMath.formatNumber(v, 0)
        }
    }

    /** 左上角可见区间提示 + 右上角「＋标记」/「重置」按钮组 */
    private fun drawRangeHint(context: CanvasContext, crosshairIndex: Int) {
        if (!attr.interactive) return
        val zoomed = count < total
        val first = attr.bars.getOrNull(start)?.date ?: ""
        val last = attr.bars.getOrNull(start + count - 1)?.date ?: ""
        val rangeText = if (zoomed) {
            "${shortDate(first)}~${shortDate(last)} · $count/$total 根"
        } else {
            "共 $total 根 · 双指缩放 · 长按读数"
        }
        context.save()
        context.font(9.5f)
        context.textAlign(TextAlign.LEFT)
        context.fillStyle(attr.hintTextColor)
        context.fillText(rangeText, area.left, area.top - 4f)
        context.restore()

        // 「＋标记」：始终显示。有十字光标时按钮文案带出价格（明确要标哪一个价位）
        val markRect = markChipRect()
        if (markRect != null) {
            val onCross = crosshairIndex in start until (start + count)
            drawChip(
                context,
                markRect,
                if (onCross) "标记" + fmt2(markPrice(crosshairIndex)) else "标记最新价",
                highlighted = onCross
            )
        }
        val resetRect = resetChipRect()
        if (resetRect != null) {
            drawChip(context, resetRect, "重置", highlighted = false)
        }
    }

    /** 画一个右上角小按钮（实心圆角矩形 + 居中文字） */
    private fun drawChip(context: CanvasContext, rect: ChartRect, text: String, highlighted: Boolean) {
        context.save()
        context.beginPath()
        context.moveTo(rect.left, rect.top)
        context.lineTo(rect.right, rect.top)
        context.lineTo(rect.right, rect.bottom)
        context.lineTo(rect.left, rect.bottom)
        context.closePath()
        context.fillStyle(attr.crosshairColor.opacity(if (highlighted) 1f else 0.9f))
        context.fill()
        context.font(9.5f)
        context.textAlign(TextAlign.CENTER)
        context.fillStyle(attr.crosshairPanelText)
        context.fillText(text, (rect.left + rect.right) / 2f, rect.bottom - 4f)
        context.restore()
    }

    private fun shortDate(date: String): String =
        if (date.length >= 10) date.substring(5, 10) else date

    private fun drawCandles(context: CanvasContext) {
        val slotWidth = priceArea.width / count
        val candleWidth = slotWidth * attr.candleWidthRatio
        val half = candleWidth / 2f

        for (i in 0 until count) {
            val bar = bars[i]
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
        val maxVol = bars.maxOfOrNull { it.volume }?.toFloat() ?: return
        if (maxVol <= 0f) return
        val slotWidth = vArea.width / count
        val barWidth = slotWidth * attr.candleWidthRatio
        val half = barWidth / 2f

        for (i in 0 until count) {
            val bar = bars[i]
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

    private companion object {
        /** 右上角按钮行高度 */
        const val CHIP_H = 16f
        /** 「＋标记」按钮宽度：需容纳「标记4.68」这种带价格的文案 */
        const val MARK_CHIP_W = 62f
        /** 「重置」按钮宽度 */
        const val RESET_CHIP_W = 44f
        /** 两个按钮之间的间距 */
        const val CHIP_GAP = 6f
    }
}
