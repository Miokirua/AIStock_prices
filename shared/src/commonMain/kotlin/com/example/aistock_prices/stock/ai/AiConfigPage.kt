package com.example.aistock_prices.stock.ai

import com.example.aistock_prices.RouterNavBar
import com.example.aistock_prices.base.BasePager
import com.example.aistock_prices.stock.ui.StockColors
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder

/**
 * AI 服务设置独立页面（从 ☰ 菜单 / AI 问答「去设置」跳转进入）。
 * 展示预设列表；新建/编辑预设跳转 [PresetDetailPage]。
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
                // 保存/编辑动作已迁移至 preset_detail 页，本页仅展示列表
            }
        }
    }
}
