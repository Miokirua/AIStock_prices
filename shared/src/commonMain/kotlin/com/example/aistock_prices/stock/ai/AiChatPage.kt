package com.example.aistock_prices.stock.ai

import com.example.aistock_prices.RouterNavBar
import com.example.aistock_prices.base.BasePager
import com.example.aistock_prices.base.setTimeout
import com.example.aistock_prices.stock.ui.StockColors
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder

/**
 * AI 问答独立页（从个股详情页「更多详情」跳转进入）。
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
                backgroundColor(StockColors.BG_PAGE)
            }

            RouterNavBar {
                attr {
                    title = "AI 问答"
                    backDisable = false
                }
            }

            AiChatView {
                ctx.chatView = this
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        val code = pagerData.params.optString("code")
        val name = pagerData.params.optString("name", "该股票")
        if (code.isNotBlank()) {
            // 注意：body() 先于 viewDidLoad() 执行（Pager.didInit 内 created -> body -> viewDidLoad），
            // chatView 已就绪；额外延迟一拍确保视图树稳定后再初始化该股会话并自动发问。
            setTimeout(200) {
                chatView?.initForStock(code, name, autoSend = true)
            }
        }
    }

    /** 页面每次出现（含从 AI 设置页返回）时刷新：重新读取 SP 配置并触发视图重跑 */
    override fun pageDidAppear() {
        super.pageDidAppear()
        chatView?.reload()
    }
}
