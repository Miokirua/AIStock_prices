package com.example.kuiklytable.model

/**
 * 表格行数据模型
 *
 * @param cells 单元格数据 Map，key 对应 TableColumn.key
 * @param height 行高（dp，null 表示自适应）
 * @param backgroundColor 行背景色（覆盖主题默认色）
 * @param extra 扩展数据
 */
data class TableRow(
    val cells: Map<String, TableCell>,
    val height: Float? = null,
    val backgroundColor: Long? = null,
    val extra: Map<String, String> = emptyMap()
) {
    /**
     * 便捷方法：按列 key 获取单元格
     */
    fun cell(columnKey: String): TableCell? = cells[columnKey]
}
