package com.example.aistock_prices.stock.ai

import com.example.aistock_prices.RouterNavBar
import com.example.aistock_prices.base.BasePager
import com.example.aistock_prices.base.bridgeModule
import com.example.aistock_prices.stock.ui.StockColors
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.module.RouterModule

/**
 * AI 服务设置独立页面（从详情页「去设置」跳转进入）。
 * 表单本体复用 [AiConfigView]，保存成功后关闭本页返回。
 */
@Page("ai_config", supportInLocal = true)
internal class AiConfigPage : BasePager() {

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                flex(1f)
                backgroundColor(StockColors.BG_PAGE)
            }

            RouterNavBar {
                attr {
                    title = "AI 服务设置"
                    backDisable = false
                }
            }

            AiConfigView {
                event {
                    onSaved = {
                        ctx.bridgeModule.toast("配置已保存")
                        ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                    }
                    onInvalid = { msg ->
                        ctx.bridgeModule.toast(msg)
                    }
                }
            }
        }
    }
}
