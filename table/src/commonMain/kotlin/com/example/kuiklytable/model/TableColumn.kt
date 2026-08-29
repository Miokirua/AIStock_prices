package com.example.kuiklytable.model

/**
 * 表格列定义
 *
 * @param key 列标识键（对应 TableRow.cells 中的 key）
 * @param title 列标题（表头显示文本）
 * @param width 列宽（dp，null 表示自适应/flex 均分）
 * @param minWidth 最小列宽
 * @param maxWidth 最大列宽
 * @param alignment 该列默认对齐方式
 * @param headerAlignment 表头对该列的对齐方式（覆盖 alignment）
 * @param visible 是否可见
 * @param editable 该列是否可编辑（默认 true）
 * @param fixed 是否冻结（LEFT / RIGHT / NONE）
 */
data class TableColumn(
    val key: String,
    val title: String,
    val width: Float? = null,
    val minWidth: Float = 60f,
    val maxWidth: Float = Float.MAX_VALUE,
    val alignment: CellAlignment = CellAlignment.LEFT,
    val headerAlignment: CellAlignment = CellAlignment.CENTER,
    val visible: Boolean = true,
    val editable: Boolean = true,
    val fixed: FixedPosition = FixedPosition.NONE
)

/**
 * 列冻结位置
 */
enum class FixedPosition {
    NONE,
    LEFT,
    RIGHT
}
