package com.example.aistock_prices.stock.ai

import com.example.aistock_prices.base.BasePager
import com.example.aistock_prices.base.bridgeModule
import com.example.aistock_prices.stock.ui.ThemePalette
import com.example.aistock_prices.stock.ui.ThemePalettes
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Switch
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * AI 服务配置视图（首页 ☰ 菜单「AI 设置」与 ai_config 独立页共用）。
 *
 * 展示预设列表：
 * - 每项：预设名 + 模型/URL 摘要 + 「当前」生效标记 + 独立启用开关 + 删除
 * - 启用开关：启用即设为当前生效配置；停用则保留在列表中
 * - 连接失败（failed）的预设标红展示，提醒修正
 * - 点击预设行进入预设详情页编辑；列表底端「新建预设」进入新建页
 * - 配置表单（URL / Key / 模型 / 连接测试）已迁移至 [PresetDetailPage]
 */
internal class AiConfigView : ComposeView<AiConfigViewAttr, AiConfigViewEvent>() {

    private var presets by observableList<AiPreset>()
    /** 待删除的预设（删除确认弹窗） */
    private var pendingDelete by observable<AiPreset?>(null)

    /** 当前主题色板：按宿主页面 isNightMode 取亮/暗色板（本组件非 BasePager 子类，故自行取值） */
    private val pal: ThemePalette
        get() = ThemePalettes.of((getPager() as? BasePager)?.isNightMode() ?: false)

    override fun createAttr(): AiConfigViewAttr = AiConfigViewAttr()

    override fun createEvent(): AiConfigViewEvent = AiConfigViewEvent()

    private val sp: SharedPreferencesModule
        get() = getPager().acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                flex(1f)
                backgroundColor(ctx.pal.bgPage)
            }
            View {
                attr {
                    flex(1f)
                    padding(16f)
                }
                Scroller {
                    attr {
                        flex(1f)
                        showScrollerIndicator(false)
                    }

                // ---------- 预设列表 ----------
                vif({ ctx.presets.isNotEmpty() }) {
                    Text {
                        attr {
                            text("我的预设（点击进入编辑 · 开关启用/停用）")
                            fontSize(13f)
                            color(ctx.pal.textSub)
                            marginTop(4f)
                            marginBottom(8f)
                        }
                    }
                    ctx.presets.forEach { preset ->
                        ctx.presetRow(preset).invoke(this)
                    }
                }
                velse {
                    View {
                        attr {
                            marginTop(40f)
                            allCenter()
                        }
                        Text {
                            attr {
                                text("还没有预设，点击下方「新建预设」开始配置")
                                fontSize(13f)
                                color(ctx.pal.textSub)
                            }
                        }
                    }
                }

                // ---------- 新建预设 ----------
                View {
                    attr {
                        marginTop(16f)
                        height(44f)
                        borderRadius(22f)
                        allCenter()
                        backgroundColor(ctx.pal.accentChipBg)
                        border(Border(1f, BorderStyle.SOLID, ctx.pal.accent))
                    }
                    Text {
                        attr {
                            text("＋ 新建预设")
                            fontSize(15f)
                            color(ctx.pal.accent)
                            fontWeightSemiBold()
                        }
                    }
                    event {
                        click { ctx.openPresetDetail(null) }
                    }
                }

                Text {
                    attr {
                        text("提示：保存预设时自动校验连接，失败会标红。")
                        fontSize(12f)
                        color(ctx.pal.textSub)
                        marginTop(12f)
                        marginBottom(24f)
                    }
                }
            }
            }  // 闭合外层 View 容器

            // ---------- 删除预设确认弹窗 ----------
            vif({ ctx.pendingDelete != null }) {
                Modal {
                    View {
                        attr {
                            flex(1f)
                            allCenter()
                            backgroundColor(ctx.pal.maskFull)
                        }
                        event {
                            click { ctx.pendingDelete = null }
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
                                    text("删除预设")
                                    fontSize(16f)
                                    fontWeightSemiBold()
                                    color(ctx.pal.textMain)
                                    marginBottom(10f)
                                }
                            }
                            Text {
                                attr {
                                    text("确定删除预设「${ctx.pendingDelete?.name}」吗？")
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
                                        click { ctx.pendingDelete = null }
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
                                            ctx.pendingDelete?.let { preset ->
                                                AiAnalysisService.removePreset(ctx.sp, preset)
                                                ctx.reloadPresets()
                                            }
                                            ctx.pendingDelete = null
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

    /** 单个预设行 */
    private fun presetRow(preset: AiPreset): ViewBuilder {
        val ctx = this
        val isActive = preset.name == AiAnalysisService.activePresetName(sp)
        return {
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    marginBottom(8f)
                    padding(12f)
                    borderRadius(8f)
                    backgroundColor(ctx.pal.card)
                    // 连接失败标红边框
                    border(
                        Border(
                            1f,
                            BorderStyle.SOLID,
                            if (preset.failed) Color(0xFFF5222D) else ctx.pal.divider
                        )
                    )
                }
                // 左列：名称 + 摘要（点击进入详情页）
                View {
                    attr {
                        flex(1f)
                        flexDirectionColumn()
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        Text {
                            attr {
                                text(preset.name)
                                fontSize(14f)
                                fontWeightSemiBold()
                                color(
                                    if (preset.enabled) ctx.pal.textMain
                                    else Color(0xFFBFBFBF)
                                )
                            }
                        }
                        // 当前生效标记
                        vif({ isActive }) {
                            View {
                                attr {
                                    marginLeft(6f)
                                    paddingLeft(6f)
                                    paddingRight(6f)
                                    paddingTop(2f)
                                    paddingBottom(2f)
                                    borderRadius(4f)
                                    backgroundColor(ctx.pal.accentChipBg)
                                }
                                Text {
                                    attr {
                                        text("当前")
                                        fontSize(10f)
                                        color(ctx.pal.accent)
                                    }
                                }
                            }
                        }
                        // 连接失败标记
                        vif({ preset.failed }) {
                            View {
                                attr {
                                    marginLeft(6f)
                                    paddingLeft(6f)
                                    paddingRight(6f)
                                    paddingTop(2f)
                                    paddingBottom(2f)
                                    borderRadius(4f)
                                    backgroundColor(Color(0xFFFFF1F0))
                                }
                                Text {
                                    attr {
                                        text("连接失败")
                                        fontSize(10f)
                                        color(Color(0xFFF5222D))
                                    }
                                }
                            }
                        }
                    }
                    Text {
                        attr {
                            text("${preset.model} · ${preset.baseUrl}")
                            fontSize(11f)
                            color(
                                if (preset.failed) Color(0xFFF5222D)
                                else ctx.pal.textSub
                            )
                            marginTop(3f)
                        }
                    }
                    event {
                        click { ctx.openPresetDetail(preset) }
                    }
                }
                // 启用开关
                Switch {
                    attr {
                        isOn(preset.enabled)
                        onColor(ctx.pal.accent)
                        unOnColor(ctx.pal.chip2Bg)
                        thumbColor(ctx.pal.onAccent)
                        width(44f)
                        height(26f)
                    }
                    event {
                        switchOnChanged { on ->
                            ctx.togglePreset(preset, on)
                        }
                    }
                }
                // 删除
                View {
                    attr {
                        marginLeft(8f)
                        padding(8f)
                    }
                    Text {
                        attr {
                            text("删除")
                            fontSize(12f)
                            color(ctx.pal.up)
                        }
                    }
                    event {
                        click { ctx.pendingDelete = preset }
                    }
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        reloadPresets()
    }

    /** 外部刷新入口（页面重新出现时调用）：从 SP 重载预设列表 */
    fun reload() {
        reloadPresets()
    }

    /** 启用/停用预设：启用时自动关闭其他预设并设为当前生效配置（互斥） */
    private fun togglePreset(preset: AiPreset, enabled: Boolean) {
        // 连接失败的预设不允许启用（Service 层也有防护，这里拦截并提示）
        if (enabled && preset.failed) {
            reloadPresets()
            bridgeToast("连接失败的预设无法启用，请先编辑修正配置")
            return
        }
        AiAnalysisService.setPresetEnabled(sp, preset.name, enabled)
        reloadPresets()
        if (enabled) {
            bridgeToast("已启用「${preset.name}」，其他预设已自动停用")
        } else {
            bridgeToast("已停用「${preset.name}」")
        }
    }

    /** 进入预设详情页（新建传 null，编辑传预设） */
    private fun openPresetDetail(preset: AiPreset?) {
        val pageData = JSONObject().apply {
            preset?.let { put("name", it.name) }
        }
        getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME)
            .openPage("preset_detail", pageData)
    }

    private fun reloadPresets() {
        presets.clear()
        presets.addAll(AiAnalysisService.loadPresets(sp))
    }

    private fun bridgeToast(msg: String) {
        val bridge = getPager().acquireModule<com.example.aistock_prices.base.BridgeModule>(
            com.example.aistock_prices.base.BridgeModule.MODULE_NAME
        )
        bridge.toast(msg)
    }
}

internal class AiConfigViewAttr : ComposeAttr()

internal class AiConfigViewEvent : ComposeEvent() {
    /** 兼容保留：详情页保存动作在 preset_detail 内完成，本视图不再触发 */
    var onSaved: (() -> Unit)? = null
    var onInvalid: ((String) -> Unit)? = null
}

internal fun ViewContainer<*, *>.AiConfigView(init: AiConfigView.() -> Unit) {
    addChild(AiConfigView(), init)
}
