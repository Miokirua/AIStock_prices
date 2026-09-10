package com.example.aistock_prices.stock.ai

import com.example.aistock_prices.base.BasePager
import com.example.aistock_prices.stock.data.StockQuote
import com.example.aistock_prices.stock.data.StockRepository
import com.example.aistock_prices.stock.ui.StockFormat
import com.example.aistock_prices.stock.ui.ThemePalette
import com.example.aistock_prices.stock.ui.ThemePalettes
import com.example.aistock_prices.stock.ui.markdownConfig
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.layout.FlexJustifyContent
import com.tencent.kuikly.core.layout.Frame
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.DivView
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.ScrollerView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.KuiklyMarkdown
import com.tencent.kuiklybase.config.MarkdownConfig

/**
 * AI 问答视图（首页「AI 问答」内联 Tab 与 ai_chat 独立页共用）。
 *
 * 能力：
 * - 多会话：切换 / 新建 / 删除（删除弹确认框），会话跨启动持久化（SP）
 * - AI 回复：整体返回 + 加载动画；Markdown 渲染 + ```stock 标记的实时行情卡片
 * - 未配置 API：引导卡片 + 「去设置」跳转；未启用预设时不可发送/新建
 * - 长回复：对话内只展示「简单回答」（开头节选），「查看完整分析 →」进入独立详情页看全文
 * - 股票卡片点击进个股详情；K线追问的数据上下文以 context 消息注入（对话不显示）
 */
internal class AiChatView : ComposeView<AiChatViewAttr, AiChatViewEvent>() {

    /** 当前主题色板（跟随宿主注入的 isNightMode；主题切换由宿主 recreate 重建视图） */
    private val pal: ThemePalette
        get() = ThemePalettes.of((getPager() as? BasePager)?.isNightMode() ?: false)

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
    /** 菜单卡片在页面中的定位（卡片 absolutePosition 于全屏遮罩内，zIndex 高于遮罩） */
    private var menuCardX by observable(0f)
    private var menuCardY by observable(0f)
    /** ⋮ 按钮 ref（msg.ts -> 按钮），点击时换算卡片坐标 */
    private val menuTriggerRefs = mutableMapOf<Long, ViewRef<DivView>>()
    /**
     * AiChatView 内部根容器（body 渲染容器）。
     * 菜单卡片 absolutePosition 相对该容器；convertFrame 也换算到该坐标系，
     * 保证与卡片定位同基准（宿主偏移：独立页导航栏 / 首页 Tab 栏均不影响）。
     */
    private var rootContainerRef: ViewContainer<*, *>? = null
    /** 正在"修改"模式中的用户消息 ts（null=正常输入） */
    private var editingUserMsgTs by observable<Long?>(null)
    /** 股票卡片行情/分时就绪后自增，触发卡片重绘 */
    private var quoteTick by observable(0)
    /** 外部刷新信号（如从设置页返回）：自增触发 body 重跑，重新求值 isConfigured() 等 SP 依赖 */
    private var refreshTick by observable(0)

    private var chatInputRef: ViewRef<InputView>? = null
    /** 消息列表 Scroller 引用：进入页面/发送消息后自动滚动到底端 */
    private var chatScrollerRef: ViewRef<ScrollerView<*, *>>? = null
    /** code -> 行情（null=尚未加载成功） */
    private val cardQuotes = mutableMapOf<String, StockQuote?>()
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
        // 渲染数据源剔除 context（数据上下文）消息：它们仅随会话持久化供发送时映射 system，
        // 若混入 vfor 渲染，context 项不会生成任何子视图，将触发框架异常（vfor 要求每项恰好生成一个子视图）
        activeMsgs.addAll(
            activeMessages().filter { it.role != ChatMessage.ROLE_CONTEXT }
        )
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        reloadConversations()
        val saved = sp.getItem(KEY_ACTIVE_ID)
        if (saved.isNotBlank() && conversations.any { it.id == saved }) activeId = saved
        if (activeId.isBlank() && conversations.isNotEmpty()) activeId = conversations.first().id
        // 修复：activeId 在上述赋值后才确定，必须再同步一次消息列表（vfor 数据源 activeMsgs），
        // 否则恢复选中会话后消息区为空（需手动重新点一次会话才显示历史记录）
        syncActiveMsgs()
        // 进入页面：等列表布局完成后滚动到底端
        scrollToBottom(animated = false)
    }

    /** 消息列表滚动到底端（延迟一拍等内容布局完成；offsetY 传大值由平台 clamp） */
    private fun scrollToBottom(animated: Boolean = true) {
        setTimeout(pagerId, 80) {
            chatScrollerRef?.view?.setContentOffset(0f, 100000f, animated)
        }
    }

    /** 外部(ai_chat 独立页)初始化:切换到指定股票会话;autoSend 时自动发问 */
    fun initForStock(code: String, name: String, autoSend: Boolean, question: String? = null) {
        // 切换股票时先静默中断在途 AI 生成,避免新会话发问被旧 sending 卡住
        cancelIfSending()
        val conv = ConversationStore.findOrCreateForStock(sp, code, name)
        activeId = conv.id
        sp.setItem(KEY_ACTIVE_ID, conv.id)
        reloadConversations()
        scrollToBottom(animated = false)
        if (autoSend && !sending) {
            val last = conv.messages.lastOrNull()
            val prompt = question ?: "帮我分析${name}（$code）这只股票"
            val alreadyAutoAsked = if (question == null) {
                last?.role == "user" && last.content.startsWith("帮我分析")
            } else {
                last?.role == "user" && last.content == prompt
            }
            if (!alreadyAutoAsked) {
                // 延迟到视图树稳定后再发问
                setTimeout(pagerId, 150) { doSend(prompt) }
            }
        }
    }

    /**
     * 详情页点 K 线追问入口：携带选中 bar 数据。
     * 数据上下文以独立 context 消息注入（对话中不渲染、发送时映射为 system 角色），
     * 不再拼进用户提问文本——用户在对话里只看到自己的问题；
     * 同时上下文注明"实时行情数据、与模型知识时间无关"，避免 AI 误判为"未来/不确定数据"。
     */
    fun initForStockWithBar(
        code: String,
        name: String,
        question: String,
        barDate: String,
        barOpen: Double,
        barClose: Double,
        barHigh: Double,
        barLow: Double,
        barVolume: Long
    ) {
        // 详情页 K 线追问:先静默中断在途 AI 生成,确保本次发问不被旧 sending 卡住
        cancelIfSending()
        val conv = ConversationStore.findOrCreateForStock(sp, code, name)
        activeId = conv.id
        sp.setItem(KEY_ACTIVE_ID, conv.id)
        reloadConversations()
        scrollToBottom(animated = false)
        if (sending) return
        // 已问过相同问题且尚在等待回复时不重复发问
        val last = conv.messages.lastOrNull()
        if (last?.role == ChatMessage.ROLE_USER && stripContextSuffix(last.content) == question) return
        // 数据上下文：标注为客户端实时行情接口数据（可信），要求模型直接采用、勿质疑时间
        val contextText = "【实时行情数据】以下是你在客户端看到的 K 线数据，来自行情接口（可信源，" +
                "时间与你的知识截止无关），请直接基于这些数据回答，不要质疑数据日期为未来或不真实：\n" +
                "$barDate 开盘 ${barOpen}、收盘 ${barClose}、最高 ${barHigh}、最低 ${barLow}、成交量 ${barVolume}"
        // 每次追问仅保留最新一条数据上下文，避免历史选中 bar 堆叠干扰
        val now = DateTime.currentTimestamp()
        val withCtx = conv.copy(
            messages = conv.messages.filter { it.role != ChatMessage.ROLE_CONTEXT } +
                    ChatMessage(ChatMessage.ROLE_CONTEXT, contextText, now)
        )
        ConversationStore.update(sp, withCtx)
        reloadConversations()
        // 延迟到视图树稳定后发问（doSend 内部会读取启用中的配置，未配置则提示不发送）
        setTimeout(pagerId, 150) { doSend(question) }
    }

    /**
     * 外部刷新入口（页面重新出现时调用）：
     * 重新加载会话并触发 body 重跑，使 SP 依赖（如 isConfigured）重新求值。
     */
    fun reload() {
        refreshTick++
        reloadConversations()
        scrollToBottom(animated = false)
    }

    private fun isConfigured(): Boolean = AiAnalysisService.activePreset(sp) != null

    /** 当前启用中预设的运行时配置（未启用返回 null，用于发送时取真实可用配置） */
    private fun activeAiConfig(): AiConfig? = AiAnalysisService.activePreset(sp)?.let {
        AiConfig(it.baseUrl, it.apiKey, it.model)
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            ctx.rootContainerRef = this
            attr {
                flex(1f)
                backgroundColor(ctx.pal.bgPage)
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
            // ---------- 消息操作菜单：全屏遮罩（zIndex 149，点击任意非菜单区域关闭）+ 菜单卡片（遮罩内 absolutePosition，zIndex 150 盖过遮罩） ----------
            vif({ ctx.pendingMenuMsgTs != null }) {
                View {
                    attr {
                        absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                        zIndex(149)
                        backgroundColor(Color(0x0A000000))
                    }
                    event {
                        click { ctx.pendingMenuMsgTs = null }
                    }
                    // 菜单卡片：绝对定位在 ⋮ 按钮附近（坐标由 openMessageMenu 换算），
                    // 作为遮罩子视图且 zIndex 更高，点击功能键命中卡片而非遮罩
                    View {
                        attr {
                            absolutePosition(top = ctx.menuCardY, left = ctx.menuCardX)
                            zIndex(150)
                            borderRadius(10f)
                            backgroundColor(ctx.pal.card)
                            border(Border(1f, BorderStyle.SOLID, ctx.pal.divider))
                            padding(4f)
                            flexDirectionColumn()
                        }
                        val pendingMsg = ctx.activeMsgs.firstOrNull { it.ts == ctx.pendingMenuMsgTs }
                        if (pendingMsg != null) {
                            if (pendingMsg.role == "user") {
                                // 用户消息：修改（铅笔）、删除（垃圾桶）
                                ctx.menuItem("✏️", "修改", ctx.pal.accent) {
                                    ctx.startEditUserMessage(pendingMsg.ts)
                                }.invoke(this)
                                ctx.menuItem("🗑️", "删除", ctx.pal.up) {
                                    ctx.deleteMessage(pendingMsg.ts)
                                }.invoke(this)
                            } else {
                                // AI 消息：重新生成（循环）、删除（垃圾桶）
                                ctx.menuItem("🔄", "重新生成", ctx.pal.accent) {
                                    ctx.regenerateMessage(pendingMsg.ts)
                                }.invoke(this)
                                ctx.menuItem("🗑️", "删除", ctx.pal.up) {
                                    ctx.deleteMessage(pendingMsg.ts)
                                }.invoke(this)
                            }
                        }
                    }
                }
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
                    backgroundColor(ctx.pal.card)
                    border(Border(0.5f, BorderStyle.SOLID, ctx.pal.divider))
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
                        backgroundColor(ctx.pal.chipBg)
                    }
                    Text {
                        attr {
                            text("☰ ")
                            fontSize(15f)
                            color(ctx.pal.accent)
                        }
                    }
                    Text {
                        attr {
                            text(ctx.activeConv()?.displayName ?: "选择会话")
                            fontSize(14f)
                            fontWeightSemiBold()
                            color(ctx.pal.textMain)
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
                        backgroundColor(ctx.pal.accentChipBg)
                    }
                    Text {
                        attr {
                            text("＋ 新建")
                            fontSize(13f)
                            color(ctx.pal.accent)
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
                    backgroundColor(ctx.pal.maskDim)
                }
                event {
                    click { ctx.showConvPanel = false }
                }
                // 左侧抽屉面板
                View {
                    attr {
                        absolutePosition(top = 0f, left = 0f, bottom = 0f)
                        width(drawerWidth)
                        backgroundColor(ctx.pal.card)
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
                            border(Border(0.5f, BorderStyle.SOLID, ctx.pal.divider))
                        }
                        Text {
                            attr {
                                flex(1f)
                                text("会话列表")
                                fontSize(15f)
                                fontWeightSemiBold()
                                color(ctx.pal.textMain)
                            }
                        }
                        View {
                            attr {
                                paddingTop(6f)
                                paddingBottom(6f)
                                paddingLeft(12f)
                                paddingRight(12f)
                                borderRadius(6f)
                                backgroundColor(ctx.pal.accentChipBg)
                            }
                            Text {
                                attr {
                                    text("＋ 新建")
                                    fontSize(13f)
                                    color(ctx.pal.accent)
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
                                        if (conv.id == ctx.activeId) ctx.pal.accentChipBg else ctx.pal.card
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
                                            color(ctx.pal.textMain)
                                        }
                                    }
                                    Text {
                                        attr {
                                            text(
                                                when {
                                                    conv.stockCode != null -> "股票：${conv.stockCode}"
                                                    conv.messages.isEmpty() -> "空对话"
                                                    else -> {
                                                        val visible = conv.messages.count {
                                                            it.role == ChatMessage.ROLE_USER || it.role == ChatMessage.ROLE_ASSISTANT
                                                        }
                                                        if (visible == 0) "空对话" else "共 $visible 条消息"
                                                    }
                                                }
                                            )
                                            fontSize(11f)
                                            color(ctx.pal.textSub)
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
                                            color(ctx.pal.up)
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

    /** 新建会话(先关面板,再延迟刷新列表,避免 vfor 遍历期间修改列表导致崩溃) */
    private fun onCreateConversation() {
        // 切换/新建前先静默中断在途 AI 生成(++chatSeq),确保新会话发问不被旧 sending 卡住
        cancelIfSending()
        // 未启用 AI 服务时不伪装"已新建对话",直接引导先配置
        if (!isConfigured()) {
            bridgeToast("请先配置 AI 服务")
            return
        }
        val conv = ConversationStore.newConversation(sp)
        activeId = conv.id
        sp.setItem(KEY_ACTIVE_ID, conv.id)
        showConvPanel = false
        bridgeToast("已新建对话")
        setTimeout(pagerId, 50) { reloadConversations() }
    }

    /** 切换会话(同样先关面板再刷新) */
    private fun onSelectConversation(conv: Conversation) {
        // 切换前先静默中断在途 AI 生成,避免新会话发问被旧 sending 卡住
        cancelIfSending()
        activeId = conv.id
        sp.setItem(KEY_ACTIVE_ID, conv.id)
        showConvPanel = false
        pendingMenuMsgTs = null
        setTimeout(pagerId, 50) {
            reloadConversations()
            scrollToBottom(animated = false)
        }
    }

    // ==================== 消息列表 ====================

    private fun messageList(): ViewBuilder {
        val ctx = this
        return {
            Scroller {
                ref { ctx.chatScrollerRef = it }
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
        // 数据上下文消息（K线追问注入的行情数据）兜底防御：正常已被 syncActiveMsgs 过滤；
        // 万一混入渲染数据源，渲染 0 尺寸占位以满足 vfor「每项恰生成一个子视图」的框架约束，
        // 不可见且不抛异常（此前返回空 builder 会因子视图增量为 0 直接触发框架运行时异常闪退）
        if (msg.role == ChatMessage.ROLE_CONTEXT) {
            return {
                View {
                    attr {
                        width(0f)
                        height(0f)
                    }
                }
            }
        }
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
                            backgroundColor(ctx.pal.accent)
                        }
                        Text {
                            attr {
                                // 旧版本曾把"（数据上下文：…）"拼在提问尾部，渲染时剥离避免暴露给用户
                                text(stripContextSuffix(msg.content))
                                fontSize(14f)
                                color(ctx.pal.onAccent)
                                lineHeight(20f)
                            }
                        }
                    }
                } else {
                    val longReply = msg.content.length > CHAT_SUMMARY_MAX
                    View {
                        attr {
                            maxWidth(ctx.pagerData.pageViewWidth - 40f)
                            borderRadius(12f)
                            padding(14f)
                            backgroundColor(ctx.pal.card)
                            border(Border(1f, BorderStyle.SOLID, ctx.pal.divider))
                            flexDirectionColumn()
                        }
                        vif({ msg.error }) {
                            Text {
                                attr {
                                    text("⚠️ ${msg.content}")
                                    fontSize(13f)
                                    color(ctx.pal.errRed)
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
                                val conv = ctx.activeConv()
                                // 长回复仅展示「简单回答」（开头节选，剔除行情卡片标记）；
                                // 完整内容不再在对话内原地展开，点击底部「查看完整分析」进独立详情页
                                val renderText = if (longReply) ctx.summarizeForChat(msg.content) else msg.content
                                ctx.renderSegments(renderText, includeStock = !longReply).invoke(this)
                                // 兜底：AI 未输出 ```stock 标记，但会话关联了股票 → 自动补一张该股票卡片
                                // （仅短回复显示；长回复的行情卡片在详情页内解析展示）
                                vif({
                                    !longReply &&
                                            !parseChatSegments(msg.content).any { it.type == "stock" } &&
                                            conv?.stockCode != null
                                }) {
                                    if (conv?.stockCode != null) {
                                        ctx.stockCard(conv.stockCode, conv.stockName ?: "").invoke(this)
                                    }
                                }
                            }
                        }
                        // 「查看完整分析」入口：长回复显示，点击进入独立详情页看全文
                        vif({ longReply }) {
                            View {
                                attr {
                                    marginTop(12f)
                                    paddingTop(10f)
                                    border(Border(0.5f, BorderStyle.SOLID, ctx.pal.divider))
                                    flexDirectionRow()
                                    alignItemsCenter()
                                }
                                Text {
                                    attr {
                                        flex(1f)
                                        text("查看完整分析 →")
                                        fontSize(12f)
                                        color(ctx.pal.accent)
                                    }
                                }
                                event {
                                    click { ctx.openFullAnalysis(msg.ts) }
                                }
                            }
                        }
                    }
                }
                // ⋮ 菜单触发器
                View {
                    attr {
                        flexDirectionRow()
                        justifyContent(if (msg.role == "user") FlexJustifyContent.FLEX_END else FlexJustifyContent.FLEX_START)
                        marginTop(2f)
                        paddingLeft(if (msg.role == "user") 0f else 4f)
                        paddingRight(if (msg.role == "user") 4f else 0f)
                    }
                    View {
                        ref { ctx.menuTriggerRefs[msg.ts] = it }
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
                                color(ctx.pal.textSub)
                            }
                        }
                        event {
                            click { ctx.openMessageMenu(msg.ts) }
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
                        backgroundColor(ctx.pal.card)
                        border(Border(1f, BorderStyle.SOLID, ctx.pal.divider))
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
                            color(ctx.pal.textSub)
                        }
                    }
                }
            }
        }
    }

    /** 组合渲染：markdown 段 -> KuiklyMarkdown；stock 段 -> 行情卡片 */
    private fun renderSegments(content: String, includeStock: Boolean = true): ViewBuilder {
        val ctx = this
        return {
            parseChatSegments(content).forEach { seg ->
                when (seg.type) {
                    "stock" -> if (includeStock) {
                        ctx.stockCard(seg.code, seg.name).invoke(this)
                    }
                    else -> {
                        // Markdown 段：包固定宽度容器并留出上下间距，
                        // 避免多段/卡片间数据堆积重叠，同时保证换行不溢出
                        View {
                            attr {
                                width(ctx.pagerData.pageViewWidth - 64f)
                                flexDirectionColumn()
                                marginTop(4f)
                                marginBottom(4f)
                            }
                            KuiklyMarkdown(
                                content = seg.text,
                                config = markdownConfig((getPager() as? BasePager)?.isNightMode() ?: false)
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 长回复的「简单回答」：先剔除 ```stock 行情卡片标记（避免摘要出现围栏残迹/半截代码块），
     * 再取正文开头数句要点——在段落/句末标点边界切分，最多 CHAT_SUMMARY_MAX 字，末尾附查看提示。
     * 完整内容由「查看完整分析」跳转独立详情页展示。
     */
    private fun summarizeForChat(content: String): String {
        val plain = parseChatSegments(content)
            .filter { it.type == "markdown" }
            .joinToString("\n") { it.text.trim() }
            .trim()
        if (plain.isBlank()) return "AI 已生成回复，详细内容请查看完整分析。"
        val max = CHAT_SUMMARY_MAX
        if (plain.length <= max) return plain
        // 尝试在最近的换行或句末标点边界切，避免切断 Markdown 列表/代码块/一句话
        val cut = (max downTo (max * 3 / 4))
            .firstOrNull { i ->
                val c = plain[i]
                c == '\n' || c == '。' || c == '！' || c == '？' || c == '；' || c == ';'
            } ?: max
        return plain.substring(0, cut + 1).trimEnd() + "\n\n…（完整分析请点下方）"
    }

    /** 打开「查看完整分析」：携带当前会话 id 与消息时间戳，由宿主跳转 result_detail 独立详情页 */
    private fun openFullAnalysis(ts: Long) {
        val convId = activeConv()?.id ?: return
        event.onOpenResult?.invoke(convId, ts)
    }

    // ==================== 股票卡片（实时行情） ====================

    private fun stockCard(code: String, name: String): ViewBuilder {
        val ctx = this
        return {
            ctx.ensureCardData(code, name)
            View {
                attr {
                    marginTop(8f)
                    marginBottom(6f)
                    borderRadius(10f)
                    padding(12f)
                    backgroundColor(ctx.pal.chipBg)
                    border(Border(1f, BorderStyle.SOLID, ctx.pal.divider))
                    flexDirectionColumn()
                }
                // 头部：名称 + 代码 + 进入详情
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        marginBottom(2f)
                    }
                    Text {
                        attr {
                            flex(1f)
                            text(if (name.isNotBlank()) name else code)
                            fontSize(14f)
                            fontWeightSemiBold()
                            color(ctx.pal.textMain)
                        }
                    }
                    Text {
                        attr {
                            text(code)
                            fontSize(11f)
                            color(ctx.pal.textSub)
                        }
                    }
                }
                // 行情区
                val q = ctx.cardQuotes[code]
                ctx.quoteTick // 建立响应式依赖
                vif({ q != null }) {
                    val qq = q!!
                    val c = ctx.pal.ofChange(qq.change)
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            marginTop(8f)
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
                }
                velse {
                    View {
                        attr {
                            marginTop(12f)
                            marginBottom(4f)
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
                                color(ctx.pal.textSub)
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

    /** 拉取卡片行情（每个 code 仅拉一次；已缓存则直接复用） */
    private fun ensureCardData(code: String, name: String) {
        if (code.isBlank() || code in fetching) return
        if (cardQuotes[code] != null) return
        fetching.add(code)
        StockRepository.fetchQuotes(network, listOf(code)) { list ->
            cardQuotes[code] = list.firstOrNull()
            quoteTick++
            fetching.remove(code)
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
                    backgroundColor(ctx.pal.card)
                    border(Border(0.5f, BorderStyle.SOLID, ctx.pal.divider))
                }
                View {
                    attr {
                        flex(1f)
                        height(40f)
                        borderRadius(20f)
                        backgroundColor(ctx.pal.chipBg)
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
                            color(ctx.pal.textMain)
                            placeholder(
                                if (ctx.editingUserMsgTs != null) "修改消息后发送，将覆盖原对话…"
                                else "问问 AI 关于股票的问题..."
                            )
                            placeholderColor(ctx.pal.textSub)
                            maxTextLength(500)
                        }
                        event {
                            textDidChange { ctx.inputText = it.text }
                        }
                    }
                }
                // 发送/停止按钮：双形态（vif/velse 依赖 ctx.sending）
                // - 正常态：主题色圆形「发送」（修改模式为「替换」）
                // - sending 中：红色圆形停止按钮（中间白色方块图标，无文字），点击中断当前生成
                vif({ !ctx.sending }) {
                    View {
                        attr {
                            marginLeft(10f)
                            width(58f)
                            height(40f)
                            borderRadius(20f)
                            allCenter()
                            backgroundColor(ctx.pal.accent)
                        }
                        Text {
                            attr {
                                text(if (ctx.editingUserMsgTs != null) "替换" else "发送")
                                fontSize(14f)
                                color(ctx.pal.onAccent)
                                fontWeightSemiBold()
                            }
                        }
                        event {
                            click { ctx.send() }
                        }
                    }
                }
                velse {
                    View {
                        attr {
                            marginLeft(10f)
                            width(40f)
                            height(40f)
                            borderRadius(20f)
                            allCenter()
                            backgroundColor(ctx.pal.stopRed)
                        }
                        // 停止图标：白色方块（无文字）
                        View {
                            attr {
                                width(14f)
                                height(14f)
                                borderRadius(2f)
                                backgroundColor(ctx.pal.onAccent)
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
                    backgroundColor(ctx.pal.warnBg)
                    border(Border(0.5f, BorderStyle.SOLID, ctx.pal.warnBorder))
                }
                Text {
                    attr {
                        flex(1f)
                        text("✏️ 修改模式：修改后点击「替换」将删除原消息及之后对话并重新生成")
                        fontSize(12f)
                        color(ctx.pal.warnText)
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
                            color(ctx.pal.accent)
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
                        backgroundColor(ctx.pal.card)
                        padding(20f)
                        flexDirectionColumn()
                    }
                    Text {
                        attr {
                            text("AI 问答")
                            fontSize(17f)
                            fontWeightBold()
                            color(ctx.pal.textMain)
                            marginBottom(8f)
                        }
                    }
                    Text {
                        attr {
                            text("尚未配置 AI 服务。配置后可以在这里与 AI 多轮讨论股票、指数，回复支持 Markdown 与实时行情卡片。")
                            fontSize(13f)
                            color(ctx.pal.textSub)
                            lineHeight(20f)
                        }
                    }
                    View {
                        attr {
                            marginTop(16f)
                            height(40f)
                            borderRadius(20f)
                            allCenter()
                            backgroundColor(ctx.pal.accent)
                        }
                        Text {
                            attr {
                                text("去设置")
                                fontSize(15f)
                                color(ctx.pal.onAccent)
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
                        backgroundColor(ctx.pal.maskFull)
                    }
                    event {
                        click { ctx.pendingDeleteConv = null }
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
                                text("删除对话")
                                fontSize(16f)
                                fontWeightSemiBold()
                                color(ctx.pal.textMain)
                                marginBottom(10f)
                            }
                        }
                        Text {
                            attr {
                                text("确定删除对话「${ctx.pendingDeleteConv?.displayName}」吗？删除后不可恢复。")
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
                                    click { ctx.pendingDeleteConv = null }
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

    // ==================== 消息操作菜单项（悬浮卡片内的图标按钮行） ====================

    /**
     * 打开消息操作菜单：延迟一拍换算 ⋮ 按钮在 AiChatView 根容器中的实际坐标
     * （convertFrame 换算到 rootContainerRef，与卡片 absolutePosition 同基准；
     * 滚动不改变布局 frame，需再减 contentViewOffsetY 得到当前屏幕位置）。
     *
     * 延迟目的：进入页面/切会话会自动滚动到底（setContentOffset 带动画），点击瞬间
     * contentViewOffsetY 可能仍是中间值（动画未完成）→ 坐标错位，菜单显示在错误位置
     * （真机症状：首次点菜单位置异常，滚动稳定后再点才正常）。延迟 50ms 等滚动稳定，
     * 160ms 再校准一次（动画可能仍在进行）。
     *
     * 菜单卡片 absolutePosition 定位到按钮附近（优先下方，空间不足向上弹，不被底部栏遮挡）。
     */
    private fun openMessageMenu(ts: Long) {
        // 先关闭可能存在的旧菜单（统一延迟重开，保证坐标计算时滚动已稳定）
        pendingMenuMsgTs = null
        setTimeout(pagerId, 50) {
            if (pendingMenuMsgTs != null) return@setTimeout // 期间菜单已被打开（先执行者生效），放弃本次
            if (!applyMenuCardPos(ts)) {
                // 按钮/容器定位失败（视图未就绪）：兜底屏幕中上部，保证菜单可见可点
                menuCardX = 24f
                menuCardY = 120f
            }
            pendingMenuMsgTs = ts
        }
        // 滚动动画可能仍在进行：显示后再校准一次坐标（若菜单仍打开）
        setTimeout(pagerId, 160) {
            if (pendingMenuMsgTs != ts) return@setTimeout
            applyMenuCardPos(ts)
        }
    }

    /**
     * 计算菜单卡片坐标并写入 [menuCardX]/[menuCardY]。
     * @return true=定位成功；false=按钮/容器未就绪（调用方需兜底坐标）。
     */
    private fun applyMenuCardPos(ts: Long): Boolean {
        val root = rootContainerRef ?: return false
        val trigger = menuTriggerRefs[ts]?.view ?: return false
        val pageFrame = trigger.convertFrame(Frame(0f, 0f, 0f, 0f), root)
        val scrollY = chatScrollerRef?.view?.contentViewOffsetY ?: 0f
        val btnX = pageFrame.x
        val btnY = pageFrame.y - scrollY
        val btnW = 36f // ⋮ 按钮估算宽
        val btnH = 30f // ⋮ 按钮估算高
        val cardW = 132f
        val cardH = 100f
        // 边界基准用 AiChatView 根容器实际尺寸（frame 布局尺寸），而非 pageViewWidth/Height——
        // 独立页导航栏 / 首页 Tab 栏会压缩可视区域，用页面高度判断会让最末端消息的菜单
        // 放下方时超出可视区，被底部栏遮挡
        val effW = root.frame.width.takeIf { it > 0f } ?: pagerData.pageViewWidth
        val effH = root.frame.height.takeIf { it > 0f } ?: pagerData.pageViewHeight
        // 对齐方向跟随消息角色：用户消息 ⋮ 在右 → 卡片右缘对齐按钮右缘（向左展开）；
        // AI 消息 ⋮ 在左 → 卡片左缘对齐按钮左缘（向右展开）
        val isUser = activeMsgs.firstOrNull { it.ts == ts }?.role == "user"
        val maxLeft = (effW - cardW - 8f).coerceAtLeast(8f)
        menuCardX = if (isUser) {
            (btnX + btnW - cardW).coerceIn(8f, maxLeft)
        } else {
            btnX.coerceIn(8f, maxLeft)
        }
        // 垂直：优先按钮下方；下方空间不足（底部输入栏/菜单栏遮挡）则向上弹出
        menuCardY = if (btnY + btnH + cardH + 12f <= effH) {
            btnY + btnH + 6f
        } else {
            (btnY - cardH - 6f).coerceAtLeast(8f)
        }
        return true
    }

    /** 悬浮菜单内的一行操作项：图标 + 文字，点击执行 [action] */
    private fun menuItem(icon: String, label: String, color: Color, action: () -> Unit): ViewBuilder {
        return {
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingTop(9f)
                    paddingBottom(9f)
                    paddingLeft(10f)
                    paddingRight(14f)
                    borderRadius(6f)
                }
                Text {
                    attr {
                        text(icon)
                        fontSize(16f)
                    }
                }
                Text {
                    attr {
                        text(label)
                        fontSize(13f)
                        color(color)
                        marginLeft(8f)
                    }
                }
                event {
                    click { action() }
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
        // 仅当存在"启用中的预设"才允许发送（停用/删除配置后不再能产生回复）
        val config = activeAiConfig()
        if (config == null) {
            bridgeToast("请先配置 AI 服务")
            return
        }
        sending = true
        pendingMenuMsgTs = null
        chatInputRef?.view?.setText("")
        inputText = ""
        // 数据上下文仅对紧邻的提问有效：保留会话末尾刚注入的 context（K线追问），
        // 移除更早遗留的 context，避免旧的选中 bar 数据干扰后续自由提问
        val baseMsgs = conv.messages
        val keepLastCtx = baseMsgs.isNotEmpty() && baseMsgs.last().role == ChatMessage.ROLE_CONTEXT
        val trimmed = if (keepLastCtx) {
            baseMsgs.filterIndexed { i, m -> m.role != ChatMessage.ROLE_CONTEXT || i == baseMsgs.lastIndex }
        } else {
            baseMsgs.filter { it.role != ChatMessage.ROLE_CONTEXT }
        }
        // 追加 user 消息并刷新标题（用最新发问的前 14 字作为标题）
        val updated = conv.copy(
            messages = trimmed + ChatMessage("user", text, DateTime.currentTimestamp()),
            title = deriveTitle(text)
        )
        ConversationStore.update(sp, updated)
        reloadConversations()
        scrollToBottom()
        // 公共 chat 逻辑（chatSeq 竞态保护）
        callChat(updated, updated.messages, config)
    }

    /**
     * 由用户消息派生会话标题：取前 14 字，去换行/空白，超长追加省略号。
     * 用户每次发问都更新到最新主题；股票名保留在 stockName 字段用于上下文关联。
     */
    private fun deriveTitle(text: String): String {
        val flat = text.trim().replace(Regex("\\s+"), " ")
        return if (flat.length <= 14) flat else flat.substring(0, 14) + "…"
    }

    /**
     * 公共 chat 调用：每次 +1 chatSeq，callback 内捕获当时 seq 编号；
     * 返回时若 seq != 当前 chatSeq 说明已被中断或被新一轮请求替代，直接丢弃。
     * 真正"中断"做不到（Kuikly NetworkModule.httpRequest 无 cancel API），
     * 但通过序号机制可以确保：停止后立即发新消息，旧请求结果不会污染新会话。
     */
    private fun callChat(conv: Conversation, history: List<ChatMessage>, config: AiConfig) {
        val seq = ++chatSeq
        AiAnalysisService.chat(network, config, history) { content, err ->
            // 序号不匹配 → 被中断或被新请求替代，丢弃
            if (seq != chatSeq) return@chat
            sending = false
            val nowConv = ConversationStore.load(sp).firstOrNull { it.id == conv.id }
                ?: return@chat
            val reply = if (err != null) {
                ChatMessage("assistant", err, DateTime.currentTimestamp(), error = true)
            } else {
                ChatMessage("assistant", content, DateTime.currentTimestamp())
            }
            ConversationStore.update(sp, nowConv.copy(messages = nowConv.messages + reply))
            reloadConversations()
            scrollToBottom()
        }
    }

    /** 中断生成：自增 chatSeq 让当前请求失效，UI 立即恢复 */
    private fun stopGenerating() {
        if (!sending) return
        chatSeq++
        sending = false
        bridgeToast("已停止生成")
    }

    /** 静默中断当前 AI 生成（无 toast）：切走 Tab / 页面被覆盖或销毁 / 退出页面时由宿主调用，避免继续空耗 token */
    fun cancelIfSending() {
        if (!sending) return
        chatSeq++
        sending = false
    }

    /** 视图从父容器移除（页面销毁/组件被 vif 移除）前：自动中断在途 AI 生成 */
    override fun willRemoveFromParentView() {
        super.willRemoveFromParentView()
        cancelIfSending()
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
        menuTriggerRefs.remove(ts)
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
            messages = kept + ChatMessage("user", newText, DateTime.currentTimestamp())
        )
        ConversationStore.update(sp, updated)
        editingUserMsgTs = null
        chatInputRef?.view?.setText("")
        inputText = ""
        reloadConversations()
        scrollToBottom()
        // 重新发问（chatSeq 竞态保护）
        val config = activeAiConfig()
        if (config == null) {
            bridgeToast("请先配置 AI 服务")
            return
        }
        sending = true
        callChat(updated, updated.messages, config)
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
        scrollToBottom()
        // 用 kept 作为 history 重新调 chat（chatSeq 竞态保护）
        val config = activeAiConfig()
        if (config == null) {
            bridgeToast("请先配置 AI 服务")
            return
        }
        sending = true
        callChat(updated, updated.messages, config)
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
        /** AI 回复超过此长度视为长回复：对话内只展示开头节选，完整内容进「查看完整分析」详情页 */
        private const val CHAT_SUMMARY_MAX = 120
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
