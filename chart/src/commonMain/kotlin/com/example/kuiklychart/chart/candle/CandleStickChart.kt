package com.example.kuiklychart.chart.candle

import com.example.kuiklychart.chart.base.BaseChartEvent
import com.example.kuiklychart.chart.base.ChartDataPoint
import com.example.kuiklychart.chart.base.ChartSelection
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.TouchParams
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.View
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * K线（蜡烛图）组件。
 *
 * 使用方式（DSL）：
 * ```
 * CandleStickChart {
 *     attr {
 *         bars = listOf(
 *             CandleData("2026-08-27", 1300f, 1302.8f, 1314.45f, 1295f, 21731L),
 *             ...
 *         )
 *         xAxis { labels = listOf("08-20", "08-24", "08-25") }
 *         // 启用缩放/平移时必须提供，否则 X 轴刻度会与可见区间错位
 *         xLabelAt = { i -> bars[i].date.substring(5, 10) }
 *         upColor = Color(0xFFE0322E)   // 涨 - 红
 *         downColor = Color(0xFF0A9C5C) // 跌 - 绿
 *         showVolume = true
 *         priceLevels = listOf(PriceLevel(1300f, "支撑", Color(...)))
 *     }
 *     event {
 *         onDataPointClick = { seriesIndex, pointIndex, point -> ... }
 *     }
 * }
 * ```
 *
 * ### 交互（v1.9.28「探」层）
 * - 双指捏合：连续缩放可见根数
 * - 双击：在预设档位与「全部」之间循环
 * - 已缩放时单指水平拖动：平移可见窗口
 * - 未缩放时单指水平拖动 / 长按拖动：显示 OHLCV 数值浮标（长按后锁定，单击清除）
 * - 右上角「＋标记」：把当前价（十字光标优先）经 `attr.onMarkRequest` 回调给业务层
 * - 右上角「重置」：恢复全部（仅在已缩放时出现，排在「＋标记」左侧）
 *
 * **实现说明**：Android 渲染层（Kuikly 2.7.0）并未实现 `pinch` 事件，且 `pan` 会
 * `requestDisallowInterceptTouchEvent` 抢走父级 Scroller 的竖直滚动。因此这里统一走
 * 底层 `touchDown/touchMove/touchUp`：靠 `touches` 多指信息自己算捏合比例，靠
 * 单指位移方向判定（水平=图表操作，竖直=交给页面滚动）。
 */
class CandleStickChart : ComposeView<CandleStickChartAttr, BaseChartEvent>() {

    /** 最近一次绘制的画布尺寸，供点击命中检测使用 */
    private var canvasWidth = 0f
    private var canvasHeight = 0f

    /** 当前选中状态（observable，变更后自动重绘） */
    private var selection by observable(ChartSelection())

    /** 可见窗口起始索引（全量索引） */
    private var viewStart by observable(0)

    /** 可见根数（0 表示全部） */
    private var viewCount by observable(0)

    /** 十字光标所在的全量索引（-1 表示不显示） */
    private var crosshairIndex by observable(-1)

    /** 十字光标是否锁定（长按后保持，单击清除） */
    private var locked by observable(false)

    // ---------------- 手势临时状态（不参与渲染依赖，避免无谓重绘） ----------------
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragBaseStart = 0
    private var dragMode = DRAG_NONE
    private var pinchBaseDist = 0f
    private var pinchBaseCount = 0
    private var pinchAnchorIndex = 0
    private var pinchAnchorRel = 0.5f
    private var multiTouch = false

    override fun createEvent(): BaseChartEvent {
        return BaseChartEvent()
    }

    override fun createAttr(): CandleStickChartAttr {
        return CandleStickChartAttr()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flex(1f)
                }
                event {
                    click { p ->
                        if (!ctx.attr.interactive) {
                            ctx.handleTap(p.x, p.y)
                            return@click
                        }
                        // 命中右上角「＋标记」：把当前价（有十字光标取该根收盘价）回调给业务层。
                        // 必须在下面清十字光标之前判定并 return，否则读数会被一并清掉。
                        val markRect = ctx.renderer().markChipRect()
                        if (markRect != null && markRect.contains(p.x, p.y)) {
                            val price = ctx.renderer().markPrice(ctx.crosshairIndex)
                            if (price > 0f) ctx.attr.onMarkRequest?.invoke(price)
                            return@click
                        }
                        val rect = ctx.renderer().resetChipRect()
                        if (rect != null && rect.contains(p.x, p.y)) {
                            ctx.resetZoom()
                            return@click
                        }
                        ctx.locked = false
                        ctx.crosshairIndex = -1
                        ctx.handleTap(p.x, p.y)
                    }
                    doubleClick { p ->
                        if (!ctx.attr.interactive) return@doubleClick
                        ctx.toggleZoomStep(p.x)
                    }
                    longPress { p ->
                        if (!ctx.attr.interactive) return@longPress
                        when (p.state) {
                            "start" -> {
                                ctx.locked = true
                                ctx.dragMode = DRAG_CROSSHAIR
                                ctx.crosshairIndex = ctx.renderer().indexAtX(p.x)
                            }
                            "move" -> {
                                if (ctx.locked) {
                                    ctx.crosshairIndex = ctx.renderer().indexAtX(p.x)
                                }
                            }
                        }
                    }
                    touchDown { p ->
                        if (!ctx.attr.interactive) return@touchDown
                        ctx.onTouchDown(p)
                    }
                    touchMove { p ->
                        if (!ctx.attr.interactive) return@touchMove
                        ctx.onTouchMove(p)
                    }
                    touchUp {
                        if (!ctx.attr.interactive) return@touchUp
                        ctx.multiTouch = false
                        ctx.dragMode = DRAG_NONE
                    }
                    touchCancel {
                        ctx.multiTouch = false
                        ctx.dragMode = DRAG_NONE
                    }
                }
                Canvas({
                    attr {
                        flex(1f)
                    }
                }) { context, w, h ->
                    ctx.canvasWidth = w
                    ctx.canvasHeight = h
                    val renderer = CandleStickChartRenderer(
                        ctx.attr, w, h, ctx.curStart(), ctx.curCount()
                    )
                    renderer.render(context, ctx.selection, ctx.crosshairIndex, ctx.locked)
                }
            }
        }
    }

    // ==================== 可见窗口计算 ====================

    private fun totalCount(): Int = attr.bars.size

    /** 钳制后的可见根数 */
    private fun curCount(): Int {
        val t = totalCount()
        if (t <= 0) return 0
        val min = attr.minVisibleCount.coerceAtLeast(2)
        val c = if (viewCount <= 0) t else viewCount
        return c.coerceIn(min.coerceAtMost(t), t)
    }

    /** 钳制后的起始索引 */
    private fun curStart(): Int {
        val t = totalCount()
        if (t <= 0) return 0
        return viewStart.coerceIn(0, (t - curCount()).coerceAtLeast(0))
    }

    private fun renderer(): CandleStickChartRenderer =
        CandleStickChartRenderer(attr, canvasWidth, canvasHeight, curStart(), curCount())

    /** 缩放到指定根数，尽量保持 [anchorIndex] 在窗口中的相对位置 */
    private fun zoomTo(count: Int, anchorIndex: Int) {
        val t = totalCount()
        if (t <= 0) return
        val min = attr.minVisibleCount.coerceAtLeast(2)
        val newCount = count.coerceIn(min.coerceAtMost(t), t)
        val oldStart = curStart()
        val oldCount = curCount()
        val rel = if (oldCount > 0) {
            ((anchorIndex - oldStart).toFloat() / oldCount).coerceIn(0f, 1f)
        } else {
            0.5f
        }
        val newStart = (anchorIndex - rel * newCount).toInt()
            .coerceIn(0, (t - newCount).coerceAtLeast(0))
        viewCount = newCount
        viewStart = newStart
        clampCrosshair()
    }

    /** 恢复显示全部 */
    private fun resetZoom() {
        viewCount = 0
        viewStart = 0
        clampCrosshair()
    }

    /** 双击：全部 → 档位1 → 档位2 → … → 全部 */
    private fun toggleZoomStep(anchorX: Float) {
        val t = totalCount()
        if (t <= 0) return
        val steps = attr.zoomSteps.filter { it < t }
        val cur = curCount()
        // 未缩放时选第一个「明显小于总数」的档位（避免 61 根数据只缩到 60 根的无效缩放），
        // 已缩放时逐级收窄，到底后回到全部。
        val next = if (cur >= t) {
            steps.firstOrNull { it <= t * 0.7f } ?: steps.firstOrNull() ?: t
        } else {
            steps.firstOrNull { it < cur } ?: t
        }
        zoomTo(next, renderer().indexAtX(anchorX))
    }

    /** 窗口变化后，把落在窗口外的十字光标清掉 */
    private fun clampCrosshair() {
        val i = crosshairIndex
        if (i < 0) return
        val s = curStart()
        val c = curCount()
        if (i < s || i >= s + c) crosshairIndex = -1
    }

    private fun handleTap(x: Float, y: Float) {
        val hit = renderer().hitTest(x, y)
        if (hit != null) {
            selection = hit
            val bar = attr.bars.getOrNull(hit.pointIndex)
            if (bar != null) {
                event.onDataPointClick?.invoke(0, hit.pointIndex, ChartDataPoint(bar.close, bar.date))
            }
        } else {
            selection = ChartSelection()
        }
    }

    // ==================== 手势处理 ====================

    private fun onTouchDown(p: TouchParams) {
        dragStartX = p.x
        dragStartY = p.y
        dragBaseStart = curStart()
        dragMode = DRAG_NONE
        multiTouch = false
        if (p.touches.size >= 2) {
            onPinch(p)
        }
    }

    private fun onTouchMove(p: TouchParams) {
        if (p.touches.size >= 2) {
            onPinch(p)
            return
        }
        if (multiTouch) {
            // 双指抬起一指后继续单指操作：以当前位置重置基准，下一帧重新判定方向
            multiTouch = false
            dragStartX = p.x
            dragStartY = p.y
            dragBaseStart = curStart()
            dragMode = DRAG_NONE
            return
        }
        val dx = p.x - dragStartX
        val dy = p.y - dragStartY
        if (dragMode == DRAG_NONE) {
            if (abs(dx) > DRAG_SLOP && abs(dx) > abs(dy)) {
                dragMode = if (locked || curCount() >= totalCount()) DRAG_CROSSHAIR else DRAG_PAN
                if (dragMode == DRAG_CROSSHAIR && crosshairIndex < 0) {
                    crosshairIndex = renderer().indexAtX(p.x)
                }
            } else if (abs(dy) > DRAG_SLOP) {
                // 竖直为主 → 判定为页面滚动，本组件彻底放手
                dragMode = DRAG_ABORT
                return
            } else {
                return
            }
        }
        when (dragMode) {
            DRAG_CROSSHAIR -> crosshairIndex = renderer().indexAtX(p.x)
            DRAG_PAN -> {
                val seg = renderer().slotWidth()
                if (seg > 0f) {
                    val delta = -(dx / seg).toInt()
                    val t = totalCount()
                    val c = curCount()
                    viewStart = (dragBaseStart + delta).coerceIn(0, (t - c).coerceAtLeast(0))
                    clampCrosshair()
                }
            }
        }
    }

    /** 双指捏合：按两指距离比例改变可见根数，锚点保持第一根手指下的 K 线 */
    private fun onPinch(p: TouchParams) {
        val ts = p.touches
        if (ts.size < 2) return
        val dx = ts[0].pageX - ts[1].pageX
        val dy = ts[0].pageY - ts[1].pageY
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= 0f) return
        if (!multiTouch) {
            multiTouch = true
            dragMode = DRAG_ABORT // 缩放期间不参与平移/读数
            pinchBaseDist = dist
            pinchBaseCount = curCount()
            val anchor = renderer().indexAtX(p.x)
            pinchAnchorIndex = anchor
            val s = curStart()
            val c = curCount()
            pinchAnchorRel = if (c > 0) ((anchor - s).toFloat() / c).coerceIn(0f, 1f) else 0.5f
            return
        }
        val scale = dist / pinchBaseDist
        if (scale <= 0f) return
        val t = totalCount()
        val min = attr.minVisibleCount.coerceAtLeast(2)
        val newCount = (pinchBaseCount / scale).toInt().coerceIn(min.coerceAtMost(t), t)
        if (newCount == curCount()) return
        val newStart = (pinchAnchorIndex - pinchAnchorRel * newCount).toInt()
            .coerceIn(0, (t - newCount).coerceAtLeast(0))
        viewCount = newCount
        viewStart = newStart
        clampCrosshair()
    }

    private companion object {
        const val DRAG_NONE = 0
        const val DRAG_PAN = 1
        const val DRAG_CROSSHAIR = 2
        const val DRAG_ABORT = 3
        const val DRAG_SLOP = 8f
    }
}

/**
 * K线图 DSL 扩展，将 [CandleStickChart] 添加到视图树。
 *
 * 用法：在任意 `ViewContainer` 作用域内 `CandleStickChart { attr { ... } }`
 */
fun ViewContainer<*, *>.CandleStickChart(init: CandleStickChart.() -> Unit) {
    addChild(CandleStickChart(), init)
}
