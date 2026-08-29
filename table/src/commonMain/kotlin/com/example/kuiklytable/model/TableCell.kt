package com.example.kuiklytable.model

/**
 * 单元格数据模型
 *
 * @param value 单元格的原始值（文本内容）
 * @param displayValue 显示文本（为空时使用 value）
 * @param alignment 单元格内文本对齐方式（覆盖列默认对齐）
 * @param textColor 单元格文字颜色（覆盖列/主题默认颜色）
 * @param backgroundColor 单元格背景色（覆盖行/主题默认背景色）
 * @param fontSize 单元格字体大小（覆盖列默认字体大小）
 * @param editable 是否可编辑（null 时遵循列/全局配置）
 * @param colspan 跨列数（默认 1）
 * @param rowspan 跨行数（默认 1）
 * @param extra 扩展数据（自定义渲染时可携带任意上下文）
 */
data class TableCell(
    val value: String,
    val displayValue: String = "",
    val alignment: CellAlignment? = null,
    val textColor: Long? = null,
    val backgroundColor: Long? = null,
    val fontSize: Float? = null,
    val editable: Boolean? = null,
    val colspan: Int = 1,
    val rowspan: Int = 1,
    val extra: Map<String, String> = emptyMap()
) {
    val text: String get() = displayValue.ifEmpty { value }
}

/**
 * 单元格对齐方式
 */
enum class CellAlignment {
    LEFT,
    CENTER,
    RIGHT
}
