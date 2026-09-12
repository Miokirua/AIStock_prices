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
import com.example.aistock_prices.stock.ui.FeatureTips
import com.example.aistock_prices.stock.ui.StockFormat
import com.example.aistock_prices.stock.ui.ThemeMode
import com.example.aistock_prices.stock.ui.featureTipBar
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.TouchParams
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.layout.Frame
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.DivView
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
    /** 列表排序键：""=自选顺序（含置顶优先），"price"=最新价，"changePercent"=涨跌幅，"name"=名称 */
    private var sortKey by observable("")
    /** 当前排序是否为升序（价格/涨跌幅默认降序，名称默认升序） */
    private var sortAsc by observable(false)

    // ==================== 自选分组（v1.9.30「留」层） ====================
    /**
     * 当前选中的分组。三种取值语义（用哨兵值而非可空类型，避免各处判空）：
     * - [Watchlist.GROUP_ALL]（"全部"）→ 不过滤，展示所有自选
     * - `""` → 只看未分组
     * - 其它 → 该分组名
     */
    private var activeGroup by observable(Watchlist.GROUP_ALL)
    /** 分组名列表（vfor 数据源，需 observableList） */
    private var groupNames by observableList<String>()
    /** 各分组股票数：key 空串 = 未分组；用于 chip 上显示数量 */
    private var groupCounts by observable<Map<String, Int>>(emptyMap())
    /** 未过滤的全量行情快照（分组过滤 + 排序都基于它重算） */
    private var allQuotes: List<StockQuote> = emptyList()
    /** 「移动到分组」浮层当前操作的股票（null = 不显示） */
    private var moveSheetQuote by observable<StockQuote?>(null)
    /** 分组 chip 长按菜单：当前操作的分组名（null = 不显示） */
    private var groupMenuName by observable<String?>(null)
    /** 分组排序浮层是否显示（v1.9.33，从分组长按菜单进入） */
    private var showGroupSort by observable(false)
    /** 待删除的分组（二次确认用，null = 不显示） */
    private var pendingGroupDelete by observable<String?>(null)
    /** 新建 / 重命名分组弹窗 */
    private var showGroupDialog by observable(false)
    /** 空串 = 新建分组；非空 = 正在重命名该分组 */
    private var groupDialogRenameFrom by observable("")
    private var groupInput by observable("")
    private var groupInputRef: ViewRef<InputView>? = null

    // ==================== 就地功能提示（v1.9.31） ====================
    /**
     * 分组条下方的两条一次性提示（见 [FeatureTips]）。
     * 一次只显示一条：先讲分组，点「知道了」关掉后才轮到排序 —— 两条同时堆着会把列表顶下去。
     */
    private var tipGroupVisible by observable(false)
    private var tipSortVisible by observable(false)

    // ==================== 首启引导 ====================
    /** 引导根容器引用（convertFrame 换算基准，body 根） */
    private var guideRootRef: ViewContainer<*, *>? = null
    /** 首启询问弹窗：是否开始功能引导 */
    private var showGuidePrompt by observable(false)
    /** 引导步骤：0=不显示，1..10=第几步（见 [guideTitleFor] 的步骤表） */
    private var guideStep by observable(0)
    /** 当前高亮孔矩形（相对引导根容器坐标系） */
    private var guideRect by observable(Frame(0f, 0f, 0f, 0f))
    /** 当前步骤标题 / 描述文案 */
    private var guideTitle by observable("")
    private var guideDesc by observable("")
    /** 首启询问是否已检查过（避免 pageDidAppear 重复弹出） */
    private var guidePromptChecked = false
    /** 各引导目标元素引用 */
    private var tabSelfRef: ViewRef<DivView>? = null
    private var tabAiRef: ViewRef<DivView>? = null
    private var refreshBtnRef: ViewRef<DivView>? = null
    private var menuBtnRef: ViewRef<DivView>? = null
    private var menuAddRef: ViewRef<DivView>? = null
    private var menuThemeRef: ViewRef<DivView>? = null
    private var menuAiRef: ViewRef<DivView>? = null
    private var menuGuideRef: ViewRef<DivView>? = null

    /** 左滑操作条宽度（置顶 72f + 删除 72f） */
    /** 左滑操作条总宽：置顶 / 问 AI / 删除 三等分（每项约 68dp） */
    private val actionWidth = 204f
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
            ctx.guideRootRef = this
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
                    View {
                        ref { ctx.refreshBtnRef = it }
                        attr {
                            paddingLeft(6f)
                            paddingRight(6f)
                            paddingTop(4f)
                            paddingBottom(4f)
                            marginRight(10f)
                        }
                        Text {
                            attr {
                                text("刷新")
                                fontSize(14f)
                                color(ctx.pal.accent)
                            }
                        }
                        event {
                            click { ctx.onRefreshTap() }
                        }
                    }
                }
                // 三条横杠：下拉菜单（添加自选 / AI 设置）
                // 注：Text 不支持 padding，包一层 View 容器承载点击区域
                View {
                    ref { ctx.menuBtnRef = it }
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
                            ref { ctx.menuAddRef = it }
                            attr {
                                paddingTop(14f)
                                paddingBottom(14f)
                                paddingLeft(16f)
                                paddingRight(16f)
                                flexDirectionRow()
                                alignItemsCenter()
                            }
                            Text {
                                attr {
                                    // 图标列固定宽度并居中：四个菜单项的文字起始 x 保持一致
                                    text("＋")
                                    fontSize(15f)
                                    color(ctx.pal.textMain)
                                    width(26f)
                                    textAlignCenter()
                                    marginRight(8f)
                                }
                            }
                            Text {
                                attr {
                                    flex(1f)
                                    text("添加自选股")
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
                        // 夜间模式（三态循环：跟随系统 -> 深色 -> 浅色 -> 跟随系统）
                        View {
                            ref { ctx.menuThemeRef = it }
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
                                    width(26f)
                                    textAlignCenter()
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
                            ref { ctx.menuAiRef = it }
                            attr {
                                padding(14f)
                                paddingLeft(16f)
                                paddingRight(16f)
                                flexDirectionRow()
                                alignItemsCenter()
                            }
                            Text {
                                attr {
                                    text("⚙")
                                    fontSize(15f)
                                    color(ctx.pal.textMain)
                                    width(26f)
                                    textAlignCenter()
                                    marginRight(8f)
                                }
                            }
                            Text {
                                attr {
                                    flex(1f)
                                    text("AI 设置")
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
                        // 功能说明：重新观看引导
                        View {
                            ref { ctx.menuGuideRef = it }
                            attr {
                                paddingTop(14f)
                                paddingBottom(14f)
                                paddingLeft(16f)
                                paddingRight(16f)
                                flexDirectionRow()
                                alignItemsCenter()
                            }
                            Text {
                                attr {
                                    text("❓")
                                    fontSize(15f)
                                    color(ctx.pal.textMain)
                                    width(26f)
                                    textAlignCenter()
                                    marginRight(8f)
                                }
                            }
                            Text {
                                attr {
                                    flex(1f)
                                    text("功能说明")
                                    fontSize(15f)
                                    color(ctx.pal.textMain)
                                }
                            }
                            // 引导版本升级后给老用户一个非打扰的提示：菜单里点进来就能重看新内容
                            vif({ ctx.guideOutdated() }) {
                                Text {
                                    attr {
                                        text("有新内容")
                                        fontSize(10f)
                                        color(ctx.pal.accent)
                                        marginRight(6f)
                                    }
                                }
                            }
                            event {
                                click {
                                    ctx.showMenu = false
                                    ctx.replayGuide()
                                }
                            }
                        }
                    }
                }
            }

            // ---------- 内容区：自选股 Tab ----------
            vif({ ctx.currentTab == 0 }) {
                View {
                    attr {
                        flex(1f)
                        flexDirectionColumn()
                    }
                    // 分组 chips 固定在列表上方（不随列表滚动）
                    ctx.groupChipsRow().invoke(this)
                    // ---------- 就地功能提示：讲当前这一块怎么用，点「知道了」后不再出现 ----------
                    // 两条按顺序出现（先分组后排序），避免叠在一起把列表顶下去
                    vif({ ctx.tipGroupVisible }) {
                        featureTipBar(
                            ctx.pal,
                            "点 ＋ 新建分组；长按股票行可移动到分组，长按分组名可重命名或删除",
                            { ctx.dismissFeatureTip(FeatureTips.GROUP) }
                        ).invoke(this)
                    }
                    vif({ !ctx.tipGroupVisible && ctx.tipSortVisible }) {
                        featureTipBar(
                            ctx.pal,
                            "点表头可按价格 / 涨跌幅 / 名称排序，再点一次切换升降序；置顶项恒排最前",
                            { ctx.dismissFeatureTip(FeatureTips.SORT) }
                        ).invoke(this)
                    }
                    ctx.listContent().invoke(this)
                }
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
                    ctx.tabItem("自选股", 0) { ctx.tabSelfRef = it }.invoke(this)
                    ctx.tabItem("AI 问答", 1) { ctx.tabAiRef = it }.invoke(this)
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

            // ---------- 分组相关浮层（v1.9.30「留」层） ----------
            vif({ ctx.moveSheetQuote != null }) {
                ctx.moveSheet().invoke(this)
            }
            vif({ ctx.groupMenuName != null }) {
                ctx.groupMenu().invoke(this)
            }
            vif({ ctx.showGroupDialog }) {
                ctx.groupDialog().invoke(this)
            }
            vif({ ctx.pendingGroupDelete != null }) {
                ctx.deleteGroupConfirm().invoke(this)
            }
            vif({ ctx.showGroupSort }) {
                ctx.groupSortSheet().invoke(this)
            }

            // ---------- 首启引导：询问弹窗 ----------
            vif({ ctx.showGuidePrompt }) {
                View {
                    attr {
                        absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                        zIndex(160)
                        allCenter()
                        backgroundColor(ctx.pal.maskDim)
                    }
                    event { click { } }
                    View {
                        attr {
                            width(ctx.pagerData.pageViewWidth - 72f)
                            borderRadius(14f)
                            backgroundColor(ctx.pal.card)
                            padding(22f)
                            flexDirectionColumn()
                        }
                        event { click { } }
                        Text {
                            attr {
                                text("欢迎使用 AiStock")
                                fontSize(18f)
                                fontWeightBold()
                                color(ctx.pal.textMain)
                            }
                        }
                        Text {
                            attr {
                                text("花 1 分钟了解一下主要功能吧，也可随时跳过。")
                                fontSize(14f)
                                color(ctx.pal.textSub)
                                marginTop(10f)
                                lineHeight(20f)
                            }
                        }
                        View {
                            attr {
                                flexDirectionRow()
                                marginTop(22f)
                            }
                            View {
                                attr {
                                    flex(1f)
                                    height(40f)
                                    borderRadius(20f)
                                    allCenter()
                                    backgroundColor(ctx.pal.chipBg)
                                    marginRight(8f)
                                }
                                Text {
                                    attr {
                                        text("跳过")
                                        fontSize(14f)
                                        color(ctx.pal.textMain)
                                    }
                                }
                                event { click { ctx.skipGuide() } }
                            }
                            View {
                                attr {
                                    flex(1f)
                                    height(40f)
                                    borderRadius(20f)
                                    allCenter()
                                    backgroundColor(ctx.pal.accent)
                                    marginLeft(8f)
                                }
                                Text {
                                    attr {
                                        text("开始引导")
                                        fontSize(14f)
                                        color(ctx.pal.onAccent)
                                        fontWeightSemiBold()
                                    }
                                }
                                event { click { ctx.startGuide() } }
                            }
                        }
                    }
                }
            }

            // ---------- 首启引导：步骤遮罩（四块半透明遮罩拼出真透明挖孔 + 描边 + 说明卡） ----------
            // v1.9.21 架构（v1.9.20 两个缺陷的修复）：
            // 1) 容器 always-mounted 但【容器层不挂任何事件】，遮罩与事件全在 vif 内部；
            //    guideStep=0 时 vif 整棵子树移除，不残留任何视图拦截触摸。
            //    （v1.9.20 用 opacity(0) 隐藏整层 mask，但 opacity 不影响命中测试，
            //     引导结束后全屏不可交互）
            // 2) 高亮孔真正透明：上/下/左/右四块半透明遮罩拼出挖孔，孔内直接看到目标元素。
            //    （v1.9.20 用 pal.bgPage 不透明色块盖在 mask 上当"孔"，孔内全黑看不见目标）
            // 所有尺寸/坐标都在 attr lambda 内部读 observable，guideRect 变化时即时重算。
            View {
                attr {
                    absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                    zIndex(150)
                }
                vif({ ctx.guideStep > 0 }) {
                    // 四块遮罩全部用【纯边锚定】推导尺寸（不设 width/height）：
                    // 真机实测 absolutePosition 混合"left+right 锚定 + 显式 height"不渲染，
                    // 而纯锚定（询问弹窗/菜单背板）与"top/left + width/height"（描边框）均正常。
                    // 上遮罩（0 .. rect.y）
                    View {
                        attr {
                            absolutePosition(
                                top = 0f,
                                left = 0f,
                                right = 0f,
                                bottom = (ctx.guideScreenH() - ctx.guideRect.y).coerceAtLeast(0f)
                            )
                            backgroundColor(ctx.pal.maskDim)
                        }
                        event { click { } }
                    }
                    // 下遮罩（rect 底 .. 屏底）
                    View {
                        attr {
                            absolutePosition(
                                top = (ctx.guideRect.y + ctx.guideRect.height).coerceAtLeast(0f),
                                left = 0f,
                                right = 0f,
                                bottom = 0f
                            )
                            backgroundColor(ctx.pal.maskDim)
                        }
                        event { click { } }
                    }
                    // 左遮罩（孔左侧同一水平带）
                    View {
                        attr {
                            absolutePosition(
                                top = ctx.guideRect.y.coerceAtLeast(0f),
                                left = 0f,
                                right = (ctx.guideScreenW() - ctx.guideRect.x).coerceAtLeast(0f),
                                bottom = (ctx.guideScreenH() - ctx.guideRect.y - ctx.guideRect.height).coerceAtLeast(0f)
                            )
                            backgroundColor(ctx.pal.maskDim)
                        }
                        event { click { } }
                    }
                    // 右遮罩（孔右侧同一水平带）
                    View {
                        attr {
                            absolutePosition(
                                top = ctx.guideRect.y.coerceAtLeast(0f),
                                left = (ctx.guideRect.x + ctx.guideRect.width).coerceAtLeast(0f),
                                right = 0f,
                                bottom = (ctx.guideScreenH() - ctx.guideRect.y - ctx.guideRect.height).coerceAtLeast(0f)
                            )
                            backgroundColor(ctx.pal.maskDim)
                        }
                        event { click { } }
                    }
                    // 高亮描边
                    View {
                        attr {
                            absolutePosition(
                                top = (ctx.guideRect.y - 2f).coerceAtLeast(0f),
                                left = (ctx.guideRect.x - 2f).coerceAtLeast(0f)
                            )
                            width((ctx.guideRect.width + 4f).coerceAtLeast(0f))
                            height((ctx.guideRect.height + 4f).coerceAtLeast(0f))
                            borderRadius(6f)
                            border(Border(1.5f, BorderStyle.SOLID, ctx.pal.accent))
                        }
                    }
                    // 说明卡
                    ctx.guideCard().invoke(this)
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        // 菜单展示当前主题档位（宿主注入 pageData 的 themeMode；recreate 后自动取到最新值）
        menuMode = themeMode
        loadData()
        maybeShowGuidePrompt()
        reloadFeatureTips()
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
                        // ---------- 排序表头（点击切换排序键 / 升降序） ----------
                        vif({ ctx.quotes.isNotEmpty() }) {
                            ctx.listHeaderRow().invoke(this)
                        }
                        vfor({ ctx.quotes }) { item ->
                            ctx.quoteItem(item).invoke(this)
                        }
                        // 空态：整个自选为空 / 当前分组为空（两者引导文案不同）
                        vif({ !ctx.loading && ctx.errorMsg.isEmpty() && ctx.quotes.isEmpty() }) {
                            ctx.emptyStateHint().invoke(this)
                        }
                    }
                }
            }
        }
    }

    /** 底部任务栏单项 */
    private fun tabItem(label: String, index: Int, refSetter: (ViewRef<DivView>) -> Unit): ViewBuilder {
        val ctx = this
        return {
            View {
                ref { refSetter(it) }
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
        reloadGroups()
        val metas = Watchlist.stocks(sp)
        // 1. 缓存优先展示（接口临时失效时页面不空白）
        val cached = StockCache.loadQuotes(sp)
        if (cached.isNotEmpty()) {
            replaceQuotes(cached)
            loading = false
        }
        // 2. 拉取最新数据
        fetchQuotes(metas)
    }

    /** 拉取最新报价并写缓存（静默） */
    private fun fetchQuotes(metas: List<StockMeta>) {
        StockRepository.fetchQuotes(network, metas.map { it.code }) { list ->
            if (list.isNotEmpty()) {
                replaceQuotes(list)
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
                replaceQuotes(list)
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
        val byCode = allQuotes.associateBy { it.code }
        val metaCodes = metas.map { it.code }
        val base = metas.mapNotNull { byCode[it.code] } + allQuotes.filter { it.code !in metaCodes }
        if (base.size == allQuotes.size) {
            replaceQuotes(base)
        } else {
            applyFilterAndSort()
        }
    }

    // ==================== 排序（v1.9.28「探」层） ====================

    /**
     * 接收一批新行情：先存为全量快照，再按「当前分组过滤 + 置顶 + 排序键」重算展示列表。
     * 分组过滤放在最前，保证置顶项只在组内生效——否则切到某组还会看到别组的置顶股。
     */
    private fun replaceQuotes(list: List<StockQuote>) {
        allQuotes = list.toList()
        applyFilterAndSort()
    }

    /** 按 [activeGroup] 过滤 [allQuotes]，再套用置顶 + 排序，写回 quotes（vfor 数据源） */
    private fun applyFilterAndSort() {
        val groupOf = Watchlist.stocks(sp).associate { it.code to it.group }
        val scoped = when (activeGroup) {
            Watchlist.GROUP_ALL -> allQuotes
            "" -> allQuotes.filter { (groupOf[it.code] ?: "").isEmpty() }
            else -> allQuotes.filter { groupOf[it.code] == activeGroup }
        }
        val pinned = scoped.filter { Watchlist.isPinned(sp, it.code) }
        val rest = scoped.filter { !Watchlist.isPinned(sp, it.code) }
        val sortedRest = when (sortKey) {
            "price" -> order(rest) { it.price }
            "changePercent" -> order(rest) { it.changePercent }
            "name" -> order(rest) { it.name }
            else -> rest
        }
        // 先拷贝再 clear，避免遍历 observableList 时修改自身
        quotes.clear()
        quotes.addAll(pinned + sortedRest)
    }

    private fun <T : Comparable<T>> order(list: List<StockQuote>, selector: (StockQuote) -> T): List<StockQuote> {
        val asc = list.sortedBy(selector)
        return if (sortAsc) asc else asc.reversed()
    }

    /** 点击表头：同键切换升降序，换键则用该键的默认方向（名称升序、数值降序） */
    private fun toggleSort(key: String) {
        if (sortKey == key) {
            sortAsc = !sortAsc
        } else {
            sortKey = key
            sortAsc = (key == "name")
        }
        applyFilterAndSort()
    }

    // ==================== 分组（v1.9.30「留」层） ====================

    /** 重新读取分组名与各分组数量（新建/重命名/删除/移动归属后都要调） */
    private fun reloadGroups() {
        val names = Watchlist.groups(sp)
        groupNames.clear()
        groupNames.addAll(names)
        groupCounts = Watchlist.groupCounts(sp)
        // 当前选中组被删掉 → 回到「全部」，否则列表会一直空白
        if (activeGroup != Watchlist.GROUP_ALL && activeGroup.isNotEmpty() && activeGroup !in names) {
            activeGroup = Watchlist.GROUP_ALL
        }
    }

    /**
     * 切换分组：只改过滤条件 + 重算列表，不重建页面。
     * ⚠️ 不能叫 setActiveGroup —— 会和 activeGroup 属性的 JVM setter 签名冲突（Platform declaration clash）。
     */
    private fun switchGroup(group: String) {
        if (activeGroup == group) return
        activeGroup = group
        applyFilterAndSort()
    }

    /** 分组 chip 上显示的数量：[group] 为 [Watchlist.GROUP_ALL] 时是自选总数 */
    private fun countOf(group: String): Int {
        return when (group) {
            Watchlist.GROUP_ALL -> groupCounts.values.sum()
            else -> groupCounts[group] ?: 0
        }
    }

    /** 是否有未分组的股票（决定「未分组」chip 是否出现） */
    private fun hasUngrouped(): Boolean = (groupCounts[""] ?: 0) > 0

    /**
     * 分组 chips 行：全部 / 未分组 / 各分组 / ＋新建。
     * 用横向 Scroller（attr 里 flexDirectionRow 即横向滚动），固定在列表上方、不随列表滚动。
     */
    private fun groupChipsRow(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flexDirectionColumn()
                    backgroundColor(ctx.pal.card)
                }
                Scroller {
                    attr {
                        height(44f)
                        flexDirectionRow()
                        showScrollerIndicator(false)
                        paddingLeft(12f)
                        paddingRight(12f)
                    }
                    ctx.groupChip(Watchlist.GROUP_ALL, Watchlist.GROUP_ALL).invoke(this)
                    // 「未分组」是隐式分组：只有确实存在未分组股票时才出现，避免空 chip 占位
                    vif({ ctx.hasUngrouped() }) {
                        ctx.groupChip(Watchlist.GROUP_NONE_LABEL, "").invoke(this)
                    }
                    vfor({ ctx.groupNames }) { name ->
                        ctx.groupChip(name, name).invoke(this)
                    }
                    ctx.addGroupChip().invoke(this)
                }
                View {
                    attr {
                        height(1f)
                        backgroundColor(ctx.pal.divider)
                    }
                }
            }
        }
    }

    /**
     * 单个分组 chip。[filter] 是写入 [activeGroup] 的过滤值（全部=GROUP_ALL / 未分组="" / 组名）。
     * ⚠️ 选中态与数量都必须在 attr / text 内读取 observable：在 ViewBuilder 外层算好再闭包捕获，
     * 不会建立响应式依赖，切组或数量变化时 chip 不会重绘。
     * ⚠️ 热区必须显式 width/height：仅靠 padding 撑开的 View 真机命中区域约等于文字本身。
     */
    private fun groupChip(label: String, filter: String): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    height(30f)
                    paddingLeft(12f)
                    paddingRight(12f)
                    marginRight(8f)
                    marginTop(7f)
                    borderRadius(15f)
                    allCenter()
                    backgroundColor(
                        if (ctx.activeGroup == filter) ctx.pal.accent else ctx.pal.chipBg
                    )
                }
                Text {
                    attr {
                        val n = ctx.countOf(filter)
                        text(if (n > 0) "$label $n" else label)
                        fontSize(12f)
                        fontWeightSemiBold()
                        color(if (ctx.activeGroup == filter) ctx.pal.onAccent else ctx.pal.textMain)
                    }
                }
                event {
                    click { ctx.switchGroup(filter) }
                    // 长按弹「重命名 / 删除」；「全部」与「未分组」是隐式分组，无可管理项
                    longPress {
                        if (filter.isNotEmpty() && filter != Watchlist.GROUP_ALL) {
                            ctx.groupMenuName = filter
                        }
                    }
                }
            }
        }
    }

    /** chips 行尾部的「＋」：新建分组 */
    private fun addGroupChip(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    width(34f)
                    height(30f)
                    marginTop(7f)
                    borderRadius(15f)
                    allCenter()
                    backgroundColor(ctx.pal.chipBg)
                }
                Text {
                    attr {
                        text("＋")
                        fontSize(14f)
                        color(ctx.pal.textSub)
                    }
                }
                event {
                    click { ctx.openGroupDialog("") }
                }
            }
        }
    }

    // ---------------- 分组：浮层 ----------------

    /** 列表空态：区分「整个自选为空」与「当前分组为空」，给不同引导文案 */
    private fun emptyStateHint(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    paddingTop(60f)
                    paddingLeft(32f)
                    paddingRight(32f)
                    flexDirectionColumn()
                    allCenter()
                }
                Text {
                    attr {
                        val isAll = ctx.activeGroup == Watchlist.GROUP_ALL
                        text(if (isAll) "自选列表还是空的" else "「${ctx.groupLabel()}」里还没有股票")
                        fontSize(14f)
                        color(ctx.pal.textSub)
                        marginBottom(8f)
                    }
                }
                Text {
                    attr {
                        val isAll = ctx.activeGroup == Watchlist.GROUP_ALL
                        text(if (isAll) "点右上角菜单里的「添加自选股」开始" else "长按任意股票即可把它移到这个分组")
                        fontSize(12f)
                        color(ctx.pal.textSub)
                        textAlignCenter()
                    }
                }
            }
        }
    }

    /** 「移动到分组」浮层：列出 未分组 + 所有分组，当前归属项打勾 */
    private fun moveSheet(): ViewBuilder {
        val ctx = this
        return {
            val quote = ctx.moveSheetQuote
            if (quote != null) {
                Modal {
                    View {
                        attr {
                            flex(1f)
                            allCenter()
                            backgroundColor(ctx.pal.maskFull)
                        }
                        event {
                            click { ctx.moveSheetQuote = null }
                        }
                        View {
                            attr {
                                width(ctx.pagerData.pageViewWidth - 60f)
                                borderRadius(12f)
                                backgroundColor(ctx.pal.card)
                                paddingTop(18f)
                                paddingBottom(18f)
                                flexDirectionColumn()
                                overflow(true)
                            }
                            // 空点击消费：避免冒泡到遮罩把浮层关掉
                            event {
                                click { }
                            }
                            Text {
                                attr {
                                    text("移动到分组")
                                    fontSize(16f)
                                    fontWeightSemiBold()
                                    color(ctx.pal.textMain)
                                    marginLeft(20f)
                                    marginRight(20f)
                                    marginBottom(4f)
                                }
                            }
                            Text {
                                attr {
                                    text(quote.name)
                                    fontSize(12f)
                                    color(ctx.pal.textSub)
                                    marginLeft(20f)
                                    marginRight(20f)
                                    marginBottom(10f)
                                }
                            }
                            Scroller {
                                attr {
                                    // 最多露 7 行，再多就滚动，避免浮层高于屏幕
                                    height((1 + ctx.groupNames.size).coerceAtMost(7) * 46f)
                                    showScrollerIndicator(false)
                                }
                                ctx.moveTargetRow(quote, Watchlist.GROUP_NONE_LABEL, "").invoke(this)
                                vfor({ ctx.groupNames }) { name ->
                                    ctx.moveTargetRow(quote, name, name).invoke(this)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 「移动到分组」浮层里的单个选项行 */
    private fun moveTargetRow(quote: StockQuote, label: String, filter: String): ViewBuilder {
        val ctx = this
        // 弹出浮层时构建一次，用于打勾；SP 无响应式，靠打开时重建保证正确
        val current = ctx.currentGroupOf(quote.code)
        return {
            View {
                attr {
                    height(46f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(20f)
                    paddingRight(20f)
                }
                Text {
                    attr {
                        flex(1f)
                        text(label)
                        fontSize(14f)
                        color(ctx.pal.textMain)
                    }
                }
                if (current == filter) {
                    Text {
                        attr {
                            text("✓")
                            fontSize(15f)
                            fontWeightSemiBold()
                            color(ctx.pal.accent)
                        }
                    }
                }
                event {
                    click { ctx.doMoveToGroup(quote, filter) }
                }
            }
        }
    }

    /** 新建 / 重命名分组弹窗（[groupDialogRenameFrom] 空串表示新建） */
    private fun groupDialog(): ViewBuilder {
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
                        click { ctx.showGroupDialog = false }
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
                                text(if (ctx.groupDialogRenameFrom.isEmpty()) "新建分组" else "重命名分组")
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
                                ref { ctx.groupInputRef = it }
                                attr {
                                    flex(1f)
                                    height(40f)   // 显式高度：Kuikly Input 无 height 时无可点击区域
                                    fontSize(14f)
                                    color(ctx.pal.textMain)
                                    placeholder("如：持仓 / 观察 / 题材")
                                    placeholderColor(ctx.pal.textSub)
                                    maxTextLength(Watchlist.MAX_GROUP_NAME)
                                }
                                event {
                                    textDidChange { ctx.groupInput = it.text }
                                }
                            }
                        }
                        Text {
                            attr {
                                text("分组名最多 ${Watchlist.MAX_GROUP_NAME} 个字，最多 ${Watchlist.MAX_GROUPS} 个分组")
                                fontSize(11f)
                                color(ctx.pal.textSub)
                                marginTop(8f)
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
                                    click { ctx.showGroupDialog = false }
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
                                        text(if (ctx.groupDialogRenameFrom.isEmpty()) "新建" else "保存")
                                        fontSize(14f)
                                        color(ctx.pal.onAccent)
                                        fontWeightSemiBold()
                                    }
                                }
                                event {
                                    click { ctx.confirmGroupDialog() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 分组 chip 长按菜单：重命名 / 删除 */
    private fun groupMenu(): ViewBuilder {
        val ctx = this
        return {
            val name = ctx.groupMenuName
            if (name != null) {
                Modal {
                    View {
                        attr {
                            flex(1f)
                            allCenter()
                            backgroundColor(ctx.pal.maskFull)
                        }
                        event {
                            click { ctx.groupMenuName = null }
                        }
                        View {
                            attr {
                                width(ctx.pagerData.pageViewWidth - 110f)
                                borderRadius(12f)
                                backgroundColor(ctx.pal.card)
                                paddingTop(6f)
                                paddingBottom(6f)
                                flexDirectionColumn()
                                overflow(true)
                            }
                            event {
                                click { }
                            }
                            Text {
                                attr {
                                    text("分组「$name」")
                                    fontSize(13f)
                                    color(ctx.pal.textSub)
                                    marginLeft(20f)
                                    marginRight(20f)
                                    marginTop(12f)
                                    marginBottom(8f)
                                }
                            }
                            ctx.groupMenuRow("重命名") { ctx.openGroupDialog(name) }.invoke(this)
                            // 分组排序：至少 2 个分组才有排序意义（v1.9.33）
                            if (ctx.groupNames.size >= 2) {
                                ctx.groupMenuRow("分组排序") {
                                    ctx.groupMenuName = null
                                    ctx.showGroupSort = true
                                }.invoke(this)
                            }
                            // ⚠️ 选「删除」时必须先关掉菜单：否则菜单与二次确认同时存在，
                            // 确认框关闭后会残留一个指向已删分组的菜单
                            ctx.groupMenuRow("删除", danger = true) {
                                ctx.groupMenuName = null
                                ctx.pendingGroupDelete = name
                            }.invoke(this)
                        }
                    }
                }
            }
        }
    }

    /**
     * 分组长按菜单里的单行。
     * ⚠️ danger 用 [ThemePalette.up]（红）而不是 down —— 本项目遵循 A 股涨红跌绿，
     * down 是绿色，拿来标"删除"会误导。
     */
    private fun groupMenuRow(label: String, danger: Boolean = false, onClick: () -> Unit): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    height(48f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(20f)
                    paddingRight(20f)
                }
                Text {
                    attr {
                        flex(1f)
                        text(label)
                        fontSize(15f)
                        color(if (danger) ctx.pal.up else ctx.pal.textMain)
                    }
                }
                event {
                    click { onClick() }
                }
            }
        }
    }

    /** 分组排序里点 ↑↓：改持久化顺序，下一帧刷新 chips 行与浮层行 */
    private fun moveSortGroup(name: String, delta: Int) {
        if (!Watchlist.moveGroup(sp, name, delta)) return
        // ⚠️ reloadGroups 会 clear+addAll groupNames；排序浮层的 vfor 正在遍历它，
        // 必须延迟到下一帧再重建，否则「遍历中修改列表」会崩溃
        setTimeout(16) { reloadGroups() }
    }

    /** 分组排序浮层：列出所有分组，每行 ↑/↓ 调整显示顺序（v1.9.33） */
    private fun groupSortSheet(): ViewBuilder {
        val ctx = this
        return {
            Modal {
                View {
                    attr {
                        flex(1f)
                        allCenter()
                        backgroundColor(ctx.pal.maskFull)
                    }
                    event { click { ctx.showGroupSort = false } }
                    View {
                        attr {
                            width(ctx.pagerData.pageViewWidth - 96f)
                            borderRadius(14f)
                            backgroundColor(ctx.pal.card)
                            paddingTop(16f)
                            paddingBottom(10f)
                            flexDirectionColumn()
                            overflow(true)
                        }
                        event { click { } }
                        Text {
                            attr {
                                text("分组排序")
                                fontSize(16f)
                                fontWeightSemiBold()
                                color(ctx.pal.textMain)
                                marginLeft(20f)
                                marginRight(20f)
                            }
                        }
                        Text {
                            attr {
                                text("用右侧箭头调整分组在列表里的显示顺序")
                                fontSize(12f)
                                color(ctx.pal.textSub)
                                marginLeft(20f)
                                marginRight(20f)
                                marginTop(4f)
                                marginBottom(10f)
                            }
                        }
                        // 分组行（vforIndex 拿到 index/count 判断首尾禁用）。
                        // 组多时高度封顶并纵向滚动，避免卡片溢出屏幕（12 组 × 48dp = 576dp）。
                        Scroller {
                            attr {
                                height((ctx.groupNames.size * 48f).coerceAtMost(336f))
                                showScrollerIndicator(false)
                                flexDirectionColumn()
                            }
                            vforIndex({ ctx.groupNames }) { name, index, count ->
                                ctx.groupSortRow(name, index, count).invoke(this)
                            }
                        }
                        // 完成
                        View {
                            attr {
                                height(44f)
                                borderRadius(22f)
                                allCenter()
                                backgroundColor(ctx.pal.accent)
                                marginLeft(20f)
                                marginRight(20f)
                                marginTop(10f)
                            }
                            Text {
                                attr {
                                    text("完成")
                                    fontSize(14f)
                                    fontWeightSemiBold()
                                    color(ctx.pal.onAccent)
                                }
                            }
                            event { click { ctx.showGroupSort = false } }
                        }
                    }
                }
            }
        }
    }

    /** 分组排序浮层的单行：名称 + 数量 + ↑↓ */
    private fun groupSortRow(name: String, index: Int, count: Int): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    height(48f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(20f)
                    paddingRight(12f)
                }
                Text {
                    attr {
                        flex(1f)
                        text(name)
                        fontSize(15f)
                        color(ctx.pal.textMain)
                    }
                }
                Text {
                    attr {
                        text("${ctx.countOf(name)} 只")
                        fontSize(12f)
                        color(ctx.pal.textSub)
                        marginRight(14f)
                    }
                }
                ctx.sortArrow("↑", index > 0) { ctx.moveSortGroup(name, -1) }.invoke(this)
                ctx.sortArrow("↓", index < count - 1) { ctx.moveSortGroup(name, 1) }.invoke(this)
            }
        }
    }

    /** 排序浮层里的单个方向箭头按钮；禁用时灰色且点击不响应 */
    private fun sortArrow(glyph: String, enabled: Boolean, onClick: () -> Unit): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    width(40f)
                    height(40f)
                    borderRadius(8f)
                    allCenter()
                    marginLeft(6f)
                    backgroundColor(ctx.pal.chipBg)
                }
                Text {
                    attr {
                        text(glyph)
                        fontSize(18f)
                        color(if (enabled) ctx.pal.textMain else ctx.pal.textSub)
                    }
                }
                event {
                    click { if (enabled) onClick() }
                }
            }
        }
    }

    /** 删除分组二次确认（组内股票只回到「未分组」，不会删自选） */
    private fun deleteGroupConfirm(): ViewBuilder {
        val ctx = this
        return {
            val name = ctx.pendingGroupDelete
            if (name != null) {
                Modal {
                    View {
                        attr {
                            flex(1f)
                            allCenter()
                            backgroundColor(ctx.pal.maskFull)
                        }
                        event {
                            click { ctx.pendingGroupDelete = null }
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
                                    text("删除分组")
                                    fontSize(16f)
                                    fontWeightSemiBold()
                                    color(ctx.pal.textMain)
                                    marginBottom(10f)
                                }
                            }
                            Text {
                                attr {
                                    text("删除「$name」后，组内 ${ctx.countOf(name)} 只股票会回到「未分组」，仍留在自选里。")
                                    fontSize(13f)
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
                                        click { ctx.pendingGroupDelete = null }
                                    }
                                }
                                View {
                                    attr {
                                        flex(1f)
                                        height(40f)
                                        borderRadius(20f)
                                        allCenter()
                                        // 危险操作用 up（红）——本项目 A 股配色：up=红
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
                                        click { ctx.deleteGroup(name) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------- 分组：操作逻辑 ----------------

    /** 某只股票当前所属分组名（空串 = 未分组） */
    private fun currentGroupOf(code: String): String {
        return Watchlist.stocks(sp).firstOrNull { it.code == code }?.group ?: ""
    }

    /** 当前分组名的展示文案（「全部」虚拟分组原样返回） */
    private fun groupLabel(): String {
        return if (activeGroup.isEmpty()) Watchlist.GROUP_NONE_LABEL else activeGroup
    }

    /** 打开新建（[renameFrom] 空串）或重命名分组弹窗 */
    private fun openGroupDialog(renameFrom: String) {
        groupMenuName = null
        groupDialogRenameFrom = renameFrom
        groupInput = renameFrom
        showGroupDialog = true
        // 预填旧名：Input 的显示值要显式 setText（attr 里的初始 text 不会同步）
        setTimeout(0) { groupInputRef?.view?.setText(renameFrom) }
    }

    /** 提交新建 / 重命名 */
    private fun confirmGroupDialog() {
        val clean = Watchlist.normalizeGroupName(groupInput)
        if (clean.isEmpty()) {
            bridgeModule.toast("分组名不能为空，且不能叫「全部」或「未分组」")
            return
        }
        val renaming = groupDialogRenameFrom.isNotEmpty()
        val ok = if (renaming) {
            Watchlist.renameGroup(sp, groupDialogRenameFrom, clean)
        } else {
            Watchlist.addGroup(sp, clean)
        }
        if (!ok) {
            bridgeModule.toast(
                when {
                    Watchlist.groups(sp).size >= Watchlist.MAX_GROUPS && !renaming ->
                        "最多只能有 ${Watchlist.MAX_GROUPS} 个分组"
                    Watchlist.groups(sp).any { it == clean } -> "已经有同名分组了"
                    else -> "操作失败，请重试"
                }
            )
            return
        }
        bridgeModule.toast(if (renaming) "已重命名为「$clean」" else "已新建分组「$clean」")
        showGroupDialog = false
        // 当前正在该组内 → 跟随改名
        if (renaming && activeGroup == groupDialogRenameFrom) activeGroup = clean
        // 关闭浮层后再刷新 chips，避免 vfor 遍历中 clear+addAll
        setTimeout(50) {
            reloadGroups()
            applyFilterAndSort()
        }
    }

    /** 把股票移动到目标分组（[filter] 空串 = 未分组） */
    private fun doMoveToGroup(quote: StockQuote, filter: String) {
        val changed = Watchlist.moveToGroup(sp, quote.code, filter)
        moveSheetQuote = null
        if (changed) {
            bridgeModule.toast(
                if (filter.isEmpty()) "${quote.name} 已移到「${Watchlist.GROUP_NONE_LABEL}」"
                else "${quote.name} 已移到「$filter」"
            )
        }
        // 先关浮层再刷新：vfor 正在遍历 groupNames 时 clear+addAll 会崩
        setTimeout(50) {
            reloadGroups()
            applyFilterAndSort()
        }
    }

    /** 删除分组：只解散分组，组内股票回「未分组」 */
    private fun deleteGroup(name: String) {
        pendingGroupDelete = null
        groupMenuName = null
        if (Watchlist.removeGroup(sp, name)) {
            bridgeModule.toast("已删除分组「$name」")
        }
        if (activeGroup == name) activeGroup = Watchlist.GROUP_ALL
        setTimeout(50) {
            reloadGroups()
            applyFilterAndSort()
        }
    }

    /** 排序表头行：列宽与行情行严格一致（flex + 80f + 10f + 110f） */
    private fun listHeaderRow(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    height(34f)
                    paddingLeft(16f)
                    paddingRight(16f)
                    backgroundColor(ctx.pal.card)
                }
                ctx.sortCell("名称/代码", "name", flex = true).invoke(this)
                ctx.sortCell("最新价", "price", width = 80f, end = true).invoke(this)
                View {
                    attr {
                        width(10f)
                        height(1f)
                    }
                }
                ctx.sortCell("涨跌幅", "changePercent", width = 110f, center = true).invoke(this)
            }
            View {
                attr {
                    height(1f)
                    backgroundColor(ctx.pal.divider)
                }
            }
        }
    }

    /**
     * 单个可点击表头单元。
     * 注意：Text 自身不接收手势，事件挂在外层 View 上，且必须给显式 height 才有热区。
     */
    private fun sortCell(
        label: String,
        key: String,
        width: Float = 0f,
        flex: Boolean = false,
        end: Boolean = false,
        center: Boolean = false
    ): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    if (flex) flex(1f) else width(width)
                    height(34f)
                    flexDirectionRow()
                    alignItemsCenter()
                    if (center) {
                        justifyContentCenter()
                    } else if (end) {
                        justifyContentFlexEnd()
                    } else {
                        justifyContentFlexStart()
                    }
                }
                Text {
                    attr {
                        val active = ctx.sortKey == key
                        text(if (active) "$label ${if (ctx.sortAsc) "↑" else "↓"}" else label)
                        fontSize(12f)
                        color(if (active) ctx.pal.accent else ctx.pal.textSub)
                        if (center) textAlignCenter() else if (end) textAlignRight() else textAlignLeft()
                    }
                }
                event {
                    click { ctx.toggleSort(key) }
                }
            }
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
                // 正在某个分组里添加 → 直接归入该组，省得再加一次「移动分组」
                val intoGroup = activeGroup
                if (intoGroup.isNotEmpty() && intoGroup != Watchlist.GROUP_ALL) {
                    Watchlist.moveToGroup(sp, meta.code, intoGroup)
                    bridgeModule.toast("已添加 ${meta.name} 到「$intoGroup」")
                } else {
                    bridgeModule.toast("已添加 ${meta.name}")
                }
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
                    // 问 AI：带上该股上下文进 AI 问答页
                    View {
                        attr {
                            flex(1f)
                            allCenter()
                            backgroundColor(Color(0xFF378ADD))
                        }
                        Text {
                            attr {
                                text("问 AI")
                                fontSize(13f)
                                color(ctx.pal.onAccent)
                                fontWeightSemiBold()
                            }
                        }
                        event {
                            click { ctx.onSwipeAskAi(quote) }
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
                        // 长按：呼出「移动到分组」浮层。
                        // 顺手刷新手势结束时间戳，复用 click 的防抖窗口挡住长按抬手后的 click，
                        // 否则会「先弹浮层、紧接着又跳进详情页」
                        longPress {
                            val st = ctx.ensureSwipeState(quote.code)
                            st.lastGestureEndTime = ctx.bridgeModule.currentTimeStamp()
                            if (st.offset < 0f) st.offset = 0f
                            ctx.moveSheetQuote = quote
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

    /** 左滑操作条：问 AI（带上该股上下文进入 AI 问答页，并复位该行） */
    private fun onSwipeAskAi(quote: StockQuote) {
        swipeStates[quote.code]?.offset = 0f
        val pageData = JSONObject().apply {
            put("code", quote.code)
            put("name", quote.name)
            put("autoSend", "0") // 只切到该股会话，等用户点快捷问句或自行输入
        }
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("ai_chat", pageData)
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

    // ==================== 就地功能提示（v1.9.31） ====================

    /**
     * 读取就地提示的显示态：没看过才显示。
     * 首次安装的「新功能」提示不依赖首启引导是否已读 —— 两者是独立的一次性标记。
     */
    private fun reloadFeatureTips() {
        tipGroupVisible = !FeatureTips.isSeen(sp, FeatureTips.GROUP)
        tipSortVisible = !FeatureTips.isSeen(sp, FeatureTips.SORT)
    }

    /** 点「知道了」：写 SP 标记并立即收起（[reloadFeatureTips] 会顺带把下一条推上来） */
    private fun dismissFeatureTip(key: String) {
        FeatureTips.markSeen(sp, key)
        reloadFeatureTips()
    }

    // ==================== 首启引导逻辑 ====================

    /** 首启检查：无 guide_seen 标记时延迟弹出询问弹窗（仅一次） */
    private fun maybeShowGuidePrompt() {
        if (guidePromptChecked) return
        guidePromptChecked = true
        if (sp.getItem(KEY_GUIDE_SEEN).isBlank()) {
            setTimeout(400) { showGuidePrompt = true }
        }
    }

    /** 询问弹窗「开始引导」 */
    private fun startGuide() {
        showGuidePrompt = false
        gotoGuideStep(1)
    }

    /** 菜单「功能说明」重看：先复位回自选股 Tab 再进入步骤 1；顺带把就地提示也复位，方便重新体验 */
    private fun replayGuide() {
        guideStep = 0
        showGuidePrompt = false
        showMenu = false
        currentTab = 0
        FeatureTips.resetAll(sp)
        reloadFeatureTips()
        setTimeout(100) { gotoGuideStep(1) }
    }

    /**
     * 进入某一步：设置文案、按需打开菜单、设置高亮孔矩形。
     *
     * v1.9.19：所有步骤统一用 guideFallbackRect，不再走 convertFrame。
     * 实测 vfor 列表 layer 内 / 菜单面板内部 view.frame 在某些情况下会被
     * 错算到列表中间或菜单中部，convertFrame + view.frame 补偿并不稳定。
     * 各步骤目标都在页面固定区域（边缘位置 / 固定坐标的菜单 panel），
     * 直接按布局规则算兜底坐标比 convertFrame 简洁可靠。
     */
    private fun gotoGuideStep(step: Int) {
        guideStep = step
        guideTitle = guideTitleFor(step)
        guideDesc = guideDescFor(step)
        // 同步给一个 fallback 矩形，立即让遮罩形态正确（避免初始 rect=(0,0,0,0) 全屏暗死）
        guideRect = guideFallbackRect(step)
        // 步骤 6 起在功能菜单面板上继续高亮，需先把菜单打开
        if (step == 6) showMenu = true
        // 80/200ms 后再校准一次：root frame 在 layout 完成后取值可能变化
        // （早期 frame 还未稳定；fallback 重新取一次最新 root 尺寸）
        setTimeout(80) { applyGuideRect(step) }
        setTimeout(200) { applyGuideRect(step) }
        // 菜单面板首次打开 layout 完成偏慢（vif 触发子视图创建），再补一次兜底
        if (step in 7..10) setTimeout(500) { applyGuideRect(step) }
    }

    /** 引导根容器有效宽度（布局完成前 fallback 到 pageView 尺寸） */
    private fun guideScreenW(): Float =
        guideRootRef?.frame?.width?.takeIf { it > 0f } ?: pagerData.pageViewWidth

    /** 引导根容器有效高度（布局完成前 fallback 到 pageView 尺寸） */
    private fun guideScreenH(): Float =
        guideRootRef?.frame?.height?.takeIf { it > 0f } ?: pagerData.pageViewHeight

    /**
     * 各步骤目标矩形，全部按页面布局规则手工计算。
     *
     * 步骤 1-6 是页面固定边缘位置（底栏 Tab / 分组条 / 列表区 / 顶栏按钮）：
     * - 顶栏高度 = 56f + statusBarHeight（含 sb），故内容区顶边 = sb + 56f
     * - 分组条 chips 行高 = 44f + 1f 分隔线 → 列表区顶边 = sb + 101f
     * - 底栏 Tab 高 = 54f（位于 root.frame 最底部）
     *
     * 步骤 7-10 是菜单面板内固定位置：
     * - 菜单 panel absolutePosition(top=56f + statusBarHeight, left=0, right=0) 全宽，paddingTop/Bottom=6f
     * - 每项 49dp 高，分隔线 1dp，步进 50dp
     * - 项行占满整卡宽（x=0，width=w）
     *
     * v1.9.21 布局规则（dp 密度无关，跨分辨率稳定）：
     * - 底部 Tab 栏高度 54dp
     * - 顶栏高度 56dp + statusBar
     * - 顶栏 paddingLeft/Right=16，「刷新」marginRight=10，「☰」paddingRight=4
     * - 菜单面板 top = 56 + sb（无额外偏移；v1.9.20 的 +19dp 是实测误判，已移除）
     * - 菜单白卡 paddingTop/Bottom=6
     * - 每项 = padding(14) + text 15sp 行高 21 + padding(14) = 49dp，分隔线 1dp，步进 50dp
     *
     * 所有公式用 w/h/sb 相对量，dp 值由代码布局规则定义（所有设备一致）。
     */
    private fun guideFallbackRect(step: Int): Frame {
        val w = guideScreenW()
        val h = guideScreenH()
        val sb = pagerData.statusBarHeight
        val itemH = 49f
        val itemStep = 50f  // item 49dp（padding14+文字行高21+padding14）+ divider 1dp
        // 菜单项 y = panel top (sb+56) + 白卡 paddingTop 6 + 项序号 * 50
        val menuY = sb + 62f
        // 顶栏底边 = 内容区顶边（顶栏 height 已含 sb，所以直接 sb + 56）
        val contentTop = sb + 56f
        // 列表区顶边 = 内容区顶边 + chips 行(44) + 分隔线(1)
        val listTop = contentTop + 45f
        return when (step) {
            // 底部 Tab 栏：自选股(左半) / AI问答(右半)
            1 -> Frame(0f, (h - 54f).coerceAtLeast(0f), w / 2f, 54f)
            // 分组条 chips 行（横向 Scroller + 底部分隔线）
            2 -> Frame(0f, contentTop, w, 45f)
            // 列表区（排序表头 + 股票行）。
            // ⚠️ 高度取 236f 而不是刚好 178f：孔从 chips 下沿起算，而「就地功能提示条」会把列表整体下推
            // （每条 ≈63dp），首启时提示条在场。放宽高度让「提示条在/不在」两种情况下都能框住列表本体，
            // 代价只是多框两行股票，不影响理解。
            3 -> Frame(0f, listTop, w, 236f)
            4 -> Frame(w / 2f, (h - 54f).coerceAtLeast(0f), w / 2f, 54f)
            // 顶栏「刷新」：paddingLeft(6)+text 14sp+paddingRight(6)+marginRight(10)
            // 实测起点约 w-100（取决于"刷新"实际字宽，约 30-32dp）
            5 -> Frame((w - 100f).coerceAtLeast(0f), sb + 15f, 50f, 26f)
            // 顶栏「☰」：paddingLeft(8)+"☰" 20sp+paddingRight(4)≈38dp，距右 4dp
            6 -> Frame((w - 42f).coerceAtLeast(0f), sb + 9f, 38f, 38f)
            // 菜单面板 4 项：全宽行（菜单卡 left=0/right=0，项行占满整卡宽）
            7 -> Frame(0f, menuY, w, itemH)
            8 -> Frame(0f, menuY + itemStep, w, itemH)
            9 -> Frame(0f, menuY + 2f * itemStep, w, itemH)
            10 -> Frame(0f, menuY + 3f * itemStep, w, itemH)
            else -> Frame(16f, sb + 80f, w - 32f, 120f)
        }
    }

    /**
     * 计算当前步骤高亮孔矩形（v1.9.20 一律走 guideFallbackRect）。
     *
     * 不用 convertFrame（v1.9.19 已证明不可靠），不再单独判越界（fallback 公式已
     * 保证矩形在合理范围内）。
     */
    private fun applyGuideRect(step: Int) {
        guideRect = guideFallbackRect(step)
    }

    /** 「下一步」：最后一步结束引导，否则进入下一步 */
    private fun guideNext() {
        if (guideStep >= GUIDE_LAST_STEP) finishGuide() else gotoGuideStep(guideStep + 1)
    }

    /** 「跳过」（询问弹窗或任意步骤）：结束并记录已看过 */
    private fun skipGuide() = finishGuide()

    /** 结束引导：收起遮罩与菜单，写入已看过标记与当前引导版本号 */
    private fun finishGuide() {
        guideStep = 0
        showGuidePrompt = false
        showMenu = false
        sp.setItem(KEY_GUIDE_SEEN, "1")
        sp.setItem(KEY_GUIDE_VER, GUIDE_VER)
    }

    /** 引导是否已落后于当前版本（老用户）→ 菜单「功能说明」右侧显示「有新内容」 */
    private fun guideOutdated(): Boolean = sp.getItem(KEY_GUIDE_VER) != GUIDE_VER

    private fun guideTitleFor(step: Int): String = when (step) {
        1 -> "自选股"
        2 -> "自选分组"
        3 -> "长按 / 排序"
        4 -> "AI 问答"
        5 -> "刷新行情"
        6 -> "功能菜单"
        7 -> "添加自选股"
        8 -> if (isNightMode()) "日间模式" else "夜间模式"
        9 -> "AI 设置"
        10 -> "功能说明"
        else -> ""
    }

    private fun guideDescFor(step: Int): String = when (step) {
        1 -> "这里展示你关注的股票：最新价、涨跌额与涨跌幅一目了然，左滑单行可置顶或删除。"
        2 -> "分组条把自选分成「持仓」「观察」等几类：点右侧 ＋ 新建分组，点组名只看该组，" +
            "长按组名可重命名或删除（删除分组不会删掉股票，只会让它们回到「未分组」）。"
        3 -> "长按任意股票行，可把它移动到某个分组；点表头可按价格 / 涨跌幅 / 名称排序，" +
            "再点一次切换升降序，置顶的股票无论怎么排都在最前面。"
        4 -> "切到「AI 问答」，可与 AI 多轮讨论任意股票，回复会附带实时行情卡片。" +
            "长按任意消息可复制、引用追问、重新生成或删除。"
        5 -> "点右上角「刷新」，或直接下拉列表，即可手动拉取最新行情。"
        6 -> "这是功能菜单 ☰，添加自选、外观切换、AI 设置与使用帮助都从这里进入。"
        7 -> "输入股票代码即可添加自选，例如 sh600519 贵州茅台；在某个分组里添加会自动归入该组。"
        8 -> "一键切换深色 / 浅色外观，右侧小字显示当前档位。"
        9 -> "在此配置 AI 服务（Base URL / Key / 模型），行情分析与问答共用。"
        10 -> "以后想重温这段引导、或再看一次各页面的功能提示，随时点这里即可。点「完成」结束引导。"
        else -> ""
    }

    /**
     * 估算引导卡描述文本在给定卡宽下的行数。
     *
     * 13sp 字号下：全角（中文/标点「」＋）按 1 字宽 ≈ 13dp 计，ASCII（空格、`/`、`sh600519`）按 0.55 字宽计。
     * 只用于卡片「放孔上方还是下方」的定位判断与 minHeight 下限，**不用于裁剪**
     * —— 卡片实际高度由内容撑开（见 [guideCard] 用 minHeight 而非 height）。
     */
    private fun guideDescLines(desc: String, cardW: Float): Int {
        if (desc.isBlank()) return 1
        var weight = 0f
        for (ch in desc) weight += if (ch.code < 128) 0.55f else 1f
        val perLine = ((cardW - 36f) / 13f).coerceAtLeast(4f)
        val n = weight / perLine
        val lines = n.toInt() + if (n > n.toInt() + 1e-4f) 1 else 0
        return lines.coerceIn(1, 8)
    }

    /** 引导说明卡（定位在目标孔下方，孔位于屏幕下半部分时改放上方） */
    private fun guideCard(): ViewBuilder {
        val ctx = this
        return {
            // 说明卡紧跟高亮孔：孔下方放得下就放孔下方，否则放孔上方。
            // 用根容器实际尺寸做基准（pageViewHeight 可能含状态栏导致基准偏移）。
            //
            // ⚠️ v1.9.32：**不能给卡片设固定 height**。步骤 2/3 的 desc 有 60~80 字，13sp 下要占 3~4 行，
            // 固定 180f 会把 desc 末行和「下一步」按钮一起裁掉（v1.9.31 的「引导 2、3 显示不全」）。
            // 改为 minHeight（≈ 非 desc 部分 122f + 行数×20f）：高度随内容撑开，估算值只当下限与定位依据。
            // 关键：cardTop/rectY/rectH 等求值必须在 attr lambda 内部读 observable，
            // 否则和 mask 子 view 一样的"lambda 外 val 中断响应式订阅"问题。
            View {
                attr {
                    val rectY = ctx.guideRect.y.coerceAtLeast(0f)
                    val rectH = ctx.guideRect.height.coerceAtLeast(0f)
                    val screenH = ctx.guideScreenH()
                    val cardW = ctx.guideScreenW() - 32f
                    // 非 desc 部分：padding(18×2) + title 行 22 + desc 上间距 8 + 按钮行上间距 16 + 按钮 40
                    val cardH = 122f + ctx.guideDescLines(ctx.guideDesc, cardW) * 20f
                    val below = rectY + rectH + cardH + 12f <= screenH
                    val cardTop = if (below) {
                        rectY + rectH + 12f
                    } else {
                        (rectY - cardH - 12f).coerceAtLeast(8f)
                    // 再兜一次屏幕下边界：孔太靠下时避免卡片尾巴出屏
                    }.coerceAtMost((screenH - cardH - 8f).coerceAtLeast(8f))
                    absolutePosition(
                        top = cardTop,
                        left = 16f
                    )
                    width(cardW)
                    minHeight(cardH)
                    zIndex(150)
                    borderRadius(14f)
                    backgroundColor(ctx.pal.card)
                    padding(18f)
                    flexDirectionColumn()
                }
                event { click { } }
                // 标题 + 步骤
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    Text {
                        attr {
                            flex(1f)
                            text(ctx.guideTitle)
                            fontSize(16f)
                            fontWeightSemiBold()
                            color(ctx.pal.textMain)
                        }
                    }
                    Text {
                        attr {
                            text("${ctx.guideStep}/$GUIDE_LAST_STEP")
                            fontSize(12f)
                            color(ctx.pal.textSub)
                        }
                    }
                }
                // 描述
                Text {
                    attr {
                        text(ctx.guideDesc)
                        fontSize(13f)
                        color(ctx.pal.textSub)
                        marginTop(8f)
                        lineHeight(20f)
                    }
                }
                // 按钮行
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        marginTop(16f)
                    }
                    View {
                        attr {
                            paddingTop(8f)
                            paddingBottom(8f)
                            paddingLeft(4f)
                            paddingRight(4f)
                        }
                        Text {
                            attr {
                                text("跳过")
                                fontSize(13f)
                                color(ctx.pal.textSub)
                            }
                        }
                        event { click { ctx.skipGuide() } }
                    }
                    View {
                        attr {
                            flex(1f)
                            height(40f)
                            borderRadius(20f)
                            allCenter()
                            backgroundColor(ctx.pal.accent)
                            marginLeft(12f)
                        }
                        Text {
                            attr {
                                text(if (ctx.guideStep >= GUIDE_LAST_STEP) "完成" else "下一步")
                                fontSize(14f)
                                color(ctx.pal.onAccent)
                                fontWeightSemiBold()
                            }
                        }
                        event { click { ctx.guideNext() } }
                    }
                }
            }
        }
    }

    private companion object {
        /** 首启引导是否已看过（SP key；值为 "1" 表示已看过） */
        private const val KEY_GUIDE_SEEN = "guide_intro_seen"

        /** 引导版本号：内容有新增时就升一档，老用户在菜单「功能说明」上会看到「有新内容」角标 */
        private const val KEY_GUIDE_VER = "guide_intro_ver"
        private const val GUIDE_VER = "2"

        /** 引导总步数（1..GUIDE_LAST_STEP）；卡片右侧「N/总步数」与「完成」判定都用它 */
        private const val GUIDE_LAST_STEP = 10
    }
}
