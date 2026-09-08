package com.example.aistock_prices.stock

import com.example.aistock_prices.base.BasePager
import com.example.aistock_prices.base.bridgeModule
import com.example.aistock_prices.base.setTimeout
import com.example.aistock_prices.stock.ai.AiChatView
import com.example.aistock_prices.stock.ai.AiConfigView
import com.example.aistock_prices.stock.data.StockCache
import com.example.aistock_prices.stock.data.StockMeta
import com.example.aistock_prices.stock.data.StockQuote
import com.example.aistock_prices.stock.data.StockRepository
import com.example.aistock_prices.stock.data.Watchlist
import com.example.aistock_prices.stock.ui.StockFormat
import com.example.aistock_prices.stock.ui.ThemeMode
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
    /** 顶栏「三条横杠」下拉菜单 */
    private var showMenu by observable(false)
    /** ☰ 菜单「夜间模式」当前档位（跟随系统/深色/浅色），初始来自宿主注入的 pageData themeMode */
    private var menuMode by observable(ThemeMode.AUTO)
    private var pendingRemove by observable<StockQuote?>(null)
    private var addInput by observable("")
    private var addInputRef: ViewRef<InputView>? = null
    private var refreshRef: ViewRef<RefreshView>? = null
    private var pollTimerRef = ""
    /** AI 问答 Tab 的内联视图引用（设置页返回时刷新配置态） */
    private var chatView: AiChatView? = null

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
                backgroundColor(ctx.pal.bgPage)
            }
            // ---------- 顶栏 ----------
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingTop(pagerData.statusBarHeight)
                    height(56f + pagerData.statusBarHeight)
                    backgroundColor(ctx.pal.card)
                    paddingLeft(16f)
                    paddingRight(16f)
                }
                Text {
                    attr {
                        flex(1f)
                        text(if (ctx.currentTab == 0) "自选股" else "AI 问答")
                        fontSize(18f)
                        fontWeightBold()
                        color(ctx.pal.textMain)
                    }
                }
                vif({ ctx.currentTab == 0 }) {
                    Text {
                        attr {
                            text("刷新")
                            fontSize(14f)
                            color(ctx.pal.accent)
                            marginRight(16f)
                        }
                        event {
                            click { ctx.onRefreshTap() }
                        }
                    }
                }
                // 三条横杠：下拉菜单（添加自选 / AI 设置）
                // 注：Text 不支持 padding，包一层 View 容器承载点击区域
                View {
                    attr {
                        paddingTop(6f)
                        paddingBottom(6f)
                        paddingLeft(8f)
                        paddingRight(4f)
                    }
                    Text {
                        attr {
                            text("☰")
                            fontSize(20f)
                            color(ctx.pal.textMain)
                        }
                    }
                    event {
                        click { ctx.showMenu = !ctx.showMenu }
                    }
                }
            }

            // ---------- 顶栏下拉菜单 ----------
            // 注：菜单需 zIndex 置顶，否则会被后面声明的列表/底栏内容覆盖（绘制顺序在后的在上层）
            vif({ ctx.showMenu }) {
                View {
                    attr {
                        absolutePosition(
                            top = 56f + pagerData.statusBarHeight,
                            left = 0f,
                            right = 0f,
                            bottom = 0f
                        )
                        zIndex(100)
                        backgroundColor(ctx.pal.maskDim)
                    }
                    event {
                        click { ctx.showMenu = false }
                    }
                    View {
                        attr {
                            absolutePosition(top = 0f, left = 0f, right = 0f)
                            backgroundColor(ctx.pal.card)
                            paddingTop(6f)
                            paddingBottom(6f)
                        }
                        event {
                            click { }
                        }
                        // 添加自选
                        View {
                            attr {
                                padding(14f)
                                paddingLeft(16f)
                                paddingRight(16f)
                            }
                            Text {
                                attr {
                                    text("＋ 添加自选股")
                                    fontSize(15f)
                                    color(ctx.pal.textMain)
                                }
                            }
                            event {
                                click {
                                    ctx.showMenu = false
                                    ctx.addInput = ""
                                    ctx.addInputRef?.view?.setText("")
                                    ctx.showAddDialog = true
                                }
                            }
                        }
                        View {
                            attr {
                                height(1f)
                                marginLeft(16f)
                                marginRight(16f)
                                backgroundColor(ctx.pal.chip2Bg)
                            }
                        }
                        // AI 设置
                        View {
                            attr {
                                padding(14f)
                                paddingLeft(16f)
                                paddingRight(16f)
                            }
                            Text {
                                attr {
                                    text("⚙ AI 设置")
                                    fontSize(15f)
                                    color(ctx.pal.textMain)
                                }
                            }
                            event {
                                click {
                                    ctx.showMenu = false
                                    ctx.openAiConfig()
                                }
                            }
                        }
                        View {
                            attr {
                                height(1f)
                                marginLeft(16f)
                                marginRight(16f)
                                backgroundColor(ctx.pal.chip2Bg)
                            }
                        }
                        // 夜间模式（三态循环：跟随系统 -> 深色 -> 浅色 -> 跟随系统）
                        View {
                            attr {
                                padding(14f)
                                paddingLeft(16f)
                                paddingRight(16f)
                                flexDirectionRow()
                                alignItemsCenter()
                            }
                            Text {
                                attr {
                                    // 图标表示"点击后将切换到的模式"：当前夜间观感 → 显示太阳（将切到日间）；日间观感 → 月亮
                                    text(if (ctx.isNightMode()) "☀️" else "🌙")
                                    fontSize(15f)
                                    marginRight(8f)
                                }
                            }
                            Text {
                                attr {
                                    flex(1f)
                                    // 主文案与图标同语义：当前夜间观感显示「日间模式」（点按切到日间），反之亦然
                                    text(if (ctx.isNightMode()) "日间模式" else "夜间模式")
                                    fontSize(15f)
                                    color(ctx.pal.textMain)
                                }
                            }
                            Text {
                                attr {
                                    text(ThemeMode.label(ctx.menuMode))
                                    fontSize(12f)
                                    color(ctx.pal.textSub)
                                }
                            }
                            event {
                                click { ctx.cycleThemeMode() }
                            }
                        }
                    }
                }
            }

            // ---------- 内容区：自选股 Tab ----------
            vif({ ctx.currentTab == 0 }) {
                ctx.listContent().invoke(this)
            }

            // ---------- 内容区：AI 问答 Tab（内联，切 Tab 对话不丢） ----------
            vif({ ctx.currentTab == 1 }) {
                AiChatView {
                    ctx.chatView = this
                    event {
                        onOpenResult = { convId, msgTs ->
                            val pageData = JSONObject().apply {
                                put("convId", convId)
                                put("msgTs", msgTs)
                            }
                            ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
                                .openPage("result_detail", pageData)
                        }
                    }
                }
            }

            // ---------- 底部任务栏 ----------
            View {
                attr {
                    flexDirectionColumn()
                    backgroundColor(ctx.pal.card)
                    border(Border(0.5f, BorderStyle.SOLID, ctx.pal.divider))
                }
                View {
                    attr {
                        flexDirectionRow()
                        height(54f)
                    }
                    ctx.tabItem("自选股", 0).invoke(this)
                    ctx.tabItem("AI 问答", 1).invoke(this)
                }
            }

            // ---------- 添加自选弹窗 ----------
            vif({ ctx.showAddDialog }) {
                Modal {
                    View {
                        attr {
                            flex(1f)
                            allCenter()
                            backgroundColor(ctx.pal.maskFull)
                        }
                        event {
                            click { ctx.showAddDialog = false }
                        }
                        View {
                            attr {
                                width(ctx.pagerData.pageViewWidth - 60f)
                                borderRadius(12f)
                                backgroundColor(ctx.pal.card)
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
                                    color(ctx.pal.textMain)
                                    marginBottom(14f)
                                }
                            }
                            View {
                                attr {
                                    height(44f)
                                    borderRadius(8f)
                                    backgroundColor(ctx.pal.chipBg)
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
                                        color(ctx.pal.textMain)
                                        placeholder("如 600519 或 sh600519")
                                        placeholderColor(ctx.pal.textSub)
                                        maxTextLength(12)   // 股票代码最长 8 位 + 市场前缀（sh/sz/bj/hk 2 位）
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
                                        backgroundColor(ctx.pal.chip2Bg)
                                        marginRight(12f)
                                    }
                                    Text {
                                        attr {
                                            text("取消")
                                            fontSize(14f)
                                            color(ctx.pal.textSub)
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
                                        backgroundColor(ctx.pal.accent)
                                    }
                                    Text {
                                        attr {
                                            text("添加")
                                            fontSize(14f)
                                            color(ctx.pal.onAccent)
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
                            backgroundColor(ctx.pal.maskFull)
                        }
                        event {
                            click { ctx.pendingRemove = null }
                        }
                        View {
                            attr {
                                width(ctx.pagerData.pageViewWidth - 60f)
                                borderRadius(12f)
                                backgroundColor(ctx.pal.card)
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
                                    color(ctx.pal.textMain)
                                    marginBottom(10f)
                                }
                            }
                            Text {
                                attr {
                                    text("确定将 ${ctx.pendingRemove?.name} 移出自选吗？")
                                    fontSize(14f)
                                    color(ctx.pal.textSub)
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
                                        backgroundColor(ctx.pal.chip2Bg)
                                        marginRight(12f)
                                    }
                                    Text {
                                        attr {
                                            text("取消")
                                            fontSize(14f)
                                            color(ctx.pal.textSub)
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
                                        backgroundColor(ctx.pal.up)
                                    }
                                    Text {
                                        attr {
                                            text("删除")
                                            fontSize(14f)
                                            color(ctx.pal.onAccent)
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
        // 菜单展示当前主题档位（宿主注入 pageData 的 themeMode；recreate 后自动取到最新值）
        menuMode = themeMode
        loadData()
    }

    /**
     * 切换主题模式：菜单行展示的是「点击后将切换到的模式」（当前观感的反相）。
     * 点击直接落地到该模式：跟随系统(AUTO) 首次点击也按观感反相落为显式 深色/浅色，
     * 避免原 AUTO->DARK 在深色系统下观感无变化造成"点了没反应/文案与结果不符"。
     */
    private fun cycleThemeMode() {
        val target = if (isNightMode()) ThemeMode.LIGHT else ThemeMode.DARK
        menuMode = target
        bridgeModule.setThemeMode(target)
    }

    /** 页面每次出现：开始 60 秒轮询刷新；AI 问答 Tab 激活时刷新配置态（从设置页返回） */
    override fun pageDidAppear() {
        super.pageDidAppear()
        startPolling()
        if (currentTab == 1) {
            chatView?.reload()
        }
    }

    override fun pageDidDisappear() {
        super.pageDidDisappear()
        stopPolling()
        // 页面被覆盖/离开前台：若 AI 问答仍在生成则中断（不浪费 token）
        chatView?.cancelIfSending()
    }

    override fun pageWillDestroy() {
        super.pageWillDestroy()
        stopPolling()
        chatView?.cancelIfSending()
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
                            color(ctx.pal.textSub)
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
                                color(ctx.pal.textSub)
                                marginBottom(12f)
                            }
                        }
                        Text {
                            attr {
                                text("点击重试")
                                fontSize(15f)
                                color(ctx.pal.accent)
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
                                    color(ctx.pal.textSub)
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
                                        color(ctx.pal.textSub)
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
                        color(if (ctx.currentTab == index) ctx.pal.accent else ctx.pal.textSub)
                    }
                }
                vif({ ctx.currentTab == index }) {
                    View {
                        attr {
                            width(24f)
                            height(3f)
                            borderRadius(1.5f)
                            backgroundColor(ctx.pal.accent)
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
        val trendColor = ctx.pal.ofChange(quote.change)
        return {
            // 确保 attr 求值前本行 SwipeState 已存在：
            // swipeOffsetOf 里 `swipeStates[code]?.offset` 若 map 无此行会因 ?. 短路读不到
            // offset observable，导致 transform/animation 的依赖未建立、首次左滑不生效
            ctx.ensureSwipeState(quote.code)
            View {
                attr {
                    flexDirectionColumn()
                    height(68f)
                    backgroundColor(ctx.pal.card)
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
                                color(ctx.pal.onAccent)
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
                                color(ctx.pal.onAccent)
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
                        backgroundColor(ctx.pal.card)
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
                                    color(ctx.pal.textMain)
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
                                color(ctx.pal.textSub)
                                marginTop(4f)
                            }
                        }
                    }
                    // 右：价格 + 涨跌徽标（严格双列对齐：价格右对齐到 80f 列线，色块统一 110f 宽，
                    //  间距 10f 占位；价格与色块垂直居中，所有行严格对齐列线不再随文本长度漂移）
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        // 价格列（固定宽 80f，文本右对齐保证列右沿一致）
                        View {
                            attr {
                                width(80f)
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
                        // 固定间距列
                        View {
                            attr {
                                width(10f)
                                height(1f)
                            }
                        }
                        // 涨跌徽标（固定宽 110f，所有行严格同宽）
                        View {
                            attr {
                                width(110f)
                                paddingTop(4f)
                                paddingBottom(4f)
                                borderRadius(4f)
                                backgroundColor(trendColor)
                                alignItemsCenter()
                                justifyContentCenter()
                            }
                            Text {
                                attr {
                                    text(
                                        "${StockFormat.change(quote.change)}  ${StockFormat.percent(quote.changePercent)}"
                                    )
                                    fontSize(12f)
                                    color(ctx.pal.onAccent)
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
                        backgroundColor(ctx.pal.divider)
                    }
                }
            }
        }
    }

    /** 当前行的左移距离（attr 响应式读取）：展开时整体左移露出操作条，否则归位 */
    private fun swipeOffsetOf(code: String): Float = swipeStates[code]?.offset ?: 0f

    /** 确保指定行的 SwipeState 已存在（渲染/手势前调用，保证 observable 依赖可建立） */
    private fun ensureSwipeState(code: String): SwipeState = swipeStates.getOrPut(code) { SwipeState() }

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
                // 仅在本次手势确认为滑动时记录结束时间，供 click 防抖使用。
                // 注意：touchUp 先于 click 回调触发，若无条件更新时间戳会把正常点击
                // 全部拦截（now - lastGestureEndTime < 300ms），导致无法进入详情
                val wasGesture = st.gestureHandled
                st.gestureHandled = false
                if (wasGesture) {
                    st.lastGestureEndTime = bridgeModule.currentTimeStamp()
                }
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

    /** 打开 AI 服务设置页 */
    private fun openAiConfig() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("ai_config", JSONObject())
    }
}
