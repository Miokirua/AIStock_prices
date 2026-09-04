package com.example.aistock_prices.stock.ui

import com.tencent.kuikly.core.base.Color
import com.tencent.kuiklybase.config.MarkdownColors
import com.tencent.kuiklybase.config.MarkdownConfig

/**
 * 页面主题色板：亮 / 暗两套语义 token。
 *
 * 设计约定（配合「夜间模式」）：
 * - 页面/组件在 body 构建期通过 [ThemePalettes.of] 取当前主题色板（是否夜间由宿主注入的
 *   isNightMode 决定，见 BasePager.isNightMode()），所有颜色引用一律走 pal 语义字段，
 *   不要直接写 Color(0x...) 字面量或 Color.WHITE（否则暗色下无法跟随主题）。
 * - 主题切换采用宿主 DayNight recreate 重建页面，body 会重新构建，因此构建期取值即最新主题。
 *
 * A 股配色约定：涨红跌绿（up/down），深浅主题下均保持辨识度（暗色下红绿提亮）。
 */
class ThemePalette(
    /** 涨 - 红（A股） */
    val up: Color,
    /** 跌 - 绿（A股） */
    val down: Color,
    /** 平 - 灰 */
    val flat: Color,
    /** 主题强调色（按钮/链接/选中态） */
    val accent: Color,
    /** 强调色上的文字（按钮白字等） */
    val onAccent: Color,
    /** 页面背景 */
    val bgPage: Color,
    /** 卡片 / 顶栏 / 气泡 / 弹窗等白底 */
    val card: Color,
    /** 输入框 / 浅灰小容器底 */
    val chipBg: Color,
    /** 次级灰底（取消按钮 / 分割行） */
    val chip2Bg: Color,
    /** 浅蓝选中底（会话选中 / 强调 pill） */
    val accentChipBg: Color,
    /** 主文字 */
    val textMain: Color,
    /** 次要文字 */
    val textSub: Color,
    /** 分隔线 / 细边框 */
    val divider: Color,
    /** 「停止生成」按钮红 */
    val stopRed: Color,
    /** 错误提示文字红 */
    val errRed: Color,
    /** 修改模式提示条：背景 */
    val warnBg: Color,
    /** 修改模式提示条：边框 */
    val warnBorder: Color,
    /** 修改模式提示条：文字 */
    val warnText: Color,
    /** 深色半透明遮罩（抽屉/菜单外遮罩，0x33 级别） */
    val maskDim: Color,
    /** 更深遮罩（Modal 弹窗外，0x66 级别） */
    val maskFull: Color
) {
    /** 按涨跌返回颜色（红涨绿跌） */
    fun ofChange(v: Double): Color = when {
        v > 0.0 -> up
        v < 0.0 -> down
        else -> flat
    }
}

/** 亮 / 暗色板实例与选择入口 */
object ThemePalettes {

    /** 亮色（日间） */
    val light = ThemePalette(
        up = Color(0xFFE0322E),
        down = Color(0xFF0A9C5C),
        flat = Color(0xFF9E9E9E),
        accent = Color(0xFF3370FF),
        onAccent = Color(0xFFFFFFFF),
        bgPage = Color(0xFFF5F6F8),
        card = Color(0xFFFFFFFF),
        chipBg = Color(0xFFF5F6F8),
        chip2Bg = Color(0xFFF0F0F0),
        accentChipBg = Color(0xFFF0F5FF),
        textMain = Color(0xFF1A1A1A),
        textSub = Color(0xFF8A8A8A),
        divider = Color(0xFFE4E4E4),
        stopRed = Color(0xFFE53935),
        errRed = Color(0xFFD4380D),
        warnBg = Color(0xFFFFF7E6),
        warnBorder = Color(0xFFFFE7BA),
        warnText = Color(0xFFAD6800),
        maskDim = Color(0x33000000),
        maskFull = Color(0x66000000)
    )

    /** 暗色（夜间） */
    val dark = ThemePalette(
        up = Color(0xFFF0564D),
        down = Color(0xFF1FBF75),
        flat = Color(0xFF8A93A1),
        accent = Color(0xFF5B8CFF),
        onAccent = Color(0xFFFFFFFF),
        bgPage = Color(0xFF111418),
        card = Color(0xFF1D2129),
        chipBg = Color(0xFF2A3039),
        chip2Bg = Color(0xFF3A4150),
        accentChipBg = Color(0xFF2A3852),
        textMain = Color(0xFFE6E9EF),
        textSub = Color(0xFF98A2B3),
        divider = Color(0xFF363D48),
        stopRed = Color(0xFFF05A55),
        errRed = Color(0xFFFF8A70),
        warnBg = Color(0xFF3A3120),
        warnBorder = Color(0xFF55492C),
        warnText = Color(0xFFE5B35C),
        maskDim = Color(0x59000000),
        maskFull = Color(0x80000000)
    )

    /** 选择当前主题色板 */
    fun of(night: Boolean): ThemePalette = if (night) dark else light
}

/**
 * 主题模式（三态，用户可手动切换；状态由宿主持久化并注入各页面 pageData 参数）。
 * 切换顺序：跟随系统(AUTO) -> 深色(DARK) -> 浅色(LIGHT) -> 跟随系统(AUTO) …
 */
object ThemeMode {
    const val AUTO = 0
    const val LIGHT = 1
    const val DARK = 2

    /** 循环切换下一档 */
    fun next(mode: Int): Int = when (mode) {
        AUTO -> DARK
        DARK -> LIGHT
        else -> AUTO
    }

    /** 模式展示名（菜单右侧小字） */
    fun label(mode: Int): String = when (mode) {
        DARK -> "深色"
        LIGHT -> "浅色"
        else -> "跟随系统"
    }
}

/**
 * AI 回复 Markdown 渲染配置：夜间提供暗色文字/代码块配色。
 * 调用方（AI 问答/结果详情等）渲染 markdown 时按当前主题传入。
 */
fun markdownConfig(night: Boolean): MarkdownConfig {
    if (!night) return MarkdownConfig()
    return MarkdownConfig(
        colors = MarkdownColors(
            text = 0xFFDDE2EAL,
            codeBackground = 0xFF262C36L,
            inlineCodeBackground = 0xFF2E3541L,
            dividerColor = 0xFF363D48L,
            tableBackground = 0xFF1D2129L,
            blockQuoteBar = 0xFF5B8CFFL,
            blockQuoteBackground = 0xFF232936L,
            linkColor = 0xFF7AA5FFL,
            codeText = 0xFFFFC777L
        ),
        codeHighlightDarkTheme = true
    )
}
