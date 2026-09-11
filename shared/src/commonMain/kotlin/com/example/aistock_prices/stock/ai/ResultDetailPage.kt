package com.example.aistock_prices.stock.ai

import com.example.aistock_prices.RouterNavBar
import com.example.aistock_prices.base.BasePager
import com.example.aistock_prices.stock.data.StockQuote
import com.example.aistock_prices.stock.data.StockRepository
import com.example.aistock_prices.stock.ui.StockFormat
import com.example.aistock_prices.stock.ui.markdownConfig
import com.example.kuiklychart.chart.base.ChartDataPoint
import com.example.kuiklychart.chart.base.ChartDataSet
import com.example.kuiklychart.chart.line.LineChart
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
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.KuiklyMarkdown

/**
 * AI 问答结果详情页（承接页）：
 * 从 AI 问答消息「查看完整分析」进入，展示 基础行情 + 走势区域 + AI 解读（Markdown）。
 */
@Page("result_detail", supportInLocal = true)
internal class ResultDetailPage : BasePager() {

    private var quote by observable<StockQuote?>(null)
    private var minutePoints by observableList<com.example.aistock_prices.stock.data.MinutePoint>()
    private var content by observable("")
    private var pageTitle by observable("分析详情")
    private var loading by observable(true)

    private val convId: String get() = pagerData.params.optString("convId")
    private val msgTs: Long get() = pagerData.params.optLong("msgTs") ?: 0L

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

            RouterNavBar {
                attr {
                    title = ctx.pageTitle
                    backDisable = false
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
                            text("  加载中...")
                            fontSize(14f)
                            color(ctx.pal.textSub)
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
                    // 基础行情卡
                    vif({ ctx.quote != null }) {
                        ctx.quoteCard().invoke(this)
                    }
                    // 走势区域
                    vif({ ctx.minutePoints.size > 1 }) {
                        ctx.minuteChartCard().invoke(this)
                    }
                    // AI 解读
                    View {
                        attr {
                            marginTop(10f)
                            backgroundColor(ctx.pal.card)
                            padding(16f)
                            paddingBottom(24f)
                        }
                        Text {
                            attr {
                                text("AI 解读")
                                fontSize(15f)
                                fontWeightSemiBold()
                                color(ctx.pal.textMain)
                                marginBottom(10f)
                            }
                        }
                        // 固定宽度容器：确保长文本/Markdown 换行不超出屏幕
                        View {
                            attr {
                                width(ctx.pagerData.pageViewWidth - 32f)
                            }
                            KuiklyMarkdown(content = ctx.content, config = markdownConfig(ctx.isNightMode()))
                        }
                        // 继续追问：回到 AI 问答页并把本条分析设为引用上下文
                        View {
                            attr {
                                marginTop(18f)
                                height(40f)
                                borderRadius(20f)
                                allCenter()
                                backgroundColor(ctx.pal.accent)
                            }
                            Text {
                                attr {
                                    text("💬 就这条继续追问")
                                    fontSize(14f)
                                    color(ctx.pal.onAccent)
                                    fontWeightSemiBold()
                                }
                            }
                            event {
                                click { ctx.askFollowUp() }
                            }
                        }
                    }
                    View {
                        attr {
                            height(24f)
                        }
                    }
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        loadResult()
    }

    /** 就本条分析继续追问：回 AI 问答页，切到该会话并把本条设为引用上下文 */
    private fun askFollowUp() {
        val pageData = JSONObject().apply {
            put("convId", convId)
            put("quoteTs", msgTs)
        }
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("ai_chat", pageData)
    }

    private fun loadResult() {
        val conv = ConversationStore.load(sp).firstOrNull { it.id == convId }
        val msg = conv?.messages?.firstOrNull { it.ts == msgTs }
        content = msg?.content ?: "该分析内容不存在或已被删除。"
        pageTitle = conv?.displayName ?: "分析详情"
        // 取第一条股票标记作为行情区数据源
        val seg = parseChatSegments(content).firstOrNull { it.type == "stock" }
        if (seg != null && seg.code.isNotBlank()) {
            StockRepository.fetchQuotes(network, listOf(seg.code)) { list ->
                val q = list.firstOrNull()
                quote = q
                loading = false
                if (q != null) {
                    StockRepository.fetchMinute(network, q.code) { points ->
                        if (points.isNotEmpty()) {
                            minutePoints.clear()
                            minutePoints.addAll(points)
                        }
                    }
                }
            }
        } else {
            loading = false
        }
    }

    /** 基础行情卡 */
    private fun quoteCard(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    backgroundColor(ctx.pal.card)
                    padding(16f)
                }
                vif({ ctx.quote != null }) {
                    val q = ctx.quote!!
                    val c = ctx.pal.ofChange(q.change)
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
                            }
                        }
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            marginTop(8f)
                        }
                        // 价格：32f 加粗大字号，固定预留宽度避免溢出盖住涨跌幅
                        Text {
                            attr {
                                width(140f)
                                text(StockFormat.price(q.price))
                                fontSize(32f)
                                fontWeightBold()
                                color(c)
                            }
                        }
                        View {
                            attr {
                                flex(1f)
                                flexDirectionRow()
                                alignItemsFlexEnd()
                                paddingBottom(5f)
                            }
                            Text {
                                attr {
                                    flex(1f)
                                    text("${StockFormat.change(q.change)}  ${StockFormat.percent(q.changePercent)}")
                                    fontSize(14f)
                                    color(c)
                                }
                            }
                        }
                    }
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

    /** 走势区域（分时图） */
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
                        text("走势")
                        fontSize(15f)
                        fontWeightSemiBold()
                        color(ctx.pal.textMain)
                        marginLeft(16f)
                        marginBottom(6f)
                    }
                }
                val points = ctx.minutePoints
                val prevClose = ctx.quote?.prevClose ?: points.first().price
                val range = ctx.minuteRange(points, prevClose)
                val width = pageWidth - 16f
                LineChart {
                    attr {
                        width(width)
                        height(180f)
                        dataSets = listOf(
                            ChartDataSet(
                                points = points.map { ChartDataPoint(it.price.toFloat()) },
                                label = "价格",
                                color = ctx.pal.ofChange(
                                    (ctx.quote?.price ?: points.last().price) - prevClose
                                )
                            )
                        )
                        xAxis {
                            labels = listOf("09:30", "10:30", "11:30", "13:00", "14:00", "15:00")
                            showGridLines = false
                        }
                        yAxis {
                            min = range.first
                            max = range.second
                            showGridLines = true
                            formatter = { v -> StockFormat.price(v.toDouble()) }
                        }
                        smooth = true
                        lineWidth = 1.8f
                        showDots = false
                        fillArea = true
                    }
                }
            }
        }
    }

    private fun minuteRange(
        points: List<com.example.aistock_prices.stock.data.MinutePoint>,
        prevClose: Double
    ): Pair<Float, Float> {
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
}
