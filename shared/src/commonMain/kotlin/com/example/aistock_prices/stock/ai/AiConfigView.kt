package com.example.aistock_prices.stock.ai

import com.example.aistock_prices.base.setTimeout
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
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.clearTimeout
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
 * 能力：手动填写预设名 / Base URL / Key / 模型；
 * URL/Key 填写后自动获取可用模型列表，常驻显示在模型名输入框下方供点选；
 * 预设支持一键切换、编辑（回填表单）与删除；保存后自动以预设名/模型名创建或更新预设。
 */
internal class AiConfigView : ComposeView<AiConfigViewAttr, AiConfigViewEvent>() {

    private var baseUrl by observable("")
    private var apiKey by observable("")
    private var model by observable("")
    private var presetName by observable("")
    /** 正在编辑的预设名（非空 = 编辑模式，显示提示条） */
    private var editingPresetName by observable("")
    /** 当前表单内容来源的预设名（切换预设/编辑时记录，保存时优先更新该预设而非新建） */
    private var activePresetName by observable("")
    /** 待删除的预设（删除确认弹窗） */
    private var pendingDelete by observable<AiPreset?>(null)
    private var presets by observableList<AiPreset>()
    private var modelsLoading by observable(false)
    private var models by observableList<String>()
    /** 获取模型失败原因（空 = 无错误） */
    private var modelsError by observable("")
    /** 自动获取模型的防抖定时器引用 */
    private var fetchTimerRef = ""
    private lateinit var nameRef: ViewRef<InputView>
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
            // Scroller 自身不要写 padding（Kuikly 的 Scroller padding 需要在
            // addChild 时通过 initScrollerContentComponentIfNeed 复制到 contentView，
            // 某些场景下会出现左/右 padding 不一致导致子元素整体错位/超出可点击区域）。
            // 改为外层 View 容器包 Scroller，padding 写在外层 View 上，行为最确定。
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

                // ---------- 我的预设 ----------
                vif({ ctx.presets.isNotEmpty() }) {
                    aiFieldLabel("我的预设（点击切换 · 可编辑）")
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
                                    backgroundColor(Color(0xFFF0F5FF))
                                    marginRight(6f)
                                }
                                Text {
                                    attr {
                                        text("编辑")
                                        fontSize(12f)
                                        color(StockColors.ACCENT)
                                    }
                                }
                                event {
                                    click { ctx.startEdit(preset) }
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
                                    click { ctx.pendingDelete = preset }
                                }
                            }
                        }
                    }
                }

                // ---------- 编辑模式提示条 ----------
                vif({ ctx.editingPresetName.isNotEmpty() }) {
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            marginTop(16f)
                            padding(10f)
                            borderRadius(8f)
                            backgroundColor(Color(0xFFF0F5FF))
                        }
                        Text {
                            attr {
                                flex(1f)
                                text("正在编辑预设：${ctx.editingPresetName}")
                                fontSize(13f)
                                color(StockColors.ACCENT)
                            }
                        }
                        Text {
                            attr {
                                text("取消编辑")
                                fontSize(13f)
                                color(StockColors.TEXT_SUB)
                                textDecorationUnderLine()
                            }
                            event {
                                click {
                                    ctx.editingPresetName = ""
                                    ctx.presetName = ""
                                    ctx.nameRef.view?.setText("")
                                }
                            }
                        }
                    }
                }

                // ---------- 表单 ----------
                aiFieldLabel("预设名（可选，默认取模型名）")
                aiInputField({ ctx.nameRef = it }, "我的预设名") {
                    ctx.presetName = it
                }
                aiFieldLabel("API Base URL（OpenAI 兼容）")
                aiInputField({ ctx.urlRef = it }, "https://api.xxx.com/v1/chat/completions") {
                    ctx.baseUrl = it
                    ctx.scheduleFetchModels()
                }
                aiFieldLabel("API Key")
                aiInputField({ ctx.keyRef = it }, "sk-...") {
                    ctx.apiKey = it
                    ctx.scheduleFetchModels()
                }
                aiFieldLabel("模型名")
                aiInputField({ ctx.modelRef = it }, "deepseek-chat") {
                    ctx.model = it
                }

                // ---------- 可用模型（URL/Key 填写后自动获取，点选填入） ----------
                vif({ ctx.modelsLoading || ctx.models.isNotEmpty() || ctx.modelsError.isNotEmpty() }) {
                    View {
                        attr {
                            marginTop(8f)
                            borderRadius(8f)
                            backgroundColor(Color.WHITE)
                            border(Border(1f, BorderStyle.SOLID, Color(0xFFE4E4E4)))
                            padding(8f)
                        }
                        vif({ ctx.modelsLoading }) {
                            View {
                                attr {
                                    padding(10f)
                                }
                                Text {
                                    attr {
                                        text("正在获取模型...")
                                        fontSize(13f)
                                        color(StockColors.TEXT_SUB)
                                    }
                                }
                            }
                        }
                        velse {
                            vif({ ctx.models.isEmpty() }) {
                                View {
                                    attr {
                                        padding(10f)
                                    }
                                    vif({ ctx.modelsError.isNotEmpty() }) {
                                        Text {
                                            attr {
                                                text(ctx.modelsError)
                                                fontSize(13f)
                                                color(Color(0xFFD4380D))
                                            }
                                        }
                                    }
                                    velse {
                                        Text {
                                            attr {
                                                text("未获取到模型，请检查 URL / Key 是否正确")
                                                fontSize(13f)
                                                color(StockColors.TEXT_SUB)
                                            }
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
                                            color(StockColors.TEXT_MAIN)
                                        }
                                    }
                                    event {
                                        click {
                                            ctx.model = m
                                            ctx.modelRef.view?.setText(m)
                                        }
                                    }
                                }
                                View {
                                    attr {
                                        height(1f)
                                        backgroundColor(Color(0xFFEBEBEB))
                                    }
                                }
                            }
                        }
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
            }  // 闭合外层 View 容器（包住 Scroller 的 padding 容器）

            // ---------- 删除预设确认弹窗 ----------
            vif({ ctx.pendingDelete != null }) {
                Modal {
                    View {
                        attr {
                            flex(1f)
                            allCenter()
                            backgroundColor(Color(0x66000000))
                        }
                        event {
                            click { ctx.pendingDelete = null }
                        }
                        View {
                            attr {
                                width(ctx.pagerData.pageViewWidth - 60f)
                                borderRadius(12f)
                                backgroundColor(Color.WHITE)
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
                                    color(StockColors.TEXT_MAIN)
                                    marginBottom(10f)
                                }
                            }
                            Text {
                                attr {
                                    text("确定删除预设「${ctx.pendingDelete?.name}」吗？")
                                    fontSize(14f)
                                    color(StockColors.TEXT_SUB)
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
                                        backgroundColor(Color(0xFFF0F0F0))
                                        marginRight(12f)
                                    }
                                    Text {
                                        attr {
                                            text("取消")
                                            fontSize(14f)
                                            color(StockColors.TEXT_SUB)
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
                                        backgroundColor(StockColors.UP)
                                    }
                                    Text {
                                        attr {
                                            text("删除")
                                            fontSize(14f)
                                            color(Color.WHITE)
                                            fontWeightSemiBold()
                                        }
                                    }
                                    event {
                                        click {
                                            ctx.pendingDelete?.let { preset ->
                                                AiAnalysisService.removePreset(ctx.sp, preset)
                                                // 若删除的是当前来源/编辑中的预设，清除关联状态
                                                if (ctx.editingPresetName == preset.name) {
                                                    ctx.editingPresetName = ""
                                                    ctx.presetName = ""
                                                    ctx.nameRef.view?.setText("")
                                                }
                                                if (ctx.activePresetName == preset.name) {
                                                    ctx.activePresetName = ""
                                                }
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
        scheduleFetchModels()
    }

    /** 一键切换预设（立即生效并保存） */
    private fun applyPreset(preset: AiPreset) {
        editingPresetName = ""
        activePresetName = preset.name
        presetName = preset.name
        baseUrl = preset.baseUrl
        apiKey = preset.apiKey
        model = preset.model
        nameRef.view?.setText(preset.name)
        urlRef.view?.setText(preset.baseUrl)
        keyRef.view?.setText(preset.apiKey)
        modelRef.view?.setText(preset.model)
        AiAnalysisService.saveConfig(sp, AiConfig(preset.baseUrl, preset.apiKey, preset.model))
        event.onSaved?.invoke()
        scheduleFetchModels()
    }

    /** 进入编辑模式：将预设值回填到表单 */
    private fun startEdit(preset: AiPreset) {
        editingPresetName = preset.name
        activePresetName = preset.name
        presetName = preset.name
        baseUrl = preset.baseUrl
        apiKey = preset.apiKey
        model = preset.model
        nameRef.view?.setText(preset.name)
        urlRef.view?.setText(preset.baseUrl)
        keyRef.view?.setText(preset.apiKey)
        modelRef.view?.setText(preset.model)
        scheduleFetchModels()
    }

    /** 防抖调度自动获取模型：URL/Key 停止输入 500ms 后触发 */
    private fun scheduleFetchModels() {
        if (fetchTimerRef.isNotEmpty()) {
            clearTimeout(fetchTimerRef)
            fetchTimerRef = ""
        }
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            modelsLoading = false
            models.clear()
            modelsError = ""
            return
        }
        fetchTimerRef = setTimeout(500) {
            fetchTimerRef = ""
            fetchModels()
        }
    }

    /** 请求模型列表：成功后常驻显示在模型名输入框下方，失败显示具体原因 */
    private fun fetchModels() {
        if (baseUrl.isBlank() || apiKey.isBlank()) return
        modelsLoading = true
        modelsError = ""
        models.clear()
        AiAnalysisService.fetchModels(network, baseUrl, apiKey) { list, err ->
            modelsLoading = false
            modelsError = err ?: ""
            models.clear()
            models.addAll(list)
        }
    }

    private fun reloadPresets() {
        presets.clear()
        presets.addAll(AiAnalysisService.loadPresets(sp))
    }

    private fun save() {
        val url = baseUrl.trim()
        val key = apiKey.trim()
        val mdl = model.trim()
        if (url.isBlank() || key.isBlank() || mdl.isBlank()) {
            event.onInvalid?.invoke("请完整填写 Base URL、API Key 与模型名")
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            event.onInvalid?.invoke("Base URL 需以 http:// 或 https:// 开头")
            return
        }
        // 当前表单内容来源的预设名（编辑/切换预设后修改时，优先更新该预设而非新建）
        val sourceName = editingPresetName.ifEmpty { activePresetName }
        // 预设名：优先取输入值；为空时编辑/切换保留原预设名，全新则取模型名
        val targetName = presetName.trim().ifBlank {
            if (sourceName.isNotEmpty()) sourceName else mdl
        }
        // 预设名重复校验（排除当前来源预设自身）
        if (presets.any { it.name == targetName && it.name != sourceName }) {
            event.onInvalid?.invoke("预设名「$targetName」已存在，请更换")
            return
        }
        val config = AiConfig(url, key, mdl)
        if (sourceName.isNotEmpty() && presets.any { it.name == sourceName }) {
            // 编辑 / 切换预设后修改：更新原预设（支持改名），并作为当前生效配置
            val newPreset = AiPreset(targetName, url, key, mdl)
            AiAnalysisService.updatePreset(sp, sourceName, newPreset)
            editingPresetName = ""
            presetName = ""
            nameRef.view?.setText("")
        } else {
            // 全新新增：保存配置 + 创建预设（预设名优先取输入值）
            AiAnalysisService.saveConfig(sp, config)
            AiAnalysisService.upsertPreset(sp, config, presetName)
        }
        activePresetName = targetName
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
            flexDirectionRow()
            alignItemsCenter()
            border(Border(1f, BorderStyle.SOLID, Color(0xFFE4E4E4)))
        }
        // 注意：Input 在 Scroller 内必须显式给 height（参考 Kuikly demo
        // MaxTextLengthDemoPage 中 Scroller 内 Input 的写法）。
        // 只写 flex(1f) 不给 height 时，Android 上 EditText 拿不到正确的可点击区域，
        // 即使能看到占位符也无法点击和输入。
        Input {
            ref { refSetter(it) }
            attr {
                flex(1f)
                height(40f)
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
