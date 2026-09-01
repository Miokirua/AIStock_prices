package com.example.aistock_prices.stock.ai

import com.example.aistock_prices.RouterNavBar
import com.example.aistock_prices.base.BasePager
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
            chatView?.initForStock(code, name, autoSend = true)
        }
    }
}
