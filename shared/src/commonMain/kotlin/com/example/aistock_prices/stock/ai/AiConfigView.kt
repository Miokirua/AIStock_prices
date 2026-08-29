package com.example.aistock_prices.stock.ai

import com.example.aistock_prices.stock.ui.StockColors
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * AI 服务配置表单组件（可复用）：
 * - 首页底部任务栏「AI 设置」Tab 内联使用
 * - ai_config 独立页面使用
 *
 * 能力：手动填写 Base URL / Key / 模型；按 URL+Key 自动读取可用模型；
 * 保存后自动以模型名创建/更新预设；预设支持一键切换与删除。
 */
internal class AiConfigView : ComposeView<AiConfigViewAttr, AiConfigViewEvent>() {

    private var baseUrl by observable("")
    private var apiKey by observable("")
    private var model by observable("")
    private var presets by observableList<AiPreset>()
    private var showModelsDialog by observable(false)
    private var modelsLoading by observable(false)
    private var models by observableList<String>()
    private lateinit var urlRef: ViewRef<InputView>
    private lateinit var keyRef: ViewRef<InputView>
    private lateinit var modelRef: ViewRef<InputView>

    override fun createAttr(): AiConfigViewAttr = AiConfigViewAttr()

    override fun createEvent(): AiConfigViewEvent = AiConfigViewEvent()

    private val sp: SharedPreferencesModule
        get() = getPager().acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)

    private val network: NetworkModule
        get() = getPager().acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                flex(1f)
                backgroundColor(StockColors.BG_PAGE)
            }
            Scroller {
                attr {
                    flex(1f)
                    padding(16f)
                }

                // ---------- 我的预设 ----------
                vif({ ctx.presets.isNotEmpty() }) {
                    aiFieldLabel("我的预设（点击切换）")
                    ctx.presets.forEach { preset ->
                        View {
                            attr {
                                flexDirectionRow()
                                alignItemsCenter()
                                marginBottom(8f)
                                padding(12f)
                                borderRadius(8f)
                                backgroundColor(Color.WHITE)
                                border(Border(1f, BorderStyle.SOLID, Color(0xFFE4E4E4)))
                            }
                            View {
                                attr {
                                    flex(1f)
                                    flexDirectionColumn()
                                }
                                Text {
                                    attr {
                                        text(preset.name)
                                        fontSize(14f)
                                        fontWeightSemiBold()
                                        color(StockColors.TEXT_MAIN)
                                    }
                                }
                                Text {
                                    attr {
                                        text("${preset.model} · ${preset.baseUrl}")
                                        fontSize(11f)
                                        color(StockColors.TEXT_SUB)
                                        marginTop(3f)
                                    }
                                }
                                event {
                                    click { ctx.applyPreset(preset) }
                                }
                            }
                            View {
                                attr {
                                    padding(6f)
                                    borderRadius(4f)
                                    backgroundColor(Color(0xFFF5F6F8))
                                }
                                Text {
                                    attr {
                                        text("删")
                                        fontSize(12f)
                                        color(StockColors.TEXT_SUB)
                                    }
                                }
                                event {
                                    click {
                                        AiAnalysisService.removePreset(ctx.sp, preset)
                                        ctx.reloadPresets()
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------- 表单 ----------
                aiFieldLabel("API Base URL（OpenAI 兼容）")
                aiInputField({ ctx.urlRef = it }, "https://api.xxx.com/v1/chat/completions") {
                    ctx.baseUrl = it
                }
                aiFieldLabel("API Key")
                aiInputField({ ctx.keyRef = it }, "sk-...") {
                    ctx.apiKey = it
                }
                aiFieldLabel("模型名")
                aiInputField({ ctx.modelRef = it }, "deepseek-chat") {
                    ctx.model = it
                }

                // ---------- 获取模型列表 ----------
                View {
                    attr {
                        marginTop(12f)
                        height(40f)
                        borderRadius(20f)
                        allCenter()
                        backgroundColor(StockColors.ACCENT)
                    }
                    Text {
                        attr {
                            text("按 URL + Key 获取可用模型")
                            fontSize(14f)
                            color(Color.WHITE)
                            fontWeightSemiBold()
                        }
                    }
                    event {
                        click { ctx.fetchModels() }
                    }
                }

                // ---------- 保存 ----------
                View {
                    attr {
                        marginTop(24f)
                        height(44f)
                        borderRadius(22f)
                        allCenter()
                        backgroundLinearGradient(
                            Direction.TO_RIGHT,
                            ColorStop(StockColors.ACCENT, 0f),
                            ColorStop(Color(0xFF6A5AFF), 1f)
                        )
                    }
                    Text {
                        attr {
                            text("保存配置")
                            fontSize(16f)
                            color(Color.WHITE)
                            fontWeightSemiBold()
                        }
                    }
                    event {
                        click { ctx.save() }
                    }
                }

                Text {
                    attr {
                        text("提示：Key 与预设保存在本地明文（/data/data/.../shared_prefs），仅用于原型演示，请勿用于生产环境。")
                        fontSize(12f)
                        color(StockColors.TEXT_SUB)
                        marginTop(12f)
                        marginBottom(24f)
                    }
                }
            }

            // ---------- 模型列表弹层 ----------
            vif({ ctx.showModelsDialog }) {
                Modal {
                    View {
                        attr {
                            flex(1f)
                            allCenter()
                            backgroundColor(Color(0x66000000))
                        }
                        View {
                            attr {
                                width(600f)
                                maxHeight(900f)
                                borderRadius(12f)
                                backgroundColor(Color.WHITE)
                                padding(20f)
                            }
                            Text {
                                attr {
                                    text(if (ctx.modelsLoading) "正在获取模型..." else "选择模型（${ctx.models.size} 个）")
                                    fontSize(16f)
                                    fontWeightSemiBold()
                                    color(StockColors.TEXT_MAIN)
                                    marginBottom(14f)
                                }
                            }
                            vif({ ctx.models.isEmpty() && !ctx.modelsLoading }) {
                                Text {
                                    attr {
                                        text("未获取到模型，请检查 URL / Key 是否正确")
                                        fontSize(13f)
                                        color(StockColors.TEXT_SUB)
                                        marginBottom(10f)
                                    }
                                }
                            }
                            View {
                                attr {
                                    flexDirectionRow()
                                    flexWrapWrap()
                                }
                                ctx.models.forEach { m ->
                                    View {
                                        attr {
                                            marginRight(8f)
                                            marginBottom(8f)
                                            paddingTop(6f)
                                            paddingBottom(6f)
                                            paddingLeft(12f)
                                            paddingRight(12f)
                                            borderRadius(16f)
                                            backgroundColor(Color(0xFFF0F5FF))
                                        }
                                        Text {
                                            attr {
                                                text(m)
                                                fontSize(13f)
                                                color(StockColors.ACCENT)
                                            }
                                        }
                                        event {
                                            click {
                                                ctx.model = m
                                                ctx.modelRef.view?.setText(m)
                                                ctx.showModelsDialog = false
                                            }
                                        }
                                    }
                                }
                            }
                            View {
                                attr {
                                    marginTop(10f)
                                    height(38f)
                                    borderRadius(19f)
                                    allCenter()
                                    backgroundColor(Color(0xFFF0F0F0))
                                }
                                Text {
                                    attr {
                                        text("关闭")
                                        fontSize(14f)
                                        color(StockColors.TEXT_SUB)
                                    }
                                }
                                event {
                                    click { ctx.showModelsDialog = false }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        val config = AiAnalysisService.loadConfig(sp)
        baseUrl = config.baseUrl
        apiKey = config.apiKey
        model = config.model
        urlRef.view?.setText(config.baseUrl)
        keyRef.view?.setText(config.apiKey)
        modelRef.view?.setText(config.model)
        reloadPresets()
    }

    /** 一键切换预设（立即生效并保存） */
    private fun applyPreset(preset: AiPreset) {
        baseUrl = preset.baseUrl
        apiKey = preset.apiKey
        model = preset.model
        urlRef.view?.setText(preset.baseUrl)
        keyRef.view?.setText(preset.apiKey)
        modelRef.view?.setText(preset.model)
        AiAnalysisService.saveConfig(sp, AiConfig(preset.baseUrl, preset.apiKey, preset.model))
        event.onSaved?.invoke()
    }

    /** 按 URL+Key 自动读取模型列表 */
    private fun fetchModels() {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            event.onInvalid?.invoke("请先填写 Base URL 与 API Key")
            return
        }
        showModelsDialog = true
        modelsLoading = true
        models.clear()
        AiAnalysisService.fetchModels(network, baseUrl, apiKey) { list ->
            modelsLoading = false
            models.clear()
            models.addAll(list)
        }
    }

    private fun reloadPresets() {
        presets.clear()
        presets.addAll(AiAnalysisService.loadPresets(sp))
    }

    private fun save() {
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            event.onInvalid?.invoke("请完整填写 Base URL、API Key 与模型名")
            return
        }
        val config = AiConfig(baseUrl, apiKey, model)
        AiAnalysisService.saveConfig(sp, config)
        // 自动以模型名创建/更新预设
        AiAnalysisService.upsertPreset(sp, config)
        reloadPresets()
        event.onSaved?.invoke()
    }
}

internal class AiConfigViewAttr : ComposeAttr()

internal class AiConfigViewEvent : ComposeEvent() {
    var onSaved: (() -> Unit)? = null
    var onInvalid: ((String) -> Unit)? = null
}

internal fun ViewContainer<*, *>.AiConfigView(init: AiConfigView.() -> Unit) {
    addChild(AiConfigView(), init)
}

// ==================== 表单小部件（直接向当前容器添加子节点） ====================

private fun ViewContainer<*, *>.aiFieldLabel(text: String) {
    Text {
        attr {
            text(text)
            fontSize(13f)
            color(StockColors.TEXT_SUB)
            marginTop(16f)
            marginBottom(8f)
        }
    }
}

private fun ViewContainer<*, *>.aiInputField(
    refSetter: (ViewRef<InputView>) -> Unit,
    placeholder: String,
    onTextChange: (String) -> Unit
) {
    View {
        attr {
            height(44f)
            borderRadius(8f)
            backgroundColor(Color.WHITE)
            paddingLeft(12f)
            paddingRight(12f)
            justifyContentCenter()
            border(Border(1f, BorderStyle.SOLID, Color(0xFFE4E4E4)))
        }
        Input {
            ref { refSetter(it) }
            attr {
                flex(1f)
                fontSize(14f)
                color(StockColors.TEXT_MAIN)
                placeholder(placeholder)
                placeholderColor(StockColors.TEXT_SUB)
            }
            event {
                textDidChange { onTextChange(it.text) }
            }
        }
    }
}
