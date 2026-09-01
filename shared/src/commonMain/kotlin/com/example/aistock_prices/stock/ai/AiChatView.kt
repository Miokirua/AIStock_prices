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
import com.tencent.kuikly.core.layout.FlexJustifyContent
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
    /**
     * 生成请求序号：每次发请求 +1，callback 捕获当时的 seq，返回时与最新 chatSeq 比对，
     * 不一致则丢弃（被中断或被新一轮请求替代）。比布尔 cancelled 更安全：
     * 停止后立刻发新消息时，旧请求先返回也不会被误当成新请求结果。
     */
    private var chatSeq = 0
    private var pendingDeleteConv by observable<Conversation?>(null)
    /** 当前打开 ⋮ 菜单的消息 ts（null=无菜单） */
    private var pendingMenuMsgTs by observable<Long?>(null)
    /** 正在"修改"模式中的用户消息 ts（null=正常输入） */
    private var editingUserMsgTs by observable<Long?>(null)
    /** 股票卡片行情/分时就绪后自增，触发卡片重绘 */
    private var quoteTick by observable(0)
    /** 外部刷新信号（如从设置页返回）：自增触发 body 重跑，重新求值 isConfigured() 等 SP 依赖 */
    private var refreshTick by observable(0)

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

    /**
     * 外部刷新入口（页面重新出现时调用）：
     * 重新加载会话并触发 body 重跑，使 SP 依赖（如 isConfigured）重新求值。
     */
    fun reload() {
        refreshTick++
        reloadConversations()
    }

    private fun isConfigured(): Boolean = AiAnalysisService.loadConfig(sp).isConfigured

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                flex(1f)
                backgroundColor(StockColors.BG_PAGE)
            }
            ctx.refreshTick // 建立响应式依赖：外部 reload() 后整体重跑（重新读取 SP 配置等）
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
            // ---------- 修改模式提示条（用户消息点"修改"后显示） ----------
            vif({ ctx.editingUserMsgTs != null }) {
                ctx.editBanner().invoke(this)
            }
            // ---------- 会话面板（下拉覆盖层） ----------
            vif({ ctx.showConvPanel }) {
                ctx.convPanel().invoke(this)
            }
            // ---------- 删除会话确认弹窗 ----------
            vif({ ctx.pendingDeleteConv != null }) {
                ctx.deleteModal().invoke(this)
            }
            // ---------- 消息操作菜单（删除 / 重新生成 / 修改） ----------
            vif({ ctx.pendingMenuMsgTs != null }) {
                ctx.msgMenu().invoke(this)
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
                    // 注：Kuikly 的 maxWidth 在 flex 容器内对 Text 不可靠（Text 按内容测量撑开），
                    // 需用固定 width 强制换行，避免长消息溢出屏幕。
                    View {
                        attr {
                            width(ctx.pagerData.pageViewWidth - 80f)
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
                // ⋮ 菜单触发器：贴气泡下方，对齐方向与气泡一致
                View {
                    attr {
                        flexDirectionRow()
                        justifyContent(if (msg.role == "user") FlexJustifyContent.FLEX_END else FlexJustifyContent.FLEX_START)
                        marginTop(2f)
                        paddingLeft(if (msg.role == "user") 0f else 4f)
                        paddingRight(if (msg.role == "user") 4f else 0f)
                    }
                    View {
                        attr {
                            paddingLeft(8f)
                            paddingRight(8f)
                            paddingTop(3f)
                            paddingBottom(3f)
                        }
                        Text {
                            attr {
                                text("⋮")
                                fontSize(15f)
                                color(StockColors.TEXT_SUB)
                            }
                        }
                        event {
                            click { ctx.pendingMenuMsgTs = msg.ts }
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
                            alignItemsCenter()
                            marginTop(6f)
                        }
                        // 价格：大字号加粗，固定宽度 + 右侧留白，杜绝加粗字溢出盖住涨跌幅
                        Text {
                            attr {
                                width(120f)
                                text(StockFormat.price(qq.price))
                                fontSize(22f)
                                fontWeightBold()
                                color(c)
                            }
                        }
                        View {
                            attr {
                                flex(1f)
                                marginLeft(10f)
                                flexDirectionRow()
                                alignItemsFlexEnd()
                                paddingBottom(3f)
                            }
                            Text {
                                attr {
                                    flex(1f)
                                    text("${StockFormat.change(qq.change)}  ${StockFormat.percent(qq.changePercent)}")
                                    fontSize(13f)
                                    color(c)
                                }
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

    /** 拉取卡片行情与分时（每个 code 仅拉一次；已缓存则直接复用） */
    private fun ensureCardData(code: String, name: String) {
        if (code.isBlank() || code in fetching) return
        val hasQuote = cardQuotes[code] != null
        val hasMinute = cardMinutes[code]?.isNotEmpty() == true
        if (hasQuote && hasMinute) return
        fetching.add(code)
        if (!hasQuote) {
            StockRepository.fetchQuotes(network, listOf(code)) { list ->
                cardQuotes[code] = list.firstOrNull()
                quoteTick++
                fetching.remove(code)
            }
        }
        if (!hasMinute) {
            StockRepository.fetchMinute(network, code) { points ->
                if (points.isNotEmpty()) {
                    cardMinutes[code] = points
                    quoteTick++
                }
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
            // 建立响应式依赖：sending 状态变化时输入区整体重绘（切换 发送/停止 按钮）
            val isSending = ctx.sending
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
                            placeholder(
                                if (ctx.editingUserMsgTs != null) "修改消息后发送，将覆盖原对话…"
                                else "问问 AI 关于股票的问题..."
                            )
                            placeholderColor(StockColors.TEXT_SUB)
                            maxTextLength(500)
                        }
                        event {
                            textDidChange { ctx.inputText = it.text }
                        }
                    }
                }
                // 发送按钮：双形态
                // - 正常态：主题色「发送」
                // - sending 中：红色「停止」，点击中断当前生成
                if (!isSending) {
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
                                text(if (ctx.editingUserMsgTs != null) "替换" else "发送")
                                fontSize(14f)
                                color(Color.WHITE)
                                fontWeightSemiBold()
                            }
                        }
                        event {
                            click { ctx.send() }
                        }
                    }
                } else {
                    View {
                        attr {
                            marginLeft(10f)
                            width(58f)
                            height(40f)
                            borderRadius(20f)
                            allCenter()
                            backgroundColor(Color(0xFFE53935))
                        }
                        Text {
                            attr {
                                text("停止")
                                fontSize(14f)
                                color(Color.WHITE)
                                fontWeightSemiBold()
                            }
                        }
                        event {
                            click { ctx.stopGenerating() }
                        }
                    }
                }
            }
        }
    }

    // ==================== 修改模式提示条 ====================

    /** 用户消息点"修改"后显示在输入区上方的提示条，可点取消 */
    private fun editBanner(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(14f)
                    paddingRight(8f)
                    paddingTop(8f)
                    paddingBottom(8f)
                    backgroundColor(Color(0xFFFFF7E6))
                    border(Border(0.5f, BorderStyle.SOLID, Color(0xFFFFE7BA)))
                }
                Text {
                    attr {
                        flex(1f)
                        text("✏️ 修改模式：修改后点击「替换」将删除原消息及之后对话并重新生成")
                        fontSize(12f)
                        color(Color(0xFFAD6800))
                        lineHeight(18f)
                    }
                }
                View {
                    attr {
                        paddingLeft(10f)
                        paddingRight(10f)
                        paddingTop(4f)
                        paddingBottom(4f)
                    }
                    Text {
                        attr {
                            text("取消")
                            fontSize(12f)
                            color(StockColors.ACCENT)
                        }
                    }
                    event {
                        click {
                            ctx.editingUserMsgTs = null
                            ctx.chatInputRef?.view?.setText("")
                            ctx.inputText = ""
                        }
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

    // ==================== 消息操作菜单（删除 / 修改 / 重新生成） ====================

    /**
     * 消息操作弹层。点 ⋮ 后展示，根据当前消息 role 渲染不同操作：
     * - 用户消息：删除、修改
     * - AI 消息：删除、重新生成
     */
    private fun msgMenu(): ViewBuilder {
        val ctx = this
        return {
            val conv = ctx.activeConv()
            val ts = ctx.pendingMenuMsgTs
            val msg = conv?.messages?.firstOrNull { it.ts == ts }
            Modal {
                View {
                    attr {
                        flex(1f)
                        allCenter()
                        backgroundColor(Color(0x66000000))
                    }
                    event {
                        click { ctx.pendingMenuMsgTs = null }
                    }
                    // 卡片：根据消息角色决定展示哪些操作
                    val isUser = msg?.role == "user"
                    val isValid = msg != null
                    View {
                        attr {
                            width(ctx.pagerData.pageViewWidth - 80f)
                            borderRadius(12f)
                            backgroundColor(Color.WHITE)
                            padding(16f)
                            flexDirectionColumn()
                        }
                        event {
                            click { /* 拦截冒泡，避免点菜单卡片关闭弹窗 */ }
                        }
                        Text {
                            attr {
                                text(
                                    when {
                                        !isValid -> "消息不存在"
                                        isUser -> "用户消息操作"
                                        else -> "AI 消息操作"
                                    }
                                )
                                fontSize(14f)
                                color(StockColors.TEXT_SUB)
                                marginBottom(10f)
                            }
                        }
                        if (isValid) {
                        if (isUser) {
                            // 修改
                            View {
                                attr {
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    paddingTop(12f)
                                    paddingBottom(12f)
                                    borderRadius(8f)
                                    backgroundColor(Color(0xFFF5F8FF))
                                    paddingLeft(14f)
                                }
                                Text {
                                    attr {
                                        flex(1f)
                                        text("✏️  修改（替换该消息及之后对话并重新生成）")
                                        fontSize(14f)
                                        color(StockColors.ACCENT)
                                    }
                                }
                                event {
                                    click {
                                        ts?.let { ctx.startEditUserMessage(it) }
                                    }
                                }
                            }
                            View {
                                attr { height(8f) }
                            }
                            // 删除
                            View {
                                attr {
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    paddingTop(12f)
                                    paddingBottom(12f)
                                    borderRadius(8f)
                                    backgroundColor(Color(0xFFFFF1F0))
                                    paddingLeft(14f)
                                }
                                Text {
                                    attr {
                                        flex(1f)
                                        text("🗑  删除该消息")
                                        fontSize(14f)
                                        color(StockColors.UP)
                                    }
                                }
                                event {
                                    click {
                                        ts?.let { ctx.deleteMessage(it) }
                                    }
                                }
                            }
                        } else {
                            // 重新生成
                            View {
                                attr {
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    paddingTop(12f)
                                    paddingBottom(12f)
                                    borderRadius(8f)
                                    backgroundColor(Color(0xFFF5F8FF))
                                    paddingLeft(14f)
                                }
                                Text {
                                    attr {
                                        flex(1f)
                                        text("🔄  重新生成（删除该回复并重新调用 AI）")
                                        fontSize(14f)
                                        color(StockColors.ACCENT)
                                    }
                                }
                                event {
                                    click {
                                        ts?.let { ctx.regenerateMessage(it) }
                                    }
                                }
                            }
                            View {
                                attr { height(8f) }
                            }
                            // 删除
                            View {
                                attr {
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    paddingTop(12f)
                                    paddingBottom(12f)
                                    borderRadius(8f)
                                    backgroundColor(Color(0xFFFFF1F0))
                                    paddingLeft(14f)
                                }
                                Text {
                                    attr {
                                        flex(1f)
                                        text("🗑  删除该消息")
                                        fontSize(14f)
                                        color(StockColors.UP)
                                    }
                                }
                                event {
                                    click {
                                        ts?.let { ctx.deleteMessage(it) }
                                    }
                                }
                            }
                        }
                        }  // 闭合 if (isValid)
                        View {
                            attr {
                                marginTop(12f)
                                height(36f)
                                borderRadius(18f)
                                allCenter()
                                backgroundColor(Color(0xFFF5F6F8))
                            }
                            Text {
                                attr {
                                    text("取消")
                                    fontSize(13f)
                                    color(StockColors.TEXT_SUB)
                                }
                            }
                            event {
                                click { ctx.pendingMenuMsgTs = null }
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
        // 修改模式：点发送走 submitEdit（截断到原消息，替换后重新发问）
        if (editingUserMsgTs != null) {
            submitEdit(text)
        } else {
            doSend(text)
        }
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
        // 公共 chat 逻辑（chatSeq 竞态保护）
        callChat(updated, updated.messages)
    }

    /**
     * 公共 chat 调用：每次 +1 chatSeq，callback 内捕获当时 seq 编号；
     * 返回时若 seq != 当前 chatSeq 说明已被中断或被新一轮请求替代，直接丢弃。
     * 真正"中断"做不到（Kuikly NetworkModule.httpRequest 无 cancel API），
     * 但通过序号机制可以确保：停止后立即发新消息，旧请求结果不会污染新会话。
     */
    private fun callChat(conv: Conversation, history: List<ChatMessage>) {
        val seq = ++chatSeq
        val config = AiAnalysisService.loadConfig(sp)
        AiAnalysisService.chat(network, config, history) { content, err ->
            // 序号不匹配 → 被中断或被新请求替代，丢弃
            if (seq != chatSeq) return@chat
            sending = false
            val nowConv = ConversationStore.load(sp).firstOrNull { it.id == conv.id }
                ?: return@chat
            val reply = if (err != null) {
                ChatMessage("assistant", err, System.currentTimeMillis(), error = true)
            } else {
                ChatMessage("assistant", content, System.currentTimeMillis())
            }
            ConversationStore.update(sp, nowConv.copy(messages = nowConv.messages + reply))
            reloadConversations()
        }
    }

    /** 中断生成：自增 chatSeq 让当前请求失效，UI 立即恢复 */
    private fun stopGenerating() {
        if (!sending) return
        chatSeq++
        sending = false
        bridgeToast("已停止生成")
    }

    /** 删除单条消息并持久化 */
    private fun deleteMessage(ts: Long) {
        val conv = activeConv() ?: run {
            pendingMenuMsgTs = null
            return
        }
        val updated = conv.copy(messages = conv.messages.filter { it.ts != ts })
        ConversationStore.update(sp, updated)
        pendingMenuMsgTs = null
        reloadConversations()
    }

    /** 启动"修改用户消息"：内容回填输入框，editingUserMsgTs 标记修改对象 */
    private fun startEditUserMessage(ts: Long) {
        val conv = activeConv() ?: return
        val msg = conv.messages.firstOrNull { it.ts == ts && it.role == "user" } ?: return
        editingUserMsgTs = ts
        pendingMenuMsgTs = null
        // 回填输入框
        chatInputRef?.view?.setText(msg.content)
        inputText = msg.content
        bridgeToast("修改后点「替换」将覆盖原对话")
    }

    /**
     * 完成修改：截断到原消息（含该消息），追加新用户消息，重新生成 AI 回复。
     */
    private fun submitEdit(newText: String) {
        if (sending) return
        val ts = editingUserMsgTs ?: return
        val conv = activeConv() ?: return
        if (newText.isEmpty()) {
            bridgeToast("内容不能为空")
            return
        }
        // 找到该消息在 messages 中的索引，截断到该消息之前
        val idx = conv.messages.indexOfFirst { it.ts == ts }
        if (idx < 0) return
        val kept = conv.messages.subList(0, idx).toList()
        val updated = conv.copy(
            messages = kept + ChatMessage("user", newText, System.currentTimeMillis())
        )
        ConversationStore.update(sp, updated)
        editingUserMsgTs = null
        chatInputRef?.view?.setText("")
        inputText = ""
        reloadConversations()
        // 重新发问（chatSeq 竞态保护）
        sending = true
        callChat(updated, updated.messages)
    }

    /**
     * 重新生成 AI 回复：找到该 AI 消息，截断到它之前（不含），用其之前的 history 重新调 chat。
     * 必须保证该 AI 消息之前有 user 消息（idx > 0）才有意义。
     */
    private fun regenerateMessage(ts: Long) {
        if (sending) return
        val conv = activeConv() ?: run {
            pendingMenuMsgTs = null
            return
        }
        val idx = conv.messages.indexOfFirst { it.ts == ts }
        // 必须存在且前面有消息（否则无可用上下文）
        if (idx <= 0) {
            pendingMenuMsgTs = null
            bridgeToast("无法重新生成：缺少上下文")
            return
        }
        val kept = conv.messages.subList(0, idx).toList()
        val updated = conv.copy(messages = kept)
        ConversationStore.update(sp, updated)
        pendingMenuMsgTs = null
        reloadConversations()
        // 用 kept 作为 history 重新调 chat（chatSeq 竞态保护）
        sending = true
        callChat(updated, updated.messages)
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
