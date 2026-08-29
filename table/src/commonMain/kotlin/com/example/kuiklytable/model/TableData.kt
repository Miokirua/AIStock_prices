package com.example.kuiklytable.model

/**
 * 表格数据容器
 *
 * 将列定义和行数据组织在一起，方便传递给 KuiklyTable 组件。
 *
 * @param columns 列定义列表
 * @param rows 数据行列表（不包含表头行）
 * @param headerRows 表头行列表（支持多级表头）
 */
data class TableData(
    val columns: List<TableColumn>,
    val rows: List<TableRow>,
    val headerRows: List<TableRow> = emptyList()
) {
    /**
     * 获取可见列
     */
    val visibleColumns: List<TableColumn>
        get() = columns.filter { it.visible }

    /**
     * 获取冻结左侧列
     */
    val leftFixedColumns: List<TableColumn>
        get() = visibleColumns.filter { it.fixed == FixedPosition.LEFT }

    /**
     * 获取冻结右侧列
     */
    val rightFixedColumns: List<TableColumn>
        get() = visibleColumns.filter { it.fixed == FixedPosition.RIGHT }

    /**
     * 获取可滚动列（非冻结）
     */
    val scrollableColumns: List<TableColumn>
        get() = visibleColumns.filter { it.fixed == FixedPosition.NONE }

    /**
     * 添加多行数据
     */
    fun withRows(newRows: List<TableRow>): TableData = copy(rows = rows + newRows)

    /**
     * 添加单行数据（Map 形式，key 对应列 key）
     */
    fun withRow(cells: Map<String, String>): TableData {
        val tableCells = cells.mapValues { (_, value) -> TableCell(value) }
        return copy(rows = rows + TableRow(tableCells))
    }

    /**
     * 批量添加行数据（List<Map> 形式）
     */
    fun withMapRows(data: List<Map<String, String>>): TableData {
        val newRows = data.map { cells ->
            TableRow(cells.mapValues { (_, value) -> TableCell(value) })
        }
        return copy(rows = rows + newRows)
    }

    /**
     * 更新指定单元格的值
     */
    fun withCellUpdated(rowIndex: Int, columnKey: String, newValue: String): TableData {
        if (rowIndex < 0 || rowIndex >= rows.size) return this
        val oldRow = rows[rowIndex]
        val newCells = oldRow.cells.toMutableMap()
        newCells[columnKey] = TableCell(newValue)
        val newRows = rows.toMutableList()
        newRows[rowIndex] = oldRow.copy(cells = newCells)
        return copy(rows = newRows)
    }
}
