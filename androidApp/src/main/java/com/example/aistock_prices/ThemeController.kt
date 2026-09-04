package com.example.aistock_prices

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/**
 * 应用主题模式控制器（宿主侧单例）：
 * - 模式（跟随系统/深色/浅色）持久化于应用 Preference，并注入每个页面 pageData（isNightMode/themeMode）
 * - 切换模式通过 [AppCompatDelegate.setDefaultNightMode] 驱动所有已打开页面自动 recreate 重建，
 *   页面重建时重新读取注入参数 => 新主题即刻生效（无需逐页手动刷新）
 * - 跟随系统模式下系统深色切换由 AppCompat 接管（FOLLOW_SYSTEM 会自动 recreate 受影响页面）
 */
object ThemeController {

    const val MODE_AUTO = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    private const val PREFS = "kuikly_theme"
    private const val KEY_MODE = "mode"

    @Volatile
    var mode: Int = MODE_AUTO
        private set

    /** 冷启动初始化：读取持久化模式并应用（使新页面/首屏拿到正确主题） */
    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = sp.getInt(KEY_MODE, MODE_AUTO)
        AppCompatDelegate.setDefaultNightMode(toAppCompat(mode))
    }

    /** 当前实际是否夜间（DARK=是 / LIGHT=否 / AUTO=跟随系统 uiMode） */
    fun currentNight(): Boolean = when (mode) {
        MODE_DARK -> true
        MODE_LIGHT -> false
        else -> systemNight()
    }

    /** 供共享层（HRBridgeModule setThemeMode）调用：更新模式并触发全页面重建 */
    fun setMode(mode: Int) {
        if (mode != MODE_AUTO && mode != MODE_LIGHT && mode != MODE_DARK) return
        this.mode = mode
        KRApplication.application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MODE, mode).apply()
        AppCompatDelegate.setDefaultNightMode(toAppCompat(mode))
    }

    private fun systemNight(): Boolean {
        val app = KRApplication.application
        val uiMode = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun toAppCompat(mode: Int): Int = when (mode) {
        MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}
