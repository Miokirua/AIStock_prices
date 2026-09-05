package com.example.aistock_prices.stock.ai

import com.example.aistock_prices.RouterNavBar
import com.example.aistock_prices.base.BasePager
import com.example.aistock_prices.base.bridgeModule
import com.example.aistock_prices.base.setTimeout
import com.example.aistock_prices.stock.ui.ThemePalette
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 预设详情页（新建 / 编辑共用）：
 * - 从 AI 设置列表「新建预设」进入（无 name 参数）或点击预设进入（带 name 参数）
 * - 配置预设名 / Base URL / API Key / 模型
 * - 「连接」按钮测试连接并拉取可用模型下拉（点选后收起）
 * - 保存时自动校验连接：失败则保存并标红该预设（列表展示），成功则保存并生效
 */
@Page("preset_detail", supportInLocal = true)
internal class PresetDetailPage : BasePager() {

    private var presetName by observable("")
    private var baseUrl by observable("")
    private var apiKey by observable("")
    private var model by observable("")
    /** API Key 是否明文显示（false=密码态 * 掩码；true=明文） */
    private var apiKeyVisible by observable(false)
    /** 连接测试中 */
    private var connecting by observable(false)
    /** 连接测试结果信息（空 = 未测试） */
    private var connectMsg by observable("")
    /** 连接测试成功（用于信息条颜色） */
    private var connectOk by observable(false)
    /** 可用模型列表 */
    private var models by observableList<String>()
    /** 模型下拉是否展开 */
    private var modelsVisible by observable(false)
    /** 保存校验中 */
    private var saving by observable(false)

    private lateinit var nameRef: ViewRef<InputView>
    private lateinit var urlRef: ViewRef<InputView>
    private lateinit var keyRef: ViewRef<InputView>
    private lateinit var modelRef: ViewRef<InputView>

    /** 编辑模式下的原预设名（空 = 新建） */
    private val editingName: String get() = pagerData.params.optString("name")

    private val sp: SharedPreferencesModule
        get() = acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)

    private val network: NetworkModule
        get() = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                flex(1f)
                backgroundColor(ctx.pal.bgPage)
            }

            RouterNavBar {
                attr {
                    title = if (ctx.editingName.isNotBlank()) "编辑预设" else "新建预设"
                    backDisable = false
                }
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

                    // ---------- 表单 ----------
                    fieldLabel("预设名（可选，默认取模型名）", ctx.pal)
                    inputField({ ctx.nameRef = it }, "我的预设名", 20, ctx.pal, { ctx.presetName }) { ctx.presetName = it }

                    fieldLabel("API Base URL（OpenAI 兼容）", ctx.pal)
                    inputField({ ctx.urlRef = it }, "https://api.xxx.com/v1", 100, ctx.pal, { ctx.baseUrl }) { ctx.baseUrl = it }

                    fieldLabel("API Key", ctx.pal)
                    // 密码态输入：默认 * 掩码显示，眼睛按钮切换明文/密文（vif 双 Input 重建，文本由 apiKey 状态恢复）
                    vif({ !ctx.apiKeyVisible }) {
                        ctx.keyFieldRow(true).invoke(this)
                    }
                    velse {
                        ctx.keyFieldRow(false).invoke(this)
                    }

                    // ---------- 连接测试 ----------
                    View {
                        attr {
                            marginTop(18f)
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        View {
                            attr {
                                height(40f)
                                paddingLeft(20f)
                                paddingRight(20f)
                                borderRadius(20f)
                                allCenter()
                                flexDirectionRow()
                                alignItemsCenter()
                                backgroundColor(ctx.pal.accent)
                            }
                            vif({ ctx.connecting }) {
                                ActivityIndicator {
                                    attr {
                                        isGrayStyle(false)
                                    }
                                }
                            }
                            Text {
                                attr {
                                    text(if (ctx.connecting) "  测试中..." else "连接测试")
                                    fontSize(14f)
                                    color(ctx.pal.onAccent)
                                    fontWeightSemiBold()
                                }
                            }
                            event {
                                click { ctx.connect() }
                            }
                        }
                        vif({ ctx.connectMsg.isNotEmpty() }) {
                            Text {
                                attr {
                                    flex(1f)
                                    text(ctx.connectMsg)
                                    fontSize(12f)
                                    marginLeft(10f)
                                    color(if (ctx.connectOk) Color(0xFF389E0D) else ctx.pal.errRed)
                                }
                            }
                        }
                    }

                    // ---------- 可用模型下拉（连接成功后展示，点选后收起） ----------
                    vif({ ctx.modelsVisible }) {
                        View {
                            attr {
                                marginTop(10f)
                                borderRadius(8f)
                                backgroundColor(ctx.pal.card)
                                border(Border(1f, BorderStyle.SOLID, ctx.pal.divider))
                            }
                            vif({ ctx.models.isEmpty() }) {
                                View {
                                    attr {
                                        padding(10f)
                                    }
                                    Text {
                                        attr {
                                            text("未获取到模型，请检查 URL / Key 后重试")
                                            fontSize(13f)
                                            color(ctx.pal.textSub)
                                        }
                                    }
                                }
                            }
                            ctx.models.forEach { m ->
                                View {
                                    attr {
                                        paddingTop(10f)
                                        paddingBottom(10f)
                                        paddingLeft(10f)
                                        paddingRight(10f)
                                    }
                                    Text {
                                        attr {
                                            text(m)
                                            fontSize(14f)
                                            color(ctx.pal.textMain)
                                        }
                                    }
                                    event {
                                        click {
                                            // 选完模型后收起下拉选项
                                            ctx.model = m
                                            ctx.modelRef.view?.setText(m)
                                            ctx.modelsVisible = false
                                            ctx.models.clear()
                                        }
                                    }
                                }
                                View {
                                    attr {
                                        height(1f)
                                        backgroundColor(ctx.pal.divider)
                                    }
                                }
                            }
                        }
                    }

                    // ---------- 模型名 ----------
                    fieldLabel("模型名", ctx.pal)
                    inputField({ ctx.modelRef = it }, "deepseek-chat", 64, ctx.pal, { ctx.model }) { ctx.model = it }

                    // ---------- 保存 ----------
                    View {
                        attr {
                            marginTop(24f)
                            height(44f)
                            borderRadius(22f)
                            allCenter()
                            backgroundLinearGradient(
                                Direction.TO_RIGHT,
                                ColorStop(ctx.pal.accent, 0f),
                                ColorStop(Color(0xFF6A5AFF), 1f)
                            )
                        }
                        Text {
                            attr {
                                text(if (ctx.saving) "校验中..." else "保存配置")
                                fontSize(16f)
                                color(ctx.pal.onAccent)
                                fontWeightSemiBold()
                            }
                        }
                        event {
                            click { ctx.save() }
                        }
                    }

                    Text {
                        attr {
                            text("提示：保存时自动校验连接，失败会在预设列表中标红。Key 保存在本地明文（shared_prefs），仅用于原型演示。")
                            fontSize(12f)
                            color(ctx.pal.textSub)
                            marginTop(12f)
                            marginBottom(24f)
                        }
                    }
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        val edit = editingName
        if (edit.isNotBlank()) {
            val preset = AiAnalysisService.loadPresets(sp).firstOrNull { it.name == edit }
            if (preset != null) {
                presetName = preset.name
                baseUrl = preset.baseUrl
                apiKey = preset.apiKey
                model = preset.model
            }
        }
        // 回填输入框（body 已执行，ref 可用）
        nameRef.view?.setText(presetName)
        urlRef.view?.setText(baseUrl)
        keyRef.view?.setText(apiKey)
        modelRef.view?.setText(model)
    }

    /** 切换 API Key 明文/密文：vif 会重建 Input，延迟一拍把当前值回填到新实例（ref 已指向新视图） */
    private fun toggleApiKeyVisible() {
        apiKeyVisible = !apiKeyVisible
        setTimeout(50) { keyRef.view?.setText(apiKey) }
    }

    /** API Key 输入行（isPassword=true 密文态 * 掩码 / false 明文态）；随 vif 分支创建，互不共享实例 */
    private fun keyFieldRow(isPassword: Boolean): ViewBuilder = {
        val ctx = this@PresetDetailPage
        View {
            attr {
                height(44f)
                borderRadius(8f)
                backgroundColor(ctx.pal.card)
                paddingLeft(12f)
                paddingRight(4f)
                flexDirectionRow()
                alignItemsCenter()
                border(Border(1f, BorderStyle.SOLID, ctx.pal.divider))
            }
            Input {
                ref { ctx.keyRef = it }
                attr {
                    flex(1f)
                    height(40f)
                    fontSize(14f)
                    color(ctx.pal.textMain)
                    placeholder("sk-...")
                    placeholderColor(ctx.pal.textSub)
                    maxTextLength(128)
                    if (isPassword) keyboardTypePassword()
                }
                event {
                    textDidChange { ctx.apiKey = it.text }
                }
            }
            // 眼睛：密文态显示「👁」（点按查看明文）；明文态显示「🙈」（点按恢复密文）
            View {
                attr {
                    padding(10f)
                }
                Text {
                    attr {
                        text(if (isPassword) "👁" else "🙈")
                        fontSize(15f)
                    }
                }
                event {
                    click { ctx.toggleApiKeyVisible() }
                }
            }
            // 一键清空（叉）：内容非空时显示
            vif({ ctx.apiKey.isNotEmpty() }) {
                View {
                    attr {
                        padding(10f)
                    }
                    Text {
                        attr {
                            text("✕")
                            fontSize(15f)
                            color(ctx.pal.textSub)
                        }
                    }
                    event {
                        click {
                            ctx.keyRef.view?.setText("")
                            ctx.apiKey = ""
                        }
                    }
                }
            }
        }
    }

    /** 连接测试：请求 models 接口，成功展示可用模型下拉 */
    private fun connect() {
        val url = baseUrl.trim()
        val key = apiKey.trim()
        if (url.isBlank() || key.isBlank()) {
            bridgeModule.toast("请先填写 Base URL 与 API Key")
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            bridgeModule.toast("Base URL 需以 http:// 或 https:// 开头")
            return
        }
        connecting = true
        connectMsg = ""
        models.clear()
        modelsVisible = false
        AiAnalysisService.fetchModels(network, url, key) { list, err ->
            connecting = false
            if (err == null) {
                connectOk = true
                connectMsg = "连接成功，共 ${list.size} 个模型"
                models.clear()
                models.addAll(list)
                modelsVisible = true
            } else {
                connectOk = false
                connectMsg = "连接失败：$err"
                modelsVisible = false
            }
        }
    }

    /** 保存：先校验格式，再自动校验连接；成功才启用该预设，失败则保存并标红但不启用 */
    private fun save() {
        if (saving) return
        val url = baseUrl.trim()
        val key = apiKey.trim()
        val mdl = model.trim()
        if (url.isBlank() || key.isBlank() || mdl.isBlank()) {
            bridgeModule.toast("请完整填写 Base URL、API Key 与模型名")
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            bridgeModule.toast("Base URL 需以 http:// 或 https:// 开头")
            return
        }
        val nm = presetName.trim().ifBlank { mdl }
        val edit = editingName
        if (AiAnalysisService.loadPresets(sp).any { it.name == nm && it.name != edit }) {
            bridgeModule.toast("预设名「$nm」已存在，请更换")
            return
        }
        saving = true
        AiAnalysisService.testConnection(network, url, key) { ok, err ->
            saving = false
            // 连接成功才启用（failed 预设强制不启用，与列表开关互斥逻辑一致）
            val newPreset = AiPreset(nm, url, key, mdl, enabled = ok, failed = !ok)
            if (edit.isNotBlank()) {
                AiAnalysisService.updatePreset(sp, edit, newPreset)
            } else {
                AiAnalysisService.upsertPreset(sp, AiConfig(url, key, mdl), nm, enabled = ok)
                AiAnalysisService.setPresetFailed(sp, nm, !ok)
            }
            if (ok) {
                bridgeModule.toast("已保存并连接成功")
            } else {
                bridgeModule.toast("连接失败（$err），已保存并标红，请检查配置")
            }
            acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
        }
    }
}

// ==================== 表单小部件 ====================

private fun ViewContainer<*, *>.fieldLabel(text: String, pal: ThemePalette) {
    Text {
        attr {
            text(text)
            fontSize(13f)
            color(pal.textSub)
            marginTop(16f)
            marginBottom(8f)
        }
    }
}

private fun ViewContainer<*, *>.inputField(
    refSetter: (ViewRef<InputView>) -> Unit,
    placeholder: String,
    maxLen: Int,
    pal: ThemePalette,
    value: () -> String,
    onTextChange: (String) -> Unit
) {
    var inputRef: ViewRef<InputView>? = null
    View {
        attr {
            height(44f)
            borderRadius(8f)
            backgroundColor(pal.card)
            paddingLeft(12f)
            paddingRight(4f)
            flexDirectionRow()
            alignItemsCenter()
            border(Border(1f, BorderStyle.SOLID, pal.divider))
        }
        Input {
            ref {
                inputRef = it
                refSetter(it)
            }
            attr {
                flex(1f)
                height(40f)
                fontSize(14f)
                color(pal.textMain)
                placeholder(placeholder)
                placeholderColor(pal.textSub)
                maxTextLength(maxLen)
            }
            event {
                textDidChange { onTextChange(it.text) }
            }
        }
        // 一键清空（叉）：内容非空时显示
        vif({ value().isNotEmpty() }) {
            View {
                attr {
                    padding(10f)
                }
                Text {
                    attr {
                        text("✕")
                        fontSize(15f)
                        color(pal.textSub)
                    }
                }
                event {
                    click {
                        inputRef?.view?.setText("")
                        onTextChange("")
                    }
                }
            }
        }
    }
}
