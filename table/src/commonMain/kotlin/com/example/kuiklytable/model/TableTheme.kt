package com.example.kuiklytable.model

/**
 * 表格主题模式
 */
enum class TableThemeMode {
    LIGHT,
    DARK
}

/**
 * 表格主题颜色配置集合
 */
data class TableThemeColors(
    // ---- 整体 ----
    val backgroundColor: Long = 0xFFFFFFFF,
    val borderColor: Long = 0xFFE0E0E0,
    val borderLineWidth: Float = 0.5f,

    // ---- 表头 ----
    val headerBackgroundColor: Long = 0xFFF5F7FA,
    val headerTextColor: Long = 0xFF333333,
    val headerFontSize: Float = 14f,
    val headerFontBold: Boolean = true,

    // ---- 数据行 ----
    val cellTextColor: Long = 0xFF333333,
    val cellFontSize: Float = 13f,
    val evenRowBackgroundColor: Long = 0xFFFFFFFF,
    val oddRowBackgroundColor: Long = 0xFFFAFAFA,

    // ---- 高亮/选中 ----
    val selectedRowBackgroundColor: Long = 0xFFE8F0FE,
    val hoverRowBackgroundColor: Long = 0xFFF5F5F5,

    // ---- 编辑态 ----
    val editingBorderColor: Long = 0xFF4F8FFF,

    // ---- 空状态 ----
    val emptyTextColor: Long = 0xFF999999,
    val emptyTextSize: Float = 14f
)

/**
 * 预定义浅色主题
 */
val LightTableTheme = TableThemeColors()

/**
 * 预定义深色主题
 */
val DarkTableTheme = TableThemeColors(
    backgroundColor = 0xFF1A1A2E,
    borderColor = 0xFF3D3D5C,
    headerBackgroundColor = 0xFF2D2D44,
    headerTextColor = 0xFFE0E0E0,
    cellTextColor = 0xFFE0E0E0,
    evenRowBackgroundColor = 0xFF1A1A2E,
    oddRowBackgroundColor = 0xFF222240,
    selectedRowBackgroundColor = 0xFF1A3A5C,
    hoverRowBackgroundColor = 0xFF2D2D44,
    editingBorderColor = 0xFF6C9FFF,
    emptyTextColor = 0xFF808080
)

fun resolveTableThemeColors(mode: TableThemeMode): TableThemeColors {
    return when (mode) {
        TableThemeMode.LIGHT -> LightTableTheme
        TableThemeMode.DARK -> DarkTableTheme
    }
}
