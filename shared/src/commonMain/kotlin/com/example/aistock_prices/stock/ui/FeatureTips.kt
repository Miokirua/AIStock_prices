package com.example.aistock_prices.stock.ui

import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 就地功能提示（v1.9.31）。
 *
 * 与首启引导（StockListPage 里那套 10 步挖孔遮罩）分工：
 * - 首启引导：只在没看过时弹一次，讲「这个 App 有哪些模块」，锚点都在自选列表页；
 * - 就地提示：[featureTipBar] 挂在具体功能区旁边，讲「当前这一块怎么用」，
 *   点「知道了」写 SP 后永不再出现，不打扰。
 *
 * 已装过旧版本的用户没有首启引导弹窗（`guide_intro_seen` 已置位），
 * 但这里每个 key 都是新增的，所以他们会在对应页面自然看到提示 —— 这正是本次改动的目的。
 */
object FeatureTips {
    /** 自选分组：分组条切组 / ＋新建 / 长按分组名 / 长按股票移动（StockListPage） */
    const val GROUP = "group"
    /** 表头排序（StockListPage） */
    const val SORT = "sort"
    /** K 线手势 + 关键位备忘（StockDetailPage） */
    const val CHART = "chart"
    /** AI 消息长按菜单 / 快捷问句（AiChatView） */
    const val AI_MSG = "ai_msg"

    /** 逐条重置时需要遍历的全部 key */
    val ALL = listOf(GROUP, SORT, CHART, AI_MSG)

    private const val PREFIX = "feature_tip_"

    /** 是否已看过（值为 "1" 表示看过） */
    fun isSeen(sp: SharedPreferencesModule, key: String): Boolean =
        sp.getItem(PREFIX + key) == "1"

    /** 标记已看过 */
    fun markSeen(sp: SharedPreferencesModule, key: String) {
        sp.setItem(PREFIX + key, "1")
    }

    /**
     * 重置全部提示（「功能说明」重看引导时一并调用，方便重新体验新功能提示）。
     * ⚠️ 框架的 SharedPreferencesModule 没有 removeItem，写空串即视为未看过。
     */
    fun resetAll(sp: SharedPreferencesModule) {
        ALL.forEach { sp.setItem(PREFIX + it, "") }
    }
}

/**
 * 一次性提示条：左侧 💡 + 文案，右侧「知道了」。
 *
 * 视觉上刻意做得比正文弱（浅蓝底 + textSub 小字 11.5sp），避免抢走列表主体的注意力。
 * ⚠️ 底色必须用 [ThemePalette.accentChipBg] 而不是 chipBg / card：
 * 浅色下 chipBg == bgPage（#F5F6F8），挂在列表页会直接"隐形"；card 又和详情页的
 * 白卡同色。浅蓝底 + accent 文字在 bgPage / card 两种底色上都能立住。
 *
 * @param onClose 点「知道了」的回调；调用方应在此写 SP 标记并隐藏自己
 */
fun featureTipBar(pal: ThemePalette, text: String, onClose: () -> Unit): ViewBuilder = {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            marginLeft(12f)
            marginRight(12f)
            marginTop(6f)
            marginBottom(4f)
            paddingLeft(10f)
            paddingRight(6f)
            paddingTop(8f)
            paddingBottom(8f)
            borderRadius(10f)
            backgroundColor(pal.accentChipBg)
        }
        Text {
            attr {
                text("💡")
                fontSize(12f)
                color(pal.accent)
                marginRight(6f)
            }
        }
        Text {
            attr {
                flex(1f)
                text(text)
                fontSize(11.5f)
                // ⚠️ 用 textMain 而非 textSub：textSub(#8A8A8A) 在浅蓝底(#F0F5FF)上对比度只有 ~3.1:1，
                // 低于可读线；这条提示是"读一次就懂"的操作说明，可读性优先于视觉弱化。
                color(pal.textMain)
                lineHeight(17f)
            }
        }
        View {
            // ⚠️ 热区必须显式 width/height：只靠 padding 撑开的 View 真机命中区域约等于文字本身
            attr {
                width(54f)
                height(26f)
                allCenter()
                borderRadius(13f)
                backgroundColor(pal.card)
            }
            Text {
                attr {
                    text("知道了")
                    fontSize(11f)
                    color(pal.accent)
                    fontWeightSemiBold()
                }
            }
            event {
                click { onClose() }
            }
        }
    }
}
