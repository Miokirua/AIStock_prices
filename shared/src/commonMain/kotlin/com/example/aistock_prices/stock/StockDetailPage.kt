package com.example.aistock_prices.stock

import com.example.aistock_prices.RouterNavBar
import com.example.aistock_prices.base.BasePager
import com.example.aistock_prices.base.bridgeModule
import com.example.aistock_prices.base.setTimeout
import com.example.aistock_prices.stock.ai.AiAnalysisResult
import com.example.aistock_prices.stock.ai.AiAnalysisService
import com.example.aistock_prices.stock.ai.AiConfig
import com.example.aistock_prices.stock.ai.KeyLevel
import com.example.aistock_prices.stock.ai.MetricInsight
import com.example.aistock_prices.stock.data.KLineBar
import com.example.aistock_prices.stock.data.MinutePoint
import com.example.aistock_prices.stock.data.StockCache
import com.example.aistock_prices.stock.data.StockQuote
import com.example.aistock_prices.stock.data.StockRepository
import com.example.aistock_prices.stock.ui.StockFormat
import com.example.aistock_prices.stock.ui.markdownConfig
import com.example.kuiklychart.chart.base.CandleData
import com.example.kuiklychart.chart.base.ChartDataPoint
import com.example.kuiklychart.chart.base.ChartDataSet
import com.example.kuiklychart.chart.base.PriceLevel
import com.example.kuiklychart.chart.candle.CandleStickChart
import com.example.kuiklychart.chart.line.LineChart
import com.example.kuiklytable.dsl.KuiklyTable
import com.example.kuiklytable.dsl.tableData
import com.example.kuiklytable.model.CellAlignment
import com.tencent.kuiklybase.KuiklyMarkdown
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 个股详情页：
 * 报价区（名称/代码/最新价/涨跌幅）+ 分时走势图 + 行情详情表 + （K线/AI 分析见后续阶段）
 */
@Page("stock_detail", supportInLocal = true)
internal class StockDetailPage : BasePager() {

    private var quote by observable<StockQuote?>(null)
    private var minutePoints by observableList<MinutePoint>()
    private var klineBars by observableList<KLineBar>()
    private var loading by observable(true)
    private var errorMsg by observable("")
    private var aiResult by observable<AiAnalysisResult?>(null)
    private var aiLoading by observable(false)
    /** 当前选中的 K 线索引（点击 K 线触发；-1 表示未选中） */
    private var selectedKlineIndex by observable(-1)
    /** 当前选中的分时点索引（点击分时图触发；-1 表示未选中） */
    private var selectedMinuteIndex by observable(-1)
    /** AI 深度分析正文（Markdown）是否展开（正文较长，默认收起） */
    private var markdownExpanded by observable(false)
    /** AI 分析请求序号：页面退出/重新发起时自增，使旧请求回调失效（防止退出后结果写回） */
    private var aiSeq = 0
    /** 数据就绪后是否需要自动执行 AI 分析 */
    private var needAutoAnalyze = false
    /** 上次自动分析时的配置签名（配置变化时重新分析） */
    private var lastConfigHash = ""
    /** 60 秒轮询定时器引用 */
    private var pollTimerRef = ""
    /** 加载超时兜底计时器引用 */
    private var loadTimerRef = ""

    private val stockCode: String get() = pagerData.params.optString("code")
    private val stockName: String get() = pagerData.params.optString("name", "个股详情")

    private val network: NetworkModule
        get() = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                flex(1f)
                backgroundColor(ctx.pal.bgPage)
            }

            RouterNavBar {
                attr {
                    title = ctx.stockName
                    backDisable = false
                }
            }

            // ---------- 风险等级标签（已配置 API 且分析完成才显示） ----------
            vif({ ctx.isAiConfigured() && ctx.aiResult != null }) {
                val level = ctx.aiResult!!.riskLevel
                val levelColor = when (level) {
                    AiAnalysisResult.RISK_LOW -> ctx.pal.accent
                    AiAnalysisResult.RISK_HIGH -> ctx.pal.up
                    else -> Color(0xFFE6A23C)
                }
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        backgroundColor(ctx.pal.card)
                        paddingLeft(16f)
                        paddingRight(16f)
                        paddingTop(6f)
                        paddingBottom(6f)
                    }
                    Text {
                        attr {
                            text("风险等级")
                            fontSize(12f)
                            color(ctx.pal.textSub)
                            marginRight(8f)
                        }
                    }
                    View {
                        attr {
                            width(8f)
                            height(8f)
                            borderRadius(4f)
                            backgroundColor(levelColor)
                            marginRight(5f)
                        }
                    }
                    Text {
                        attr {
                            text(level)
                            fontSize(12f)
                            fontWeightSemiBold()
                            color(levelColor)
                        }
                    }
                }
            }

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
                            text("  详情加载中...")
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
                                click { ctx.loadDetail() }
                            }
                        }
                    }
                }
                velse {
                    ctx.detailContent().invoke(this)
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        loadDetail()
    }

    /**
     * 页面每次出现（首次进入 / 从 AI 设置页保存返回）时触发：
     * 1. AI 分析不做持久化：已配置 API 时每次进入都会重新分析（aiResult 每次为 null）；
     * 2. 启动 60 秒轮询（报价 + 分时，K线每日更新不轮询）。
     */
    override fun pageDidAppear() {
        super.pageDidAppear()
        checkAutoAnalyze()
        startPolling()
    }

    override fun pageDidDisappear() {
        super.pageDidDisappear()
        cancelAnalyze()
        stopPolling()
        stopLoadTimer()
    }

    override fun pageWillDestroy() {
        super.pageWillDestroy()
        cancelAnalyze()
        stopPolling()
        stopLoadTimer()
    }

    /** 60 秒轮询刷新报价与分时（静默覆盖 + 写缓存） */
    private fun startPolling() {
        stopPolling()
        pollTimerRef = setTimeout(60000) {
            refreshQuoteAndMinute()
            startPolling()
        }
    }

    private fun stopPolling() {
        if (pollTimerRef.isNotEmpty()) {
            clearTimeout(pollTimerRef)
            pollTimerRef = ""
        }
    }

    /** 拉取最新报价 + 分时并写缓存（静默）；成功时解除加载超时兜底 */
    private fun refreshQuoteAndMinute() {
        StockRepository.fetchQuotes(network, listOf(stockCode)) { list ->
            val q = list.firstOrNull()
            if (q != null) {
                stopLoadTimer()
                quote = q
                loading = false
                errorMsg = ""
                StockCache.saveQuotes(sp, list)
                maybeRunAutoAnalyze()
            }
            StockRepository.fetchMinute(network, stockCode) { points ->
                if (points.isNotEmpty()) {
                    minutePoints.clear()
                    minutePoints.addAll(points)
                    StockCache.saveMinute(sp, stockCode, points)
                }
            }
        }
    }

    /**
     * 检查是否需要自动分析：
     * - 未配置 → 标记不分析
     * - 已有结果（含缓存恢复）且配置未变 → 直接展示，不重复请求
     * - 无结果 或 配置变化 → 标记待分析
     */
    private fun checkAutoAnalyze() {
        val config = loadAiConfig()
        if (config == null) {
            needAutoAnalyze = false
            return
        }
        val hash = config.baseUrl + "|" + config.apiKey + "|" + config.model
        if (aiResult == null || lastConfigHash != hash) {
            needAutoAnalyze = true
            lastConfigHash = hash
        }
        maybeRunAutoAnalyze()
    }

    /** 数据就绪且需要自动分析时执行（行情加载完成回调中调用） */
    private fun maybeRunAutoAnalyze() {
        if (!needAutoAnalyze) return
        if (aiLoading) return
        val q = quote ?: return
        needAutoAnalyze = false
        val config = loadAiConfig()
        if (config == null) return
        runAnalyze(config, q)
    }

    /** 读取当前可用 AI 配置：仅当存在「启用中的预设」才返回（停用/删除后一律视为未配置） */
    private fun loadAiConfig(): AiConfig? {
        val sp = acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
        return AiAnalysisService.activePreset(sp)?.let {
            AiConfig(it.baseUrl, it.apiKey, it.model)
        }
    }

    private val sp: SharedPreferencesModule
        get() = acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)

    /** 加载详情：先展示本地行情缓存，再拉新数据覆盖并写缓存；超时未就绪时用本地数据兜底。
     *  AI 分析不做持久化：每次进入详情页都会重新分析（pageDidAppear -> checkAutoAnalyze）。 */
    private fun loadDetail() {
        loading = true
        errorMsg = ""
        // 1. 缓存优先展示（接口临时失效时页面不空白）
        applyCache()
        // 2. 加载超时兜底：8 秒内行情仍未加载出来，则使用本地缓存或提示错误，避免一直空白
        stopLoadTimer()
        loadTimerRef = setTimeout(8000) {
            loadTimerRef = ""
            if (quote != null) return@setTimeout
            applyCache()
            if (quote != null) {
                loading = false
                errorMsg = ""
                bridgeModule.toast("网络较慢，已展示本地缓存数据")
            } else {
                loading = false
                errorMsg = "行情加载超时，请检查网络后重试"
            }
        }
        // 3. 拉取最新数据（成功后会解除超时兜底）
        refreshQuoteAndMinute()
        // 4. 日K线（独立加载，失败不影响其他区域）
        StockRepository.fetchKLine(network, stockCode, 60) { bars ->
            klineBars.clear()
            klineBars.addAll(bars)
            StockCache.saveKLine(sp, stockCode, bars)
            maybeRunAutoAnalyze()
        }
    }

    /** 将本地缓存填充到页面（报价/分时/K线，任一有数据即退出整页 loading） */
    private fun applyCache() {
        val cachedQuote = StockCache.loadQuotes(sp).firstOrNull { it.code == stockCode }
        val cachedMinute = StockCache.loadMinute(sp, stockCode)
        val cachedKLine = StockCache.loadKLine(sp, stockCode)
        if (cachedQuote != null) quote = cachedQuote
        if (cachedMinute.isNotEmpty()) {
            minutePoints.clear()
            minutePoints.addAll(cachedMinute)
        }
        if (cachedKLine.isNotEmpty()) {
            klineBars.clear()
            klineBars.addAll(cachedKLine)
        }
        if (cachedQuote != null || cachedMinute.isNotEmpty()) {
            loading = false
        }
    }

    private fun stopLoadTimer() {
        if (loadTimerRef.isNotEmpty()) {
            clearTimeout(loadTimerRef)
            loadTimerRef = ""
        }
    }

    /** 详情主内容（滚动） */
    private fun detailContent(): ViewBuilder {
        val ctx = this
        return {
            Scroller {
                attr {
                    flex(1f)
                }
                // ---------- 报价区 ----------
                ctx.quoteHeader().invoke(this)
                // ---------- 分时走势 ----------
                ctx.minuteChartCard().invoke(this)
                // ---------- 日K线 ----------
                ctx.klineChartCard().invoke(this)
                // ---------- AI 分析与解读 ----------
                ctx.aiCard().invoke(this)
                // ---------- 行情详情表 ----------
                ctx.detailTableCard().invoke(this)
                View {
                    attr {
                        height(24f)
                    }
                }
            }
        }
    }

    /** 报价区 */
    private fun quoteHeader(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    backgroundColor(ctx.pal.card)
                    padding(16f)
                    paddingTop(12f)
                    paddingBottom(12f)
                }
                vif({ ctx.quote != null }) {
                    val q = ctx.quote!!
                    val c = ctx.pal.ofChange(q.change)
                    // 名称 + 代码 + 涨跌徽标
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        Text {
                            attr {
                                flex(1f)
                                text(q.name)
                                fontSize(18f)
                                fontWeightBold()
                                color(ctx.pal.textMain)
                            }
                        }
                        Text {
                            attr {
                                text(q.symbol)
                                fontSize(13f)
                                color(ctx.pal.textSub)
                                marginRight(10f)
                            }
                        }
                        View {
                            attr {
                                paddingTop(3f)
                                paddingBottom(3f)
                                paddingLeft(8f)
                                paddingRight(8f)
                                borderRadius(4f)
                                backgroundColor(c)
                            }
                            Text {
                                attr {
                                    text(StockFormat.percent(q.changePercent))
                                    fontSize(12f)
                                    color(ctx.pal.onAccent)
                                }
                            }
                        }
                    }
                    // 最新价
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            marginTop(10f)
                        }
                        // 价格：36f 加粗大字号，固定预留宽度避免溢出盖住涨跌幅
                        Text {
                            attr {
                                width(160f)
                                text(StockFormat.price(q.price))
                                fontSize(36f)
                                fontWeightBold()
                                color(c)
                            }
                        }
                        View {
                            attr {
                                flex(1f)
                                flexDirectionRow()
                                alignItemsFlexEnd()
                                paddingBottom(6f)
                            }
                            Text {
                                attr {
                                    flex(1f)
                                    text(
                                        "${StockFormat.change(q.change)}  ${StockFormat.percent(q.changePercent)}"
                                    )
                                    fontSize(15f)
                                    color(c)
                                }
                            }
                        }
                    }
                    // 今开/最高/最低/昨收 快速信息
                    View {
                        attr {
                            flexDirectionRow()
                            marginTop(12f)
                        }
                        ctx.quickItem("今开", StockFormat.price(q.open)).invoke(this)
                        ctx.quickItem("最高", StockFormat.price(q.high)).invoke(this)
                        ctx.quickItem("最低", StockFormat.price(q.low)).invoke(this)
                        ctx.quickItem("昨收", StockFormat.price(q.prevClose)).invoke(this)
                    }
                }
            }
        }
    }

    /** 快速信息小格 */
    private fun quickItem(label: String, value: String): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flex(1f)
                    flexDirectionColumn()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text(label)
                        fontSize(12f)
                        color(ctx.pal.textSub)
                    }
                }
                Text {
                    attr {
                        text(value)
                        fontSize(14f)
                        fontWeightSemiBold()
                        color(ctx.pal.textMain)
                        marginTop(4f)
                    }
                }
            }
        }
    }

    /** 分时走势图 */
    private fun minuteChartCard(): ViewBuilder {
        val ctx = this
        val pageWidth = pagerData.pageViewWidth
        return {
            View {
                attr {
                    backgroundColor(ctx.pal.card)
                    marginTop(10f)
                    paddingTop(14f)
                    paddingBottom(10f)
                }
                Text {
                    attr {
                        text("分时走势")
                        fontSize(15f)
                        fontWeightSemiBold()
                        color(ctx.pal.textMain)
                        marginLeft(16f)
                        marginBottom(6f)
                    }
                }
                vif({ ctx.minutePoints.size > 1 }) {
                    val points = ctx.minutePoints
                    val range = ctx.minuteRange(points)
                    val width = pageWidth - 16f
                    // 选中点气泡（点击分时图后展示该时点信息；不接 AI，单分钟粒度追问意义不大）
                    vif({ ctx.selectedMinuteIndex >= 0 }) {
                        val idx = ctx.selectedMinuteIndex
                        val mp = ctx.minutePoints.getOrNull(idx)
                        if (mp != null) {
                            val prevClose = ctx.quote?.prevClose ?: points.first().price
                            val change = mp.price - prevClose
                            val pct = if (prevClose > 0) change / prevClose * 100 else 0.0
                            val upColor = ctx.pal.up
                            val downColor = ctx.pal.down
                            val flatColor = ctx.pal.flat
                            val changeColor = when {
                                change > 0 -> upColor
                                change < 0 -> downColor
                                else -> flatColor
                            }
                            View {
                                attr {
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    marginLeft(16f)
                                    marginRight(16f)
                                    marginTop(2f)
                                    marginBottom(6f)
                                    paddingLeft(10f)
                                    paddingRight(10f)
                                    paddingTop(6f)
                                    paddingBottom(6f)
                                    borderRadius(6f)
                                    backgroundColor(ctx.pal.accentChipBg)
                                }
                                Text {
                                    attr {
                                        text(ctx.formatMinuteTime(mp.time))
                                        fontSize(12f)
                                        color(ctx.pal.textMain)
                                        fontWeightSemiBold()
                                    }
                                }
                                Text {
                                    attr {
                                        text("  ${StockFormat.price(mp.price)}  ")
                                        fontSize(13f)
                                        color(changeColor)
                                        fontWeightSemiBold()
                                    }
                                }
                                Text {
                                    attr {
                                        text(
                                            "${if (change > 0) "+" else ""}${StockFormat.change(change)}  " +
                                                    "${StockFormat.percent(pct)}"
                                        )
                                        fontSize(12f)
                                        color(changeColor)
                                    }
                                }
                                Text {
                                    attr {
                                        flex(1f)
                                        text("")
                                    }
                                }
                                Text {
                                    attr {
                                        text("量 ${StockFormat.volume(mp.volume)}")
                                        fontSize(11f)
                                        color(ctx.pal.textSub)
                                    }
                                }
                            }
                        }
                    }
                    LineChart {
                        attr {
                            width(width)
                            height(200f)
                            dataSets = listOf(
                                ChartDataSet(
                                    points = points.map {
                                        ChartDataPoint(it.price.toFloat())
                                    },
                                    label = "价格",
                                    color = ctx.pal.ofChange(
                                        (ctx.quote?.price ?: points.last().price) - (ctx.quote?.prevClose ?: points.first().price)
                                    )
                                )
                            )
                            xAxis {
                                labels = listOf("09:30", "10:30", "11:30", "13:00", "14:00", "15:00")
                                showGridLines = false
                                labelColor = if (ctx.isNightMode()) Color(0xFF98A2B3) else Color(0xFF999999)
                                gridColor = if (ctx.isNightMode()) Color(0xFF363D48) else Color(0x1A000000)
                                axisColor = if (ctx.isNightMode()) Color(0xFF363D48) else Color(0xFFE0E0E0)
                            }
                            yAxis {
                                min = range.first
                                max = range.second
                                showGridLines = true
                                formatter = { v -> StockFormat.price(v.toDouble()) }
                                labelColor = if (ctx.isNightMode()) Color(0xFF98A2B3) else Color(0xFF999999)
                                gridColor = if (ctx.isNightMode()) Color(0xFF363D48) else Color(0x1A000000)
                                axisColor = if (ctx.isNightMode()) Color(0xFF363D48) else Color(0xFFE0E0E0)
                            }
                            smooth = true
                            lineWidth = 1.8f
                            showDots = false
                            fillArea = true
                        }
                        event {
                            onDataPointClick = { _, idx, _ ->
                                ctx.selectMinute(idx)
                            }
                        }
                    }
                }
                velse {
                    View {
                        attr {
                            width(pageWidth - 16f)
                            height(120f)
                            allCenter()
                        }
                        Text {
                            attr {
                                text("分时数据暂不可用")
                                fontSize(13f)
                                color(ctx.pal.textSub)
                            }
                        }
                    }
                }
            }
        }
    }

    /** 日K线图 */
    private fun klineChartCard(): ViewBuilder {
        val ctx = this
        val pageWidth = pagerData.pageViewWidth
        return {
            View {
                attr {
                    backgroundColor(ctx.pal.card)
                    marginTop(10f)
                    paddingTop(14f)
                    paddingBottom(10f)
                }
                Text {
                    attr {
                        text("日K线")
                        fontSize(15f)
                        fontWeightSemiBold()
                        color(ctx.pal.textMain)
                        marginLeft(16f)
                        marginBottom(6f)
                    }
                }
                vif({ ctx.klineBars.size > 1 }) {
                    val kData = ctx.klineBars
                    val width = pageWidth - 16f
                    CandleStickChart {
                        attr {
                            width(width)
                            height(260f)
                            bars = kData.map {
                                CandleData(
                                    date = it.date,
                                    open = it.open.toFloat(),
                                    close = it.close.toFloat(),
                                    high = it.high.toFloat(),
                                    low = it.low.toFloat(),
                                    volume = it.volume
                                )
                            }
                            xAxis {
                                labels = ctx.sparseDateLabels(kData, 6)
                                showGridLines = false
                                labelColor = if (ctx.isNightMode()) Color(0xFF98A2B3) else Color(0xFF999999)
                                gridColor = if (ctx.isNightMode()) Color(0xFF363D48) else Color(0x1A000000)
                                axisColor = if (ctx.isNightMode()) Color(0xFF363D48) else Color(0xFFE0E0E0)
                            }
                            yAxis {
                                showGridLines = true
                                formatter = { v -> StockFormat.price(v.toDouble()) }
                                labelColor = if (ctx.isNightMode()) Color(0xFF98A2B3) else Color(0xFF999999)
                                gridColor = if (ctx.isNightMode()) Color(0xFF363D48) else Color(0x1A000000)
                                axisColor = if (ctx.isNightMode()) Color(0xFF363D48) else Color(0xFFE0E0E0)
                            }
                            upColor = ctx.pal.up
                            downColor = ctx.pal.down
                            showVolume = true
                            priceLevels = ctx.aiResult?.keyLevels?.map { lvl ->
                                PriceLevel(
                                    price = lvl.price.toFloat(),
                                    label = lvl.label,
                                    color = if (lvl.type == KeyLevel.TYPE_RESISTANCE) ctx.pal.up else ctx.pal.down
                                )
                            } ?: emptyList()
                        }
                        event {
                            onDataPointClick = { _, idx, _ ->
                                // Kuikly vif 条件不变不会重建子树；先设 -1 强制销毁提示条，
                                // 再延迟一拍设回 idx 重建以承载新内容（避免仅首次生效）
                                ctx.selectKline(idx)
                            }
                        }
                    }
                }
                velse {
                    View {
                        attr {
                            width(pageWidth - 16f)
                            height(160f)
                            allCenter()
                        }
                        Text {
                            attr {
                                text("K线数据暂不可用")
                                fontSize(13f)
                                color(ctx.pal.textSub)
                            }
                        }
                    }
                }
                // 点选提示条：选中某根 K 线后展示该时点信息 + 追问入口
                vif({ ctx.selectedKlineIndex >= 0 }) {
                    val idx = ctx.selectedKlineIndex
                    val bar = ctx.klineBars.getOrNull(idx)
                    if (bar != null) {
                        View {
                            attr {
                                flexDirectionRow()
                                alignItemsCenter()
                                marginTop(10f)
                                marginLeft(16f)
                                marginRight(16f)
                                paddingTop(8f)
                                paddingBottom(8f)
                                paddingLeft(12f)
                                paddingRight(12f)
                                borderRadius(8f)
                                backgroundColor(ctx.pal.accentChipBg)
                            }
                            Text {
                                attr {
                                    flex(1f)
                                    text(
                                        "${if (bar.date.length >= 10) bar.date.substring(5, 10) else bar.date} " +
                                                "收盘 ${StockFormat.price(bar.close)}"
                                    )
                                    fontSize(12f)
                                    color(ctx.pal.textMain)
                                }
                            }
                            Text {
                                attr {
                                    text("问问 AI")
                                    fontSize(12f)
                                    color(ctx.pal.accent)
                                    fontWeightSemiBold()
                                }
                                event {
                                    click { ctx.askAiAboutKline(idx) }
                                }
                            }
                        }
                    }
                }
                // 关键价位说明（AI 结构化支撑/压力，与 K 线参考线对应）
                vif({ ctx.aiResult != null && ctx.aiResult!!.keyLevels.isNotEmpty() }) {
                    val levels = ctx.aiResult!!.keyLevels
                    View {
                        attr {
                            flexDirectionColumn()
                            marginTop(10f)
                            marginLeft(16f)
                            marginRight(16f)
                        }
                        levels.forEach { lvl ->
                            View {
                                attr {
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    marginTop(3f)
                                    marginBottom(3f)
                                }
                                View {
                                    attr {
                                        width(8f)
                                        height(8f)
                                        borderRadius(4f)
                                        backgroundColor(
                                            if (lvl.type == KeyLevel.TYPE_RESISTANCE) ctx.pal.up else ctx.pal.down
                                        )
                                        marginRight(6f)
                                    }
                                }
                                Text {
                                    attr {
                                        text("${lvl.label} ${StockFormat.price(lvl.price)}")
                                        fontSize(12f)
                                        fontWeightSemiBold()
                                        color(ctx.pal.textMain)
                                    }
                                }
                                vif({ lvl.desc.isNotEmpty() }) {
                                    Text {
                                        attr {
                                            text("  ${lvl.desc}")
                                            fontSize(11f)
                                            color(ctx.pal.textSub)
                                            flex(1f)
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

    /** 从K线日期中抽样 N 个等距标签（MM-DD 格式） */
    private fun sparseDateLabels(bars: List<KLineBar>, n: Int): List<String> {
        if (bars.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        val last = bars.size - 1
        for (i in 0 until n) {
            val idx = (last * i) / (n - 1)
            val d = bars[idx].date
            result.add(if (d.length >= 10) d.substring(5, 10) else d)
        }
        return result
    }

    /** AI 分析与解读卡片 */
    private fun aiCard(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    backgroundColor(ctx.pal.card)
                    marginTop(10f)
                    padding(16f)
                }
                // 标题 + 来源
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        marginBottom(12f)
                    }
                    Text {
                        attr {
                            flex(1f)
                            text("AI 分析与解读")
                            fontSize(15f)
                            fontWeightSemiBold()
                            color(ctx.pal.textMain)
                        }
                    }
                    vif({ ctx.aiResult != null }) {
                        View {
                            attr {
                                paddingTop(2f)
                                paddingBottom(2f)
                                paddingLeft(8f)
                                paddingRight(8f)
                                borderRadius(4f)
                                backgroundColor(ctx.pal.chip2Bg)
                            }
                            Text {
                                attr {
                                    text("来源：${ctx.aiResult!!.source}")
                                    fontSize(11f)
                                    color(ctx.pal.textSub)
                                }
                            }
                        }
                    }
                }

                // 未配置 → 引导
                vif({ !ctx.aiLoading && ctx.aiResult == null && !ctx.isAiConfigured() }) {
                    ctx.aiGuideCard().invoke(this)
                }
                // 已配置 → 操作按钮
                vif({ !ctx.aiLoading && ctx.aiResult == null && ctx.isAiConfigured() }) {
                    ctx.aiActionButton().invoke(this)
                }
                // 请求中
                vif({ ctx.aiLoading }) {
                    View {
                        attr {
                            flexDirectionRow()
                            allCenter()
                            height(80f)
                        }
                        ActivityIndicator {
                            attr {
                                isGrayStyle(false)
                            }
                        }
                        Text {
                            attr {
                                text("  AI 分析中，请稍候...")
                                fontSize(14f)
                                color(ctx.pal.textSub)
                            }
                        }
                    }
                }
                // 分析结果
                vif({ ctx.aiResult != null }) {
                    ctx.aiResultView(ctx.aiResult!!).invoke(this)
                }
            }
        }
    }

    private fun isAiConfigured(): Boolean =
        AiAnalysisService.activePreset(
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
        ) != null

    /** 未配置引导卡片 */
    private fun aiGuideCard(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    borderRadius(8f)
                    backgroundColor(ctx.pal.accentChipBg)
                    padding(14f)
                }
                Text {
                    attr {
                        text("尚未配置 AI 服务。配置后可基于实时行情生成趋势判断、买卖点位与风险提醒。")
                        fontSize(13f)
                        color(ctx.pal.textMain)
                        lineHeight(20f)
                    }
                }
                View {
                    attr {
                        marginTop(10f)
                        height(34f)
                        paddingLeft(16f)
                        paddingRight(16f)
                        borderRadius(17f)
                        allCenter()
                        backgroundColor(ctx.pal.accent)
                    }
                    Text {
                        attr {
                            text("去设置")
                            fontSize(14f)
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

    /** 发起 AI 分析按钮 */
    private fun aiActionButton(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    height(44f)
                    borderRadius(22f)
                    allCenter()
                    backgroundColor(ctx.pal.accent)
                }
                Text {
                    attr {
                        text("开始 AI 分析")
                        fontSize(15f)
                        color(ctx.pal.onAccent)
                        fontWeightSemiBold()
                    }
                }
                event {
                    click { ctx.analyze() }
                }
            }
        }
    }

    /** AI 分析结果视图 */
    private fun aiResultView(result: AiAnalysisResult): ViewBuilder {
        val ctx = this
        return {
            // 趋势标签
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                View {
                    attr {
                        paddingTop(4f)
                        paddingBottom(4f)
                        paddingLeft(10f)
                        paddingRight(10f)
                        borderRadius(4f)
                        backgroundColor(ctx.pal.ofChange(if (result.trend.contains("空")) -1.0 else if (result.trend.contains("多")) 1.0 else 0.0))
                    }
                    Text {
                        attr {
                            text(result.trend)
                            fontSize(13f)
                            color(ctx.pal.onAccent)
                            fontWeightSemiBold()
                        }
                    }
                }
                vif({ result.trendDesc.isNotEmpty() }) {
                    Text {
                        attr {
                            text("  ${result.trendDesc}")
                            fontSize(13f)
                            color(ctx.pal.textSub)
                            flex(1f)
                        }
                    }
                }
            }
            // 操作建议
            vif({ result.suggestion.isNotEmpty() }) {
                View {
                    attr {
                        marginTop(12f)
                        borderRadius(8f)
                        backgroundColor(Color(0xFFFFF7E6))
                        padding(10f)
                    }
                    Text {
                        attr {
                            text("操作建议：${result.suggestion}")
                            fontSize(13f)
                            color(Color(0xFFB87A00))
                            lineHeight(20f)
                        }
                    }
                }
            }
            // 买卖点位
            vif({ result.buyPoints.isNotEmpty() }) {
                View {
                    attr {
                        flexDirectionColumn()
                        marginTop(12f)
                    }
                    Text {
                        attr {
                            text("买入参考")
                            fontSize(13f)
                            fontWeightSemiBold()
                            color(ctx.pal.up)
                            marginBottom(4f)
                        }
                    }
                    result.buyPoints.forEach { p ->
                        Text {
                            attr {
                                text("· $p")
                                fontSize(13f)
                                color(ctx.pal.textMain)
                                marginTop(3f)
                            }
                        }
                    }
                }
            }
            vif({ result.sellPoints.isNotEmpty() }) {
                View {
                    attr {
                        flexDirectionColumn()
                        marginTop(12f)
                    }
                    Text {
                        attr {
                            text("卖出参考")
                            fontSize(13f)
                            fontWeightSemiBold()
                            color(ctx.pal.down)
                            marginBottom(4f)
                        }
                    }
                    result.sellPoints.forEach { p ->
                        Text {
                            attr {
                                text("· $p")
                                fontSize(13f)
                                color(ctx.pal.textMain)
                                marginTop(3f)
                            }
                        }
                    }
                }
            }
            // 风险提醒
            vif({ result.risks.isNotEmpty() }) {
                View {
                    attr {
                        marginTop(12f)
                        borderRadius(8f)
                        backgroundColor(Color(0xFFFFF1F0))
                        padding(10f)
                    }
                    Text {
                        attr {
                            text("风险提醒")
                            fontSize(13f)
                            fontWeightSemiBold()
                            color(ctx.pal.up)
                            marginBottom(4f)
                        }
                    }
                    result.risks.forEach { r ->
                        Text {
                            attr {
                                text("· $r")
                                fontSize(13f)
                                color(ctx.pal.textMain)
                                marginTop(3f)
                            }
                        }
                    }
                }
            }
            // 行情总结
            vif({ result.summary.isNotEmpty() }) {
                Text {
                    attr {
                        text(result.summary)
                        fontSize(13f)
                        color(ctx.pal.textMain)
                        lineHeight(20f)
                        marginTop(12f)
                    }
                }
            }
            // AI 深度分析正文（Markdown，可展开收起）
            vif({ result.markdown.isNotBlank() }) {
                View {
                    attr {
                        marginTop(12f)
                        flexDirectionRow()
                        alignItemsCenter()
                        height(32f)
                        borderRadius(8f)
                        paddingLeft(10f)
                        paddingRight(10f)
                        backgroundColor(ctx.pal.chip2Bg)
                    }
                    Text {
                        attr {
                            text("深度分析正文")
                            fontSize(13f)
                            fontWeightSemiBold()
                            color(ctx.pal.textMain)
                            flex(1f)
                        }
                    }
                    Text {
                        attr {
                            text(if (ctx.markdownExpanded) "收起" else "展开")
                            fontSize(13f)
                            color(ctx.pal.accent)
                        }
                        event {
                            click { ctx.markdownExpanded = !ctx.markdownExpanded }
                        }
                    }
                }
                vif({ ctx.markdownExpanded }) {
                    View {
                        attr {
                            marginTop(8f)
                            width(ctx.pagerData.pageViewWidth - 64f)
                        }
                        KuiklyMarkdown(content = result.markdown, config = markdownConfig(ctx.isNightMode()))
                    }
                }
            }
            // 重新分析
            View {
                attr {
                    marginTop(14f)
                    height(36f)
                    borderRadius(18f)
                    allCenter()
                    border(Border(1f, BorderStyle.SOLID, ctx.pal.accent))
                }
                Text {
                    attr {
                        text("重新分析")
                        fontSize(14f)
                        color(ctx.pal.accent)
                    }
                }
                event {
                    click { ctx.analyze() }
                }
            }
            // 详细分析：进入 AI 问答页（该股专属会话）并自动发问
            View {
                attr {
                    marginTop(10f)
                    height(36f)
                    borderRadius(18f)
                    allCenter()
                    border(Border(1f, BorderStyle.SOLID, ctx.pal.divider))
                }
                Text {
                    attr {
                        text("详细分析")
                        fontSize(14f)
                        color(ctx.pal.textSub)
                    }
                }
                event {
                    click { ctx.openAiChat() }
                }
            }
        }
    }

    /** 发起 AI 分析（手动按钮点击） */
    private fun analyze() {
        val q = quote ?: return
        val config = loadAiConfig()
        if (config == null) {
            bridgeModule.toast("请先配置 AI 服务")
            openAiConfig()
            return
        }
        lastConfigHash = config.baseUrl + "|" + config.apiKey + "|" + config.model
        runAnalyze(config, q)
    }

    /** 执行分析请求（手动 / 自动共用）；结果仅存内存，不持久化（每次进入重新分析） */
    private fun runAnalyze(config: AiConfig, q: StockQuote) {
        val seq = ++aiSeq
        aiLoading = true
        aiResult = null
        AiAnalysisService.analyze(
            network, config, q,
            minutePoints.toList(), klineBars.toList()
        ) { result ->
            // 已有更新的请求或页面已退出/销毁（aiSeq 已自增），丢弃本次结果
            if (seq != aiSeq) return@analyze
            aiLoading = false
            aiResult = result
        }
    }

    /** 中断在途 AI 分析（页面退出/销毁时调用）：序号失效 + 立即收起 loading，结果回调被丢弃 */
    private fun cancelAnalyze() {
        aiSeq++
        aiLoading = false
    }

    /** 打开 AI 服务设置页 */
    private fun openAiConfig() {
        val pageData = JSONObject().apply {
            put("code", stockCode)
            put("name", stockName)
        }
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("ai_config", pageData)
    }

    /** 点选某根 K 线（强制 vif 重建，使提示条实时刷新） */
    private fun selectKline(idx: Int) {
        selectedKlineIndex = -1
        setTimeout(0) { selectedKlineIndex = idx }
    }

    /** 点选某个分时点（强制 vif 重建，使价格气泡实时刷新） */
    private fun selectMinute(idx: Int) {
        selectedMinuteIndex = -1
        setTimeout(0) { selectedMinuteIndex = idx }
    }

    /** 进入 AI 问答页（该股专属会话，自动发问） */
    private fun openAiChat() {
        val pageData = JSONObject().apply {
            put("code", stockCode)
            put("name", stockName)
        }
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("ai_chat", pageData)
    }

    /** 针对某根 K 线追问 AI：把选中 bar 的完整数据打包进 pageData，供 AI 提问时引用 */
    private fun askAiAboutKline(index: Int) {
        val bar = klineBars.getOrNull(index) ?: return
        val date = if (bar.date.length >= 10) bar.date.substring(0, 10) else bar.date
        val pageData = JSONObject().apply {
            put("code", stockCode)
            put("name", stockName)
            put(
                "question",
                "${stockName}（$stockCode）在 $date 的 K 线（开盘 ${StockFormat.price(bar.open)}、" +
                        "收盘 ${StockFormat.price(bar.close)}、最高 ${StockFormat.price(bar.high)}、" +
                        "最低 ${StockFormat.price(bar.low)}、成交量 ${bar.volume}），" +
                        "请结合该时点的量价关系做简要分析"
            )
            // 选中数据快照：发问时附带，AI 提示词可直接引用而不必自行检索
            put("barDate", date)
            put("barOpen", bar.open)
            put("barClose", bar.close)
            put("barHigh", bar.high)
            put("barLow", bar.low)
            put("barVolume", bar.volume)
        }
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("ai_chat", pageData)
    }

    /** 行情详情表 */
    private fun detailTableCard(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    backgroundColor(ctx.pal.card)
                    marginTop(10f)
                    paddingTop(14f)
                    paddingBottom(14f)
                }
                Text {
                    attr {
                        text("行情详情")
                        fontSize(15f)
                        fontWeightSemiBold()
                        color(ctx.pal.textMain)
                        marginLeft(16f)
                        marginBottom(10f)
                    }
                }
                vif({ ctx.quote != null }) {
                    val q = ctx.quote!!
                    // AI 指标注解 → 详情表对应行高亮
                    val metricByKey = ctx.aiResult?.metrics?.associateBy { it.key } ?: emptyMap()
                    val hlColor = ctx.pal.accentChipBg.hexColor
                    val rows = listOf(
                        Triple("open", "今开", StockFormat.price(q.open)),
                        Triple("high", "最高", StockFormat.price(q.high)),
                        Triple("low", "最低", StockFormat.price(q.low)),
                        Triple("prevClose", "昨收", StockFormat.price(q.prevClose)),
                        Triple("change", "涨跌额", StockFormat.change(q.change)),
                        Triple("changePercent", "涨跌幅", StockFormat.percent(q.changePercent)),
                        Triple("volume", "成交量", StockFormat.volume(q.volume)),
                        Triple("amount", "成交额", StockFormat.amount(q.amount)),
                        Triple("turnover", "换手率", "${StockFormat.price(q.turnover)}%"),
                        Triple("amplitude", "振幅", "${StockFormat.price(q.amplitude)}%"),
                        Triple("avgPrice", "均价", StockFormat.price(q.avgPrice))
                    )
                    val data = tableData {
                        column("k", "指标", width = 110f)
                        column("v", "数值", alignment = CellAlignment.RIGHT)
                        rows.forEach { (key, label, value) ->
                            row {
                                if (metricByKey.containsKey(key)) backgroundColor(hlColor)
                                cell("k", label)
                                cell("v", value)
                            }
                        }
                    }
                    KuiklyTable(data) {
                        headerBackgroundColor = if (ctx.isNightMode()) 0xFF2A3039 else 0xFFF5F6F8
                        headerTextColor = if (ctx.isNightMode()) 0xFF98A2B3 else 0xFF666666
                        cellTextColor = if (ctx.isNightMode()) 0xFFE6E9EF else 0xFF1A1A1A
                        borderColor = if (ctx.isNightMode()) 0xFF363D48 else 0xFFEEEEEE
                        cellPaddingH = 16f
                        showZebraStripe = false
                        showOuterBorder = false
                        stickyHeader = false
                        editable = false
                    }
                    // AI 指标注解说明（对应上表高亮行）
                    val metrics = ctx.aiResult?.metrics ?: emptyList()
                    if (metrics.isNotEmpty()) {
                        View {
                            attr {
                                marginTop(10f)
                                marginLeft(16f)
                                marginRight(16f)
                                paddingLeft(12f)
                                paddingRight(12f)
                                paddingTop(10f)
                                paddingBottom(10f)
                                borderRadius(8f)
                                backgroundColor(ctx.pal.accentChipBg)
                            }
                            metrics.forEach { m ->
                                View {
                                    attr {
                                        flexDirectionRow()
                                        marginTop(4f)
                                        marginBottom(4f)
                                    }
                                    Text {
                                        attr {
                                            text(m.name)
                                            fontSize(12f)
                                            color(ctx.pal.accent)
                                            fontWeightSemiBold()
                                            marginRight(6f)
                                        }
                                    }
                                    Text {
                                        attr {
                                            text(m.comment)
                                            fontSize(12f)
                                            color(ctx.pal.textMain)
                                            flex(1f)
                                            lineHeight(18f)
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

    /** 分时图 Y 轴范围：以昨收为基准上下对称 + 5% 余量 */
    private fun minuteRange(points: List<MinutePoint>): Pair<Float, Float> {
        val prevClose = quote?.prevClose ?: points.firstOrNull()?.price ?: 0.0
        var minV = points.minOfOrNull { it.price } ?: prevClose
        var maxV = points.maxOfOrNull { it.price } ?: prevClose
        minV = minOf(minV, prevClose)
        maxV = maxOf(maxV, prevClose)
        if (maxV <= minV) {
            minV -= 0.1
            maxV += 0.1
        }
        val pad = (maxV - minV) * 0.06f
        return Pair((minV - pad).toFloat(), (maxV + pad).toFloat())
    }

    /** 分时点时间格式：HHmm → HH:mm（HHmm/HHmmss 兼容） */
    private fun formatMinuteTime(time: String): String {
        return when (time.length) {
            4 -> "${time.substring(0, 2)}:${time.substring(2, 4)}"
            6 -> "${time.substring(0, 2)}:${time.substring(2, 4)}"
            else -> time
        }
    }
}
