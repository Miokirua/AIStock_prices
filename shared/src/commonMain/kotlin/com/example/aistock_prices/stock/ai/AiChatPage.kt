package com.example.aistock_prices.stock.ai

import com.example.aistock_prices.RouterNavBar
import com.example.aistock_prices.base.BasePager
import com.example.aistock_prices.base.setTimeout
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * AI 问答独立页（从个股详情页「详细分析」跳转进入）。
 * 复用 [AiChatView]；带 code/name 参数时切换到该股专属会话并自动发问。
 */
@Page("ai_chat", supportInLocal = true)
internal class AiChatPage : BasePager() {

    private var chatView: AiChatView? = null

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                flex(1f)
                backgroundColor(ctx.pal.bgPage)
            }

            RouterNavBar {
                attr {
                    title = "AI 问答"
                    backDisable = false
                }
            }

            AiChatView {
                ctx.chatView = this
                // 绑定"查看完整分析"跳转 result_detail 入口（独立路由页与首页内联 Tab 同源）
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
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        val code = pagerData.params.optString("code")
        val name = pagerData.params.optString("name", "该股票")
        val question = pagerData.params.optString("question").takeIf { it.isNotBlank() }
        if (code.isNotBlank()) {
            // 注意：body() 先于 viewDidLoad() 执行（Pager.didInit 内 created -> body -> viewDidLoad），
            // chatView 已就绪；额外延迟一拍确保视图树稳定后再初始化该股会话并自动发问。
            setTimeout(200) {
                // 详情页点 K 线追问：pageData 携带选中 bar 完整 OHLCV 数据
                val barDate = pagerData.params.optString("barDate")
                if (question != null && barDate.isNotBlank()) {
                    chatView?.initForStockWithBar(
                        code = code,
                        name = name,
                        question = question,
                        barDate = barDate,
                        barOpen = pagerData.params.optDouble("barOpen", 0.0),
                        barClose = pagerData.params.optDouble("barClose", 0.0),
                        barHigh = pagerData.params.optDouble("barHigh", 0.0),
                        barLow = pagerData.params.optDouble("barLow", 0.0),
                        barVolume = pagerData.params.optLong("barVolume", 0L)
                    )
                } else {
                    chatView?.initForStock(code, name, autoSend = true, question = question)
                }
            }
        }
    }

    /** 页面每次出现（含从 AI 设置页返回）时刷新：重新读取 SP 配置并触发视图重跑 */
    override fun pageDidAppear() {
        super.pageDidAppear()
        chatView?.reload()
    }

    /** 页面被覆盖/失焦：中断进行中的 AI 生成（避免跳走页面后继续空耗 token） */
    override fun pageDidDisappear() {
        super.pageDidDisappear()
        chatView?.cancelIfSending()
    }

    /** 页面销毁（返回上一页）：中断进行中的 AI 生成 */
    override fun pageWillDestroy() {
        super.pageWillDestroy()
        chatView?.cancelIfSending()
    }
}
