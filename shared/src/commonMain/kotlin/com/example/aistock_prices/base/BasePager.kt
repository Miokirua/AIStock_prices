package com.example.aistock_prices.base

import com.example.aistock_prices.stock.ui.ThemeMode
import com.example.aistock_prices.stock.ui.ThemePalette
import com.example.aistock_prices.stock.ui.ThemePalettes
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.*

internal abstract class BasePager : Pager() {
    private var nightModel: Boolean? by observable(null)

    override fun createExternalModules(): Map<String, Module>? {
        val externalModules = hashMapOf<String, Module>()
        externalModules[BridgeModule.MODULE_NAME] = BridgeModule()
        return externalModules
    }

    override fun created() {
        super.created()
        isNightMode()
    }

    override fun themeDidChanged(data: JSONObject) {
        super.themeDidChanged(data)
        nightModel = data.optBoolean(IS_NIGHT_MODE_KEY)
    }

    // 是否为夜间模式
    override fun isNightMode(): Boolean {
        if (nightModel == null) {
            nightModel = pageData.params.optBoolean(IS_NIGHT_MODE_KEY)
        }
        return nightModel!!
    }

    /**
     * 当前主题色板（夜间按宿主注入的 isNightMode 取暗色）。
     * 在 body() 及各 ViewBuilder 构建期调用即取到当前主题对应配色；
     * 主题切换由宿主 recreate 页面触发 body 重建，无需响应式追踪。
     */
    val pal: ThemePalette
        get() = ThemePalettes.of(isNightMode())

    /** 当前主题模式（跟随系统/深色/浅色，宿主注入 pageData 参数），用于菜单状态展示 */
    val themeMode: Int
        get() = pageData.params.optInt(IS_THEME_MODE_KEY, ThemeMode.AUTO)

    // 不开启调试UI模式
    override fun debugUIInspector(): Boolean {
        return false
    }

    companion object {
        const val IS_NIGHT_MODE_KEY = "isNightMode"
        const val IS_THEME_MODE_KEY = "themeMode"
    }

}