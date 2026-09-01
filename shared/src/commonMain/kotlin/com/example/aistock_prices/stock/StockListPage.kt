package com.example.aistock_prices.stock

import com.example.aistock_prices.base.BasePager
import com.example.aistock_prices.base.bridgeModule
import com.example.aistock_prices.base.setTimeout
import com.example.aistock_prices.stock.ai.AiConfigView
import com.example.aistock_prices.stock.data.StockCache
import com.example.aistock_prices.stock.data.StockMeta
import com.example.aistock_prices.stock.data.StockQuote
import com.example.aistock_prices.stock.data.StockRepository
import com.example.aistock_prices.stock.data.Watchlist
import com.example.aistock_prices.stock.ui.StockColors
import com.example.aistock_prices.stock.ui.StockFormat
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.event.TouchParams
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Refresh
import com.tencent.kuikly.core.views.RefreshView
import com.tencent.kuikly.core.views.RefreshViewState
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs

/**
 * 首页：自选股行情列表 + 底部任务栏（自选股 / AI设置）
 * 支持：添加/删除自选、行情本地缓存优先展示、60 秒轮询刷新（K线除外）。
 */
@Page("stock_list", supportInLocal = true)
internal class StockListPage : BasePager() {

    private var quotes by observableList<StockQuote>()
    private var loading by observable(true)
    private var errorMsg by observable("")
    private var refreshText by observable("下拉刷新")
    private var lastUpdated by observable("")
    private var currentTab by observable(0)
    private var showAddDialog by observable(false)
    private var pendingRemove by observable<StockQuote?>(null)
    private var addInput by observable("")
    private var addInputRef: ViewRef<InputView>? = null
    private var refreshRef: ViewRef<RefreshView>? = null
    private var pollTimerRef = ""

    /** 左滑操作条宽度（置顶 72f + 删除 72f） */
    private val actionWidth = 144f
    /** 手势方向判定阈值（dp），超过才认定是水平/垂直手势 */
    private val swipeSlop = 8f
    /** 每次左滑手势结束后屏蔽点击进详情的时间窗口（ms） */
    private val swipeClickGuardMs = 300L
    /** 左滑呼出/收回动画时长（秒） */
    private val swipeAnimDuration = 0.2f
    /** 每行左滑状态：code -> SwipeState */
    private val swipeStates = mutableMapOf<String, SwipeState>()

    /**
     * 单行左滑状态。
     * offset：内容行水平位移（0f 归位 / -actionWidth 展开），observable 驱动 transform 与动画
     * 其余字段为手势过程量，非响应式
     */
    private class SwipeState {
        var offset by observable(0f)
        var startX = 0f
        var startY = 0f
        var gestureHandled = false
        var lastGestureEndTime = 0L
    }

    private val network: NetworkModule
        get() = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)

    private val sp: SharedPreferencesModule
        get() = acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                flex(1f)
                backgroundColor(StockColors.BG_PAGE)
            }
            // ---------- 顶栏 ----------
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingTop(pagerData.statusBarHeight)
                    height(56f + pagerData.statusBarHeight)
                    backgroundColor(Color.WHITE)
                    paddingLeft(16f)
                    paddingRight(16f)
                }
                Text {
                    attr {
                        flex(1f)
                        text(if (ctx.currentTab == 0) "自选股" else "AI 设置")
                        fontSize(18f)
                        fontWeightBold()
                        color(StockColors.TEXT_MAIN)
                    }
                }
                vif({ ctx.currentTab == 0 }) {
                    Text {
                        attr {
                            text("+ 添加")
                            fontSize(14f)
                            color(StockColors.ACCENT)
                            marginRight(16f)
                        }
                        event {
                            click {
                                ctx.addInput = ""
                                ctx.addInputRef?.view?.setText("")
                                ctx.showAddDialog = true
                            }
                        }
                    }
                    Text {
                        attr {
                            text("刷新")
                            fontSize(14f)
                            color(StockColors.ACCENT)
                        }
                        event {
                            click { ctx.onRefreshTap() }
                        }
                    }
                }
            }

            // ---------- 内容区：自选股 Tab ----------
            vif({ ctx.currentTab == 0 }) {
                ctx.listContent().invoke(this)
            }

            // ---------- 内容区：AI设置 Tab ----------
            vif({ ctx.currentTab == 1 }) {
                AiConfigView {
                    event {
                        onSaved = { ctx.bridgeModule.toast("配置已保存") }
                        onInvalid = { msg -> ctx.bridgeModule.toast(msg) }
                    }
                }
            }

            // ---------- 底部任务栏 ----------
            View {
                attr {
                    flexDirectionColumn()
                    backgroundColor(Color.WHITE)
                    border(Border(0.5f, BorderStyle.SOLID, Color(0xFFE4E4E4)))
                }
                View {
                    attr {
                        flexDirectionRow()
                        height(54f)
                    }
                    ctx.tabItem("自选股", 0).invoke(this)
                    ctx.tabItem("AI 设置", 1).invoke(this)
                }
            }

            // ---------- 添加自选弹窗 ----------
            vif({ ctx.showAddDialog }) {
                Modal {
                    View {
                        attr {
                            flex(1f)
                            allCenter()
                            backgroundColor(Color(0x66000000))
                        }
                        event {
                            click { ctx.showAddDialog = false }
                        }
                        View {
                            attr {
                                width(ctx.pagerData.pageViewWidth - 60f)
                                borderRadius(12f)
                                backgroundColor(Color.WHITE)
                                padding(20f)
                            }
                            event {
                                click { }
                            }
                            Text {
                                attr {
                                    text("添加自选股")
                                    fontSize(16f)
                                    fontWeightSemiBold()
                                    color(StockColors.TEXT_MAIN)
                                    marginBottom(14f)
                                }
                            }
                            View {
                                attr {
                                    height(44f)
                                    borderRadius(8f)
                                    backgroundColor(Color(0xFFF5F6F8))
                                    paddingLeft(12f)
                                    paddingRight(12f)
                                    flexDirectionRow()
                                    alignItemsCenter()
                                }
                                Input {
                                    ref { ctx.addInputRef = it }
                                    attr {
                                        flex(1f)
                                        height(40f)   // 显式高度：Kuikly Input 无 height 时 Android EditText 无可点击区域
                                        fontSize(14f)
                                        color(StockColors.TEXT_MAIN)
                                        placeholder("如 600519 或 sh600519")
                                        placeholderColor(StockColors.TEXT_SUB)
                                    }
                                    event {
                                        textDidChange { ctx.addInput = it.text }
                                    }
                                }
                            }
                            View {
                                attr {
                                    flexDirectionRow()
                                    marginTop(16f)
                                }
                                View {
                                    attr {
                                        flex(1f)
                                        height(40f)
                                        borderRadius(20f)
                                        allCenter()
                                        backgroundColor(Color(0xFFF0F0F0))
                                        marginRight(12f)
                                    }
                                    Text {
                                        attr {
                                            text("取消")
                                            fontSize(14f)
                                            color(StockColors.TEXT_SUB)
                                        }
                                    }
                                    event {
                                        click { ctx.showAddDialog = false }
                                    }
                                }
                                View {
                                    attr {
                                        flex(1f)
                                        height(40f)
                                        borderRadius(20f)
                                        allCenter()
                                        backgroundColor(StockColors.ACCENT)
                                    }
                                    Text {
                                        attr {
                                            text("添加")
                                            fontSize(14f)
                                            color(Color.WHITE)
                                            fontWeightSemiBold()
                                        }
                                    }
                                    event {
                                        click { ctx.addStock() }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------- 删除确认弹窗 ----------
            vif({ ctx.pendingRemove != null }) {
                Modal {
                    View {
                        attr {
                            flex(1f)
                            allCenter()
                            backgroundColor(Color(0x66000000))
                        }
                        event {
                            click { ctx.pendingRemove = null }
                        }
                        View {
                            attr {
                                width(ctx.pagerData.pageViewWidth - 60f)
                                borderRadius(12f)
                                backgroundColor(Color.WHITE)
                                padding(20f)
                            }
                            event {
                                click { }
                            }
                            Text {
                                attr {
                                    text("移出自选")
                                    fontSize(16f)
                                    fontWeightSemiBold()
                                    color(StockColors.TEXT_MAIN)
                                    marginBottom(10f)
                                }
                            }
                            Text {
                                attr {
                                    text("确定将 ${ctx.pendingRemove?.name} 移出自选吗？")
                                    fontSize(14f)
                                    color(StockColors.TEXT_SUB)
                                }
                            }
                            View {
                                attr {
                                    flexDirectionRow()
                                    marginTop(18f)
                                }
                                View {
                                    attr {
                                        flex(1f)
                                        height(40f)
                                        borderRadius(20f)
                                        allCenter()
                                        backgroundColor(Color(0xFFF0F0F0))
                                        marginRight(12f)
                                    }
                                    Text {
                                        attr {
                                            text("取消")
                                            fontSize(14f)
                                            color(StockColors.TEXT_SUB)
                                        }
                                    }
                                    event {
                                        click { ctx.pendingRemove = null }
                                    }
                                }
                                View {
                                    attr {
                                        flex(1f)
                                        height(40f)
                                        borderRadius(20f)
                                        allCenter()
                                        backgroundColor(StockColors.UP)
                                    }
                                    Text {
                                        attr {
                                            text("删除")
                                            fontSize(14f)
                                            color(Color.WHITE)
                                            fontWeightSemiBold()
                                        }
                                    }
                                    event {
                                        click {
                                            ctx.pendingRemove?.let { quote ->
                                                if (Watchlist.remove(ctx.sp, quote.code)) {
                                                    ctx.bridgeModule.toast("已移除 ${quote.name}")
                                                    ctx.swipeStates.remove(quote.code)
                                                    ctx.loadData()
                                                }
                                            }
                                            ctx.pendingRemove = null
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        loadData()
    }

    /** 页面每次出现：开始 60 秒轮询刷新 */
    override fun pageDidAppear() {
        super.pageDidAppear()
        startPolling()
    }

    override fun pageDidDisappear() {
        super.pageDidDisappear()
        stopPolling()
    }

    /** 自选股列表内容（loading / 错误 / 列表） */
    private fun listContent(): ViewBuilder {
        val ctx = this
        return {
            vif({ ctx.loading }) {
                View {
                    attr {
                        flex(1f)
                        allCenter()
                        flexDirectionRow()
                    }
                    ActivityIndicator {
                        attr {
                            isGrayStyle(false)
                        }
                    }
                    Text {
                        attr {
                            text("  行情加载中...")
                            fontSize(14f)
                            color(StockColors.TEXT_SUB)
                        }
                    }
                }
            }
            velse {
                vif({ ctx.errorMsg.isNotEmpty() }) {
                    View {
                        attr {
                            flex(1f)
                            allCenter()
                            flexDirectionColumn()
                        }
                        Text {
                            attr {
                                text(ctx.errorMsg)
                                fontSize(14f)
                                color(StockColors.TEXT_SUB)
                                marginBottom(12f)
                            }
                        }
                        Text {
                            attr {
                                text("点击重试")
                                fontSize(15f)
                                color(StockColors.ACCENT)
                                textDecorationUnderLine()
                            }
                            event {
                                click { ctx.loadData() }
                            }
                        }
                    }
                }
                velse {
                    Scroller {
                        attr {
                            flex(1f)
                            showScrollerIndicator(false)
                        }
                        Refresh {
                            ref {
                                ctx.refreshRef = it
                            }
                            attr {
                                height(50f)
                                allCenter()
                            }
                            event {
                                refreshStateDidChange {
                                    when (it) {
                                        RefreshViewState.REFRESHING -> {
                                            ctx.refreshText = "正在刷新..."
                                            ctx.refreshQuotes {
                                                ctx.refreshRef?.view?.endRefresh()
                                                ctx.refreshText = "下拉刷新"
                                            }
                                        }
                                        RefreshViewState.PULLING -> ctx.refreshText = "释放立即刷新"
                                        RefreshViewState.IDLE -> ctx.refreshText = "下拉刷新"
                                        else -> {}
                                    }
                                }
                            }
                            Text {
                                attr {
                                    text(ctx.refreshText)
                                    fontSize(13f)
                                    color(StockColors.TEXT_SUB)
                                }
                            }
                        }
                        vif({ ctx.lastUpdated.isNotEmpty() && ctx.quotes.isNotEmpty() }) {
                            View {
                                attr {
                                    height(28f)
                                    paddingLeft(16f)
                                    justifyContentCenter()
                                }
                                Text {
                                    attr {
                                        text("更新于 ${ctx.lastUpdated}")
                                        fontSize(11f)
                                        color(StockColors.TEXT_SUB)
                                    }
                                }
                            }
                        }
                        vfor({ ctx.quotes }) { item ->
                            ctx.quoteItem(item).invoke(this)
                        }
                    }
                }
            }
        }
    }

    /** 底部任务栏单项 */
    private fun tabItem(label: String, index: Int): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flex(1f)
                    allCenter()
                }
                Text {
                    attr {
                        text(label)
                        fontSize(14f)
                        fontWeightSemiBold()
                        color(if (ctx.currentTab == index) StockColors.ACCENT else StockColors.TEXT_SUB)
                    }
                }
                vif({ ctx.currentTab == index }) {
                    View {
                        attr {
                            width(24f)
                            height(3f)
                            borderRadius(1.5f)
                            backgroundColor(StockColors.ACCENT)
                            marginTop(4f)
                        }
                    }
                }
                event {
                    click { ctx.currentTab = index }
                }
            }
        }
    }

    /** 加载行情：先展示本地缓存，再拉新数据覆盖并写缓存 */
    private fun loadData() {
        loading = true
        errorMsg = ""
        val metas = Watchlist.stocks(sp)
        // 1. 缓存优先展示（接口临时失效时页面不空白）
        val cached = StockCache.loadQuotes(sp)
        if (cached.isNotEmpty()) {
            quotes.clear()
            quotes.addAll(cached)
            loading = false
        }
        // 2. 拉取最新数据
        fetchQuotes(metas)
    }

    /** 拉取最新报价并写缓存（静默） */
    private fun fetchQuotes(metas: List<StockMeta>) {
        StockRepository.fetchQuotes(network, metas.map { it.code }) { list ->
            if (list.isNotEmpty()) {
                quotes.clear()
                quotes.addAll(list)
                errorMsg = ""
                StockCache.saveQuotes(sp, list)
            } else if (quotes.isEmpty()) {
                errorMsg = "行情加载失败，请检查网络后重试"
            }
            loading = false
            markUpdated()
        }
    }

    /** 点击顶栏「刷新」：列表在展示态时走下拉刷新头动画，否则整页加载 */
    private fun onRefreshTap() {
        if (!loading && errorMsg.isEmpty() && quotes.isNotEmpty() && refreshRef?.view != null) {
            refreshRef?.view?.beginRefresh()
        } else {
            loadData()
        }
    }

    /** 下拉刷新（静默，不展示整页 loading） */
    private fun refreshQuotes(complete: () -> Unit) {
        val metas = Watchlist.stocks(sp)
        StockRepository.fetchQuotes(network, metas.map { it.code }) { list ->
            if (list.isNotEmpty()) {
                quotes.clear()
                quotes.addAll(list)
                errorMsg = ""
                StockCache.saveQuotes(sp, list)
            }
            markUpdated()
            bridgeModule.toast("已更新")
            complete()
        }
    }

    /** 记录最近一次刷新成功时间（用于列表顶部「更新于 HH:mm:ss」反馈） */
    private fun markUpdated() {
        val ts = bridgeModule.currentTimeStamp()
        lastUpdated = if (ts > 0) bridgeModule.dateFormatter(ts, "HH:mm:ss") else ""
    }

    /** 置顶/取消置顶后按 Watchlist 顺序本地重排（不发网络请求） */
    private fun resortQuotes() {
        val metas = Watchlist.stocks(sp)
        val byCode = quotes.associateBy { it.code }
        val reordered = metas.mapNotNull { byCode[it.code] } +
                quotes.filter { it.code !in metas.map { m -> m.code } }
        if (reordered.size == quotes.size) {
            quotes.clear()
            quotes.addAll(reordered)
        }
    }

    /** 添加自选：代码归一化 -> 有效性校验 -> 持久化 -> 刷新 */
    private fun addStock() {
        val input = addInput.trim()
        if (input.isEmpty()) {
            bridgeModule.toast("请输入股票代码")
            return
        }
        val code = Watchlist.normalizeCode(input)
        StockRepository.fetchQuotes(network, listOf(code)) { list ->
            val q = list.firstOrNull()
            if (q == null) {
                bridgeModule.toast("未找到该股票，请检查代码")
                return@fetchQuotes
            }
            val meta = StockMeta(q.code, q.name.ifBlank { q.symbol })
            if (Watchlist.add(sp, meta)) {
                bridgeModule.toast("已添加 ${meta.name}")
                showAddDialog = false
                loadData()
            } else {
                bridgeModule.toast("该股票已在自选")
            }
        }
    }

    /** 60 秒轮询：刷新自选报价（K线每日更新，不轮询） */
    private fun startPolling() {
        stopPolling()
        pollTimerRef = setTimeout(60000) {
            if (currentTab == 0) {
                fetchQuotes(Watchlist.stocks(sp))
            }
            startPolling()
        }
    }

    private fun stopPolling() {
        if (pollTimerRef.isNotEmpty()) {
            clearTimeout(pollTimerRef)
            pollTimerRef = ""
        }
    }

    /** 单条行情 item（左滑呼出 置顶/删除 操作条） */
    private fun quoteItem(quote: StockQuote): ViewBuilder {
        val ctx = this
        val trendColor = StockColors.ofChange(quote.change)
        return {
            View {
                attr {
                    flexDirectionColumn()
                    height(68f)
                    backgroundColor(Color.WHITE)
                    overflow(true) // 裁剪内容行左移后左侧超出部分
                }
                // 底层：左滑操作条（置顶 + 删除），内容行左移后露出
                View {
                    attr {
                        absolutePosition(top = 0f, right = 0f, bottom = 0f)
                        width(ctx.actionWidth)
                        flexDirectionRow()
                    }
                    // 置顶 / 取消置顶
                    View {
                        attr {
                            flex(1f)
                            allCenter()
                            backgroundColor(
                                if (Watchlist.isPinned(ctx.sp, quote.code)) Color(0xFF909399)
                                else Color(0xFFE6A23C)
                            )
                        }
                        Text {
                            attr {
                                text(if (Watchlist.isPinned(ctx.sp, quote.code)) "取消置顶" else "置顶")
                                fontSize(13f)
                                color(Color.WHITE)
                                fontWeightSemiBold()
                            }
                        }
                        event {
                            click { ctx.onSwipePin(quote) }
                        }
                    }
                    // 删除
                    View {
                        attr {
                            flex(1f)
                            allCenter()
                            backgroundColor(Color(0xFFF0483E))
                        }
                        Text {
                            attr {
                                text("删除")
                                fontSize(13f)
                                color(Color.WHITE)
                                fontWeightSemiBold()
                            }
                        }
                        event {
                            click { ctx.onSwipeDelete(quote) }
                        }
                    }
                }
                // 内容区（行布局，可左移；offset 变化时以动画过渡）
                View {
                    attr {
                        flex(1f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(16f)
                        paddingRight(16f)
                        backgroundColor(Color.WHITE)
                        transform(translate = Translate(0f, 0f, ctx.swipeOffsetOf(quote.code)))
                        animation(
                            Animation.easeInOut(ctx.swipeAnimDuration),
                            ctx.swipeOffsetOf(quote.code)
                        )
                    }
                    // 左：名称 + 代码
                    View {
                        attr {
                            flex(1f)
                            flexDirectionColumn()
                            justifyContentCenter()
                        }
                        View {
                            attr {
                                flexDirectionRow()
                                alignItemsCenter()
                            }
                            Text {
                                attr {
                                    text(quote.name)
                                    fontSize(16f)
                                    fontWeightSemiBold()
                                    color(StockColors.TEXT_MAIN)
                                }
                            }
                            vif({ Watchlist.isPinned(ctx.sp, quote.code) }) {
                                View {
                                    attr {
                                        marginLeft(6f)
                                        paddingTop(2f)
                                        paddingBottom(2f)
                                        paddingLeft(6f)
                                        paddingRight(6f)
                                        borderRadius(4f)
                                        backgroundColor(Color(0xFFFFF3E0))
                                    }
                                    Text {
                                        attr {
                                            text("置顶")
                                            fontSize(10f)
                                            color(Color(0xFFE6A23C))
                                        }
                                    }
                                }
                            }
                        }
                        Text {
                            attr {
                                text(quote.symbol)
                                fontSize(12f)
                                color(StockColors.TEXT_SUB)
                                marginTop(4f)
                            }
                        }
                    }
                    // 中：最新价
                    View {
                        attr {
                            width(110f)
                            alignItemsFlexEnd()
                            justifyContentCenter()
                        }
                        Text {
                            attr {
                                text(StockFormat.price(quote.price))
                                fontSize(17f)
                                fontWeightBold()
                                color(trendColor)
                                textAlignRight()
                            }
                        }
                    }
                    // 右：涨跌徽标
                    View {
                        attr {
                            width(92f)
                            alignItemsFlexEnd()
                            justifyContentCenter()
                        }
                        View {
                            attr {
                                paddingTop(4f)
                                paddingBottom(4f)
                                paddingLeft(8f)
                                paddingRight(8f)
                                borderRadius(4f)
                                backgroundColor(trendColor)
                            }
                            Text {
                                attr {
                                    text(
                                        "${StockFormat.change(quote.change)}  ${StockFormat.percent(quote.changePercent)}"
                                    )
                                    fontSize(12f)
                                    color(Color.WHITE)
                                    textAlignCenter()
                                }
                            }
                        }
                    }
                    event {
                        click {
                            val st = ctx.swipeStates[quote.code]
                            val now = ctx.bridgeModule.currentTimeStamp()
                            if (st != null && st.offset < 0f) {
                                st.offset = 0f // 已左滑展开 → 点击收回
                            } else if (st == null || now - st.lastGestureEndTime > ctx.swipeClickGuardMs) {
                                ctx.openDetail(quote) // 刚结束滑动手势时不响应点击，避免误进详情
                            }
                        }
                        // 低层触摸事件实现左滑检测：
                        // 不注册 pan，避免 Android 上 DOWN 即 requestDisallowInterceptTouchEvent(true)
                        // 导致父级列表无法拦截垂直滚动；垂直手势由列表接管，水平手势放行到这里
                        touchDown { ctx.onTouchSwipe(quote, it, "down") }
                        touchMove { ctx.onTouchSwipe(quote, it, "move") }
                        touchUp { ctx.onTouchSwipe(quote, it, "up") }
                        touchCancel { ctx.onTouchSwipe(quote, it, "cancel") }
                    }
                }
                // 分隔线
                View {
                    attr {
                        height(1f)
                        backgroundColor(Color(0xFFEBEBEB))
                    }
                }
            }
        }
    }

    /** 当前行的左移距离（attr 响应式读取）：展开时整体左移露出操作条，否则归位 */
    private fun swipeOffsetOf(code: String): Float = swipeStates[code]?.offset ?: 0f

    /**
     * 左滑手势（基于低层触摸事件）：检测到水平滑动动作后整体呼出/收回，带动画过渡。
     * - down：记录起点，并收起其它已展开的行
     * - move：仅做方向判定——水平手势（左滑展开 / 右滑收回）整体切换；
     *         垂直手势完全忽略、不位移任何内容，滚动交给列表容器
     * - up / cancel：复位手势状态；cancel（列表拦截滚动）同样复位
     */
    private fun onTouchSwipe(quote: StockQuote, params: TouchParams, phase: String) {
        val st = swipeStates.getOrPut(quote.code) { SwipeState() }
        when (phase) {
            "down" -> {
                st.startX = params.x
                st.startY = params.y
                st.gestureHandled = false
                swipeStates.forEach { (code, s) ->
                    if (code != quote.code && s.offset < 0f) s.offset = 0f
                }
            }
            "move" -> {
                if (st.gestureHandled) return
                val dx = params.x - st.startX
                val dy = params.y - st.startY
                if (abs(dx) < swipeSlop && abs(dy) < swipeSlop) return // 未超阈值：不响应、不位移
                st.gestureHandled = true
                if (abs(dx) > abs(dy)) {
                    // 水平手势：左滑整体呼出，右滑整体收回
                    if (dx < 0f) {
                        swipeStates.forEach { (code, s) ->
                            if (code != quote.code && s.offset < 0f) s.offset = 0f
                        }
                        st.offset = -actionWidth
                    } else {
                        st.offset = 0f
                    }
                }
                // 垂直手势：放弃处理，让列表正常滚动
            }
            "up", "cancel" -> {
                st.gestureHandled = false
                st.lastGestureEndTime = bridgeModule.currentTimeStamp()
            }
        }
    }

    /** 左滑操作条：置顶 / 取消置顶 */
    private fun onSwipePin(quote: StockQuote) {
        val willPin = !Watchlist.isPinned(sp, quote.code)
        Watchlist.pin(sp, quote.code, willPin)
        bridgeModule.toast(if (willPin) "已置顶 ${quote.name}" else "已取消置顶 ${quote.name}")
        swipeStates[quote.code]?.offset = 0f
        resortQuotes()
    }

    /** 左滑操作条：删除（弹出确认框） */
    private fun onSwipeDelete(quote: StockQuote) {
        swipeStates[quote.code]?.offset = 0f
        pendingRemove = quote
    }

    /** 跳转个股详情 */
    private fun openDetail(quote: StockQuote) {
        val pageData = JSONObject().apply {
            put("code", quote.code)
            put("name", quote.name)
        }
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("stock_detail", pageData)
    }
}
