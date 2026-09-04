package com.example.aistock_prices.stock.ai

import com.example.aistock_prices.RouterNavBar
import com.example.aistock_prices.base.BasePager
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder

/**
 * AI 服务设置独立页面（从 ☰ 菜单 / AI 问答「去设置」跳转进入）。
 * 展示预设列表；新建/编辑预设跳转 [PresetDetailPage]。
 */
@Page("ai_config", supportInLocal = true)
internal class AiConfigPage : BasePager() {

    private var configView: AiConfigView? = null

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                flex(1f)
                backgroundColor(ctx.pal.bgPage)
            }

            RouterNavBar {
                attr {
                    title = "AI 服务设置"
                    backDisable = false
                }
            }

            AiConfigView {
                ctx.configView = this
            }
        }
    }

    /** 页面每次出现（含从预设详情页保存返回）时刷新预设列表 */
    override fun pageDidAppear() {
        super.pageDidAppear()
        configView?.reload()
    }
}
