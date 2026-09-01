package com.example.aistock_prices.stock.ai

import com.example.aistock_prices.stock.data.MinutePoint
import com.example.aistock_prices.stock.data.StockQuote
import com.example.aistock_prices.stock.data.StockRepository
import com.example.aistock_prices.stock.ui.StockColors
import com.example.aistock_prices.stock.ui.StockFormat
import com.example.kuiklychart.chart.base.ChartDataPoint
import com.example.kuiklychart.chart.base.ChartDataSet
import com.example.kuiklychart.chart.line.LineChart
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.KuiklyMarkdown
import com.tencent.kuiklybase.config.MarkdownConfig

/**
 * AI 问答视图（首页「AI 问答」内联 Tab 与 ai_chat 独立页共用）。
 *
 * 能力：
 * - 多会话：切换 / 新建 / 删除（删除弹确认框），会话跨启动持久化（SP）
 * - AI 回复：整体返回 + 加载动画；Markdown 渲染 + ```stock 标记的实时行情卡片 + 迷你分时图
 * - 未配置 API：引导卡片 + 「去设置」跳转
 * - 股票卡片点击进个股详情；含股票标记的消息底部提供「查看完整分析」进结果详情页
 */
internal class AiChatView : ComposeView<AiChatViewAttr, AiChatViewEvent>() {

    private var conversations by observableList<Conversation>()
    /** 当前会话的消息（vfor 需要 ObservableList） */
    private var activeMsgs by observableList<ChatMessage>()
    private var activeId by observable("")
    private var showConvPanel by observable(false)
    private var inputText by observable("")
    private var sending by observable(false)
    private var pendingDeleteConv by observable<Conversation?>(null)
    /** 股票卡片行情/分时就绪后自增，触发卡片重绘 */
    private var quoteTick by observable(0)

    private var chatInputRef: ViewRef<InputView>? = null
    /** code -> 行情（null=尚未加载成功） */
    private val cardQuotes = mutableMapOf<String, StockQuote?>()
    /** code -> 分时数据 */
    private val cardMinutes = mutableMapOf<String, List<MinutePoint>>()
    private val fetching = mutableSetOf<String>()

    override fun createAttr(): AiChatViewAttr = AiChatViewAttr()

    override fun createEvent(): AiChatViewEvent = AiChatViewEvent()

    private val sp: SharedPreferencesModule
        get() = getPager().acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)

    private val network: NetworkModule
        get() = getPager().acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)

    private fun activeConv(): Conversation? = conversations.firstOrNull { it.id == activeId }

    private fun activeMessages(): List<ChatMessage> = activeConv()?.messages.orEmpty()

    /** 同步 activeMsgs（每次会话/消息变化后调用） */
    private fun syncActiveMsgs() {
        activeMsgs.clear()
        activeMsgs.addAll(activeMessages())
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        reloadConversations()
        val saved = sp.getItem(KEY_ACTIVE_ID)
        if (saved.isNotBlank() && conversations.any { it.id == saved }) activeId = saved
        if (activeId.isBlank() && conversations.isNotEmpty()) activeId = conversations.first().id
    }

    /** 外部（ai_chat 独立页）初始化：切换到指定股票会话；autoSend 时自动发问 */
    fun initForStock(code: String, name: String, autoSend: Boolean) {
        val conv = ConversationStore.findOrCreateForStock(sp, code, name)
        activeId = conv.id
        sp.setItem(KEY_ACTIVE_ID, conv.id)
        reloadConversations()
        if (autoSend && !sending) {
            val last = conv.messages.lastOrNull()
            val alreadyAutoAsked = last?.role == "user" && last.content.startsWith("帮我分析")
            if (!alreadyAutoAsked) {
                // 延迟到视图树稳定后再发问
                setTimeout(pagerId, 150) { doSend("帮我分析${name}（$code）这只股票") }
            }
        }
    }

    private fun isConfigured(): Boolean = AiAnalysisService.loadConfig(sp).isConfigured

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                flex(1f)
                backgroundColor(StockColors.BG_PAGE)
            }
            // ---------- 会话栏 ----------
            ctx.convHeader().invoke(this)
            // ---------- 消息区 / 未配置引导 ----------
            vif({ ctx.isConfigured() }) {
                ctx.messageList().invoke(this)
            }
            velse {
                ctx.guideCard().invoke(this)
            }
            // ---------- 输入区 ----------
            vif({ ctx.isConfigured() }) {
                ctx.inputBar().invoke(this)
            }
            // ---------- 会话面板（下拉覆盖层） ----------
            vif({ ctx.showConvPanel }) {
                ctx.convPanel().invoke(this)
            }
            // ---------- 删除会话确认弹窗 ----------
            vif({ ctx.pendingDeleteConv != null }) {
                ctx.deleteModal().invoke(this)
            }
        }
    }

    // ==================== 会话栏 ====================

    private fun convHeader(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    height(48f)
                    paddingLeft(12f)
                    paddingRight(12f)
                    backgroundColor(Color.WHITE)
                    border(Border(0.5f, BorderStyle.SOLID, Color(0xFFE4E4E4)))
                }
                // 会话切换
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingTop(8f)
                        paddingBottom(8f)
                        paddingLeft(10f)
                        paddingRight(10f)
                        borderRadius(6f)
                        backgroundColor(Color(0xFFF5F6F8))
                    }
                    Text {
                        attr {
                            text("☰ ")
                            fontSize(15f)
                            color(StockColors.ACCENT)
                        }
                    }
                    Text {
                        attr {
                            text(ctx.activeConv()?.displayName ?: "选择会话")
                            fontSize(14f)
                            fontWeightSemiBold()
                            color(StockColors.TEXT_MAIN)
                        }
                    }
                    event {
                        click { ctx.showConvPanel = !ctx.showConvPanel }
                    }
                }
                View {
                    attr {
                        flex(1f)
                    }
                }
                // 新建会话
                View {
                    attr {
                        paddingTop(8f)
                        paddingBottom(8f)
                        paddingLeft(12f)
                        paddingRight(12f)
                        borderRadius(6f)
                        backgroundColor(Color(0xFFF0F5FF))
                    }
                    Text {
                        attr {
                            text("＋ 新建")
                            fontSize(13f)
                            color(StockColors.ACCENT)
                            fontWeightSemiBold()
                        }
                    }
                    event {
                        click { ctx.onCreateConversation() }
                    }
                }
            }
        }
    }

    // ==================== 会话面板（左侧抽屉） ====================

    private fun convPanel(): ViewBuilder {
        val ctx = this
        val drawerWidth = 280f
        return {
            // 全屏遮罩（zIndex 置顶，避免被消息区/输入区覆盖）
            View {
                attr {
                    absolutePosition(top = 48f, left = 0f, right = 0f, bottom = 0f)
                    zIndex(100)
                    backgroundColor(Color(0x33000000))
                }
                event {
                    click { ctx.showConvPanel = false }
                }
                // 左侧抽屉面板
                View {
                    attr {
                        absolutePosition(top = 0f, left = 0f, bottom = 0f)
                        width(drawerWidth)
                        backgroundColor(Color.WHITE)
                        flexDirectionColumn()
                        zIndex(101)
                    }
                    event {
                        click { }
                    }
                    // 抽屉头部：标题 + 新建
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            padding(14f)
                            paddingLeft(16f)
                            paddingRight(16f)
                            border(Border(0.5f, BorderStyle.SOLID, Color(0xFFE4E4E4)))
                        }
                        Text {
                            attr {
                                flex(1f)
                                text("会话列表")
                                fontSize(15f)
                                fontWeightSemiBold()
                                color(StockColors.TEXT_MAIN)
                            }
                        }
                        View {
                            attr {
                                paddingTop(6f)
                                paddingBottom(6f)
                                paddingLeft(12f)
                                paddingRight(12f)
                                borderRadius(6f)
                                backgroundColor(Color(0xFFF0F5FF))
                            }
                            Text {
                                attr {
                                    text("＋ 新建")
                                    fontSize(13f)
                                    color(StockColors.ACCENT)
                                    fontWeightSemiBold()
                                }
                            }
                            event {
                                click { ctx.onCreateConversation() }
                            }
                        }
                    }
                    Scroller {
                        attr {
                            flex(1f)
                            showScrollerIndicator(false)
                        }
                        vfor({ ctx.conversations }) { conv ->
                            View {
                                attr {
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    padding(12f)
                                    marginLeft(12f)
                                    marginRight(12f)
                                    marginTop(6f)
                                    borderRadius(8f)
                                    backgroundColor(
                                        if (conv.id == ctx.activeId) Color(0xFFF0F5FF) else Color.WHITE
                                    )
                                }
                                View {
                                    attr {
                                        flex(1f)
                                        flexDirectionColumn()
                                    }
                                    Text {
                                        attr {
                                            text(conv.displayName)
                                            fontSize(14f)
                                            fontWeightSemiBold()
                                            color(StockColors.TEXT_MAIN)
                                        }
                                    }
                                    Text {
                                        attr {
                                            text(
                                                when {
                                                    conv.stockCode != null -> "股票：${conv.stockCode}"
                                                    conv.messages.isEmpty() -> "空对话"
                                                    else -> "共 ${conv.messages.size} 条消息"
                                                }
                                            )
                                            fontSize(11f)
                                            color(StockColors.TEXT_SUB)
                                            marginTop(3f)
                                        }
                                    }
                                    event {
                                        click { ctx.onSelectConversation(conv) }
                                    }
                                }
                                // 删除会话
                                View {
                                    attr {
                                        padding(8f)
                                        marginLeft(6f)
                                    }
                                    Text {
                                        attr {
                                            text("删除")
                                            fontSize(12f)
                                            color(StockColors.UP)
                                        }
                                    }
                                    event {
                                        click { ctx.pendingDeleteConv = conv }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 新建会话（先关面板，再延迟刷新列表，避免 vfor 遍历期间修改列表导致崩溃） */
    private fun onCreateConversation() {
        val conv = ConversationStore.newConversation(sp)
        activeId = conv.id
        sp.setItem(KEY_ACTIVE_ID, conv.id)
        showConvPanel = false
        bridgeToast("已新建对话")
        setTimeout(pagerId, 50) { reloadConversations() }
    }

    /** 切换会话（同样先关面板再刷新） */
    private fun onSelectConversation(conv: Conversation) {
        activeId = conv.id
        sp.setItem(KEY_ACTIVE_ID, conv.id)
        showConvPanel = false
        setTimeout(pagerId, 50) { reloadConversations() }
    }

    // ==================== 消息列表 ====================

    private fun messageList(): ViewBuilder {
        val ctx = this
        return {
            Scroller {
                attr {
                    flex(1f)
                    showScrollerIndicator(false)
                }
                vfor({ ctx.activeMsgs }) { msg ->
                    ctx.messageBubble(msg).invoke(this)
                }
                vif({ ctx.sending }) {
                    ctx.typingBubble().invoke(this)
                }
                View {
                    attr {
                        height(12f)
                    }
                }
            }
        }
    }

    private fun messageBubble(msg: ChatMessage): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flexDirectionColumn()
                    paddingLeft(12f)
                    paddingRight(12f)
                    paddingTop(6f)
                    paddingBottom(6f)
                    alignItems(if (msg.role == "user") FlexAlign.FLEX_END else FlexAlign.FLEX_START)
                }
                if (msg.role == "user") {
                    // 用户气泡：右对齐纯文本
                    View {
                        attr {
                            maxWidth(ctx.pagerData.pageViewWidth - 80f)
                            borderRadius(12f)
                            padding(12f)
                            backgroundColor(StockColors.ACCENT)
                        }
                        Text {
                            attr {
                                text(msg.content)
                                fontSize(14f)
                                color(Color.WHITE)
                                lineHeight(20f)
                            }
                        }
                    }
                } else {
                    View {
                        attr {
                            maxWidth(ctx.pagerData.pageViewWidth - 40f)
                            borderRadius(12f)
                            padding(12f)
                            backgroundColor(Color.WHITE)
                            border(Border(1f, BorderStyle.SOLID, Color(0xFFEBEBEB)))
                            flexDirectionColumn()
                        }
                        vif({ msg.error }) {
                            Text {
                                attr {
                                    text("⚠️ ${msg.content}")
                                    fontSize(13f)
                                    color(Color(0xFFD4380D))
                                    lineHeight(20f)
                                }
                            }
                        }
                        velse {
                            // 内容容器：固定宽度约束，确保 Markdown/长文本换行不超出屏幕
                            View {
                                attr {
                                    width(ctx.pagerData.pageViewWidth - 64f)
                                    flexDirectionColumn()
                                }
                                val segs = parseChatSegments(msg.content)
                                val conv = ctx.activeConv()
                                // 渲染 markdown + stock 标记段
                                ctx.renderSegments(msg.content).invoke(this)
                                // 兜底：AI 未输出 ```stock 标记，但会话关联了股票 → 自动补一张该股票卡片
                                vif({ segs.none { it.type == "stock" } && conv?.stockCode != null }) {
                                    if (conv?.stockCode != null) {
                                        ctx.stockCard(conv.stockCode, conv.stockName ?: "").invoke(this)
                                    }
                                }
                            }
                        }
                        // 含股票标记 → 「查看完整分析」入口（结果详情页）
                        vif({ parseChatSegments(msg.content).any { it.type == "stock" } }) {
                            View {
                                attr {
                                    marginTop(10f)
                                    paddingTop(8f)
                                    border(Border(0.5f, BorderStyle.SOLID, Color(0xFFE4E4E4)))
                                }
                                Text {
                                    attr {
                                        text("查看完整分析 →")
                                        fontSize(12f)
                                        color(StockColors.ACCENT)
                                    }
                                }
                                event {
                                    click { ctx.event.onOpenResult?.invoke(ctx.activeId, msg.ts) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 加载中气泡 */
    private fun typingBubble(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    paddingLeft(12f)
                    paddingRight(12f)
                    paddingTop(6f)
                    paddingBottom(6f)
                    alignItems(FlexAlign.FLEX_START)
                }
                View {
                    attr {
                        borderRadius(12f)
                        padding(12f)
                        backgroundColor(Color.WHITE)
                        border(Border(1f, BorderStyle.SOLID, Color(0xFFEBEBEB)))
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    ActivityIndicator {
                        attr {
                            isGrayStyle(false)
                        }
                    }
                    Text {
                        attr {
                            text("  AI 思考中...")
                            fontSize(13f)
                            color(StockColors.TEXT_SUB)
                        }
                    }
                }
            }
        }
    }

    /** 组合渲染：markdown 段 -> KuiklyMarkdown；stock 段 -> 行情卡片 */
    private fun renderSegments(content: String): ViewBuilder {
        val ctx = this
        return {
            parseChatSegments(content).forEach { seg ->
                when (seg.type) {
                    "stock" -> ctx.stockCard(seg.code, seg.name).invoke(this)
                    else -> KuiklyMarkdown(content = seg.text, config = MarkdownConfig.Default)
                }
            }
        }
    }

    // ==================== 股票卡片（实时行情 + 迷你分时图） ====================

    private fun stockCard(code: String, name: String): ViewBuilder {
        val ctx = this
        return {
            ctx.ensureCardData(code, name)
            View {
                attr {
                    marginTop(8f)
                    marginBottom(4f)
                    borderRadius(10f)
                    padding(10f)
                    backgroundColor(Color(0xFFF8F9FB))
                    border(Border(1f, BorderStyle.SOLID, Color(0xFFE4E4E4)))
                    flexDirectionColumn()
                }
                // 头部：名称 + 代码 + 进入详情
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    Text {
                        attr {
                            flex(1f)
                            text(if (name.isNotBlank()) name else code)
                            fontSize(14f)
                            fontWeightSemiBold()
                            color(StockColors.TEXT_MAIN)
                        }
                    }
                    Text {
                        attr {
                            text(code)
                            fontSize(11f)
                            color(StockColors.TEXT_SUB)
                        }
                    }
                }
                // 行情区
                val q = ctx.cardQuotes[code]
                ctx.quoteTick // 建立响应式依赖
                vif({ q != null }) {
                    val qq = q!!
                    val c = StockColors.ofChange(qq.change)
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsFlexEnd()
                            marginTop(6f)
                        }
                        Text {
                            attr {
                                text(StockFormat.price(qq.price))
                                fontSize(22f)
                                fontWeightBold()
                                color(c)
                            }
                        }
                        Text {
                            attr {
                                text("  ${StockFormat.change(qq.change)}  ${StockFormat.percent(qq.changePercent)}")
                                fontSize(13f)
                                color(c)
                                marginBottom(3f)
                            }
                        }
                    }
                    // 迷你分时图
                    vif({ ctx.cardMinutes[code]?.size ?: 0 > 1 }) {
                        val points = ctx.cardMinutes[code]!!
                        val width = ctx.pagerData.pageViewWidth - 96f
                        LineChart {
                            attr {
                                width(width)
                                height(56f)
                                marginTop(6f)
                                dataSets = listOf(
                                    ChartDataSet(
                                        points = points.map { ChartDataPoint(it.price.toFloat()) },
                                        label = "分时",
                                        color = StockColors.ofChange(
                                            (qq.price) - (ctx.quotePrevClose(code, points))
                                        )
                                    )
                                )
                                xAxis {
                                    labels = emptyList()
                                    showGridLines = false
                                }
                                yAxis {
                                    min = ctx.minuteRangeOf(points, ctx.quotePrevClose(code, points)).first
                                    max = ctx.minuteRangeOf(points, ctx.quotePrevClose(code, points)).second
                                    showGridLines = false
                                }
                                smooth = true
                                lineWidth = 1.4f
                                showDots = false
                                fillArea = true
                            }
                        }
                    }
                }
                velse {
                    View {
                        attr {
                            marginTop(8f)
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        ActivityIndicator {
                            attr {
                                isGrayStyle(true)
                            }
                        }
                        Text {
                            attr {
                                text(" 行情加载中...")
                                fontSize(12f)
                                color(StockColors.TEXT_SUB)
                            }
                        }
                    }
                }
                event {
                    click { ctx.openStockDetail(code, if (name.isNotBlank()) name else code) }
                }
            }
        }
    }

    private fun quotePrevClose(code: String, points: List<MinutePoint>): Double =
        cardQuotes[code]?.prevClose ?: points.firstOrNull()?.price ?: 0.0

    private fun minuteRangeOf(points: List<MinutePoint>, prevClose: Double): Pair<Float, Float> {
        var minV = points.minOfOrNull { it.price } ?: prevClose
        var maxV = points.maxOfOrNull { it.price } ?: prevClose
        minV = minOf(minV, prevClose)
        maxV = maxOf(maxV, prevClose)
        if (maxV <= minV) {
            minV -= 0.1
            maxV += 0.1
        }
        val pad = (maxV - minV) * 0.08f
        return Pair((minV - pad).toFloat(), (maxV + pad).toFloat())
    }

    /** 拉取卡片行情与分时（每个 code 仅拉一次） */
    private fun ensureCardData(code: String, name: String) {
        if (code.isBlank() || code in fetching) return
        fetching.add(code)
        StockRepository.fetchQuotes(network, listOf(code)) { list ->
            cardQuotes[code] = list.firstOrNull()
            quoteTick++
            fetching.remove(code)
        }
        StockRepository.fetchMinute(network, code) { points ->
            if (points.isNotEmpty()) {
                cardMinutes[code] = points
                quoteTick++
            }
        }
    }

    private fun openStockDetail(code: String, name: String) {
        val pageData = JSONObject().apply {
            put("code", code)
            put("name", name)
        }
        getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("stock_detail", pageData)
    }

    // ==================== 输入区 ====================

    private fun inputBar(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingTop(8f)
                    paddingBottom(8f)
                    paddingLeft(12f)
                    paddingRight(12f)
                    backgroundColor(Color.WHITE)
                    border(Border(0.5f, BorderStyle.SOLID, Color(0xFFE4E4E4)))
                }
                View {
                    attr {
                        flex(1f)
                        height(40f)
                        borderRadius(20f)
                        backgroundColor(Color(0xFFF5F6F8))
                        paddingLeft(14f)
                        paddingRight(14f)
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    Input {
                        ref { ctx.chatInputRef = it }
                        attr {
                            flex(1f)
                            height(36f)
                            fontSize(14f)
                            color(StockColors.TEXT_MAIN)
                            placeholder("问问 AI 关于股票的问题...")
                            placeholderColor(StockColors.TEXT_SUB)
                            maxTextLength(500)
                        }
                        event {
                            textDidChange { ctx.inputText = it.text }
                        }
                    }
                }
                View {
                    attr {
                        marginLeft(10f)
                        width(58f)
                        height(40f)
                        borderRadius(20f)
                        allCenter()
                        backgroundColor(StockColors.ACCENT)
                    }
                    Text {
                        attr {
                            text("发送")
                            fontSize(14f)
                            color(Color.WHITE)
                            fontWeightSemiBold()
                        }
                    }
                    event {
                        click { ctx.send() }
                    }
                }
            }
        }
    }

    // ==================== 未配置引导 ====================

    private fun guideCard(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flex(1f)
                    allCenter()
                    padding(24f)
                }
                View {
                    attr {
                        width(ctx.pagerData.pageViewWidth - 60f)
                        borderRadius(12f)
                        backgroundColor(Color.WHITE)
                        padding(20f)
                        flexDirectionColumn()
                    }
                    Text {
                        attr {
                            text("AI 问答")
                            fontSize(17f)
                            fontWeightBold()
                            color(StockColors.TEXT_MAIN)
                            marginBottom(8f)
                        }
                    }
                    Text {
                        attr {
                            text("尚未配置 AI 服务。配置后可以在这里与 AI 多轮讨论股票、指数，回复支持 Markdown、实时行情卡片与迷你走势图。")
                            fontSize(13f)
                            color(StockColors.TEXT_SUB)
                            lineHeight(20f)
                        }
                    }
                    View {
                        attr {
                            marginTop(16f)
                            height(40f)
                            borderRadius(20f)
                            allCenter()
                            backgroundColor(StockColors.ACCENT)
                        }
                        Text {
                            attr {
                                text("去设置")
                                fontSize(15f)
                                color(Color.WHITE)
                                fontWeightSemiBold()
                            }
                        }
                        event {
                            click { ctx.openAiConfig() }
                        }
                    }
                }
            }
        }
    }

    private fun openAiConfig() {
        getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("ai_config", JSONObject())
    }

    // ==================== 删除确认弹窗 ====================

    private fun deleteModal(): ViewBuilder {
        val ctx = this
        return {
            Modal {
                View {
                    attr {
                        flex(1f)
                        allCenter()
                        backgroundColor(Color(0x66000000))
                    }
                    event {
                        click { ctx.pendingDeleteConv = null }
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
                                text("删除对话")
                                fontSize(16f)
                                fontWeightSemiBold()
                                color(StockColors.TEXT_MAIN)
                                marginBottom(10f)
                            }
                        }
                        Text {
                            attr {
                                text("确定删除对话「${ctx.pendingDeleteConv?.displayName}」吗？删除后不可恢复。")
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
                                    click { ctx.pendingDeleteConv = null }
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
                                        ctx.pendingDeleteConv?.let { conv ->
                                            ConversationStore.delete(ctx.sp, conv.id)
                                            if (ctx.activeId == conv.id) {
                                                ctx.activeId = ""
                                                val rest = ConversationStore.load(ctx.sp)
                                                if (rest.isNotEmpty()) {
                                                    ctx.activeId = rest.first().id
                                                    ctx.sp.setItem(KEY_ACTIVE_ID, ctx.activeId)
                                                } else {
                                                    ctx.sp.setItem(KEY_ACTIVE_ID, "")
                                                }
                                            }
                                            ctx.bridgeToast("已删除对话")
                                            // 先关弹窗再延迟刷新，避免列表重绘竞争
                                            setTimeout(pagerId, 50) { ctx.reloadConversations() }
                                        }
                                        ctx.pendingDeleteConv = null
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== 发送逻辑 ====================

    private fun send() {
        val text = inputText.trim()
        if (text.isEmpty()) {
            bridgeToast("请输入内容")
            return
        }
        doSend(text)
    }

    private fun doSend(text: String) {
        if (sending) return
        val conv = activeConv() ?: return
        val config = AiAnalysisService.loadConfig(sp)
        if (!config.isConfigured) {
            bridgeToast("请先配置 AI 服务")
            return
        }
        sending = true
        chatInputRef?.view?.setText("")
        inputText = ""
        // 追加 user 消息并持久化
        val updated = conv.copy(
            messages = conv.messages + ChatMessage("user", text, System.currentTimeMillis())
        )
        ConversationStore.update(sp, updated)
        reloadConversations()
        AiAnalysisService.chat(network, config, updated.messages) { content, err ->
            sending = false
            val nowConv = ConversationStore.load(sp).firstOrNull { it.id == conv.id } ?: return@chat
            val reply = if (err != null) {
                ChatMessage("assistant", err, System.currentTimeMillis(), error = true)
            } else {
                ChatMessage("assistant", content, System.currentTimeMillis())
            }
            ConversationStore.update(sp, nowConv.copy(messages = nowConv.messages + reply))
            reloadConversations()
        }
    }

    private fun reloadConversations() {
        conversations.clear()
        conversations.addAll(ConversationStore.load(sp))
        syncActiveMsgs()
    }

    private fun bridgeToast(msg: String) {
        val bridge = getPager().acquireModule<com.example.aistock_prices.base.BridgeModule>(
            com.example.aistock_prices.base.BridgeModule.MODULE_NAME
        )
        bridge.toast(msg)
    }

    companion object {
        private const val KEY_ACTIVE_ID = "ai_chat_active_id"
    }
}

internal class AiChatViewAttr : ComposeAttr()

internal class AiChatViewEvent : ComposeEvent() {
    /** 点击「查看完整分析」进入结果详情页（参数：会话 id + 消息时间戳，用于定位消息） */
    var onOpenResult: ((String, Long) -> Unit)? = null
}

internal fun ViewContainer<*, *>.AiChatView(init: AiChatView.() -> Unit) {
    addChild(AiChatView(), init)
}
