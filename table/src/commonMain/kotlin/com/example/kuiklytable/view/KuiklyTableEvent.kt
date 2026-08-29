package com.example.kuiklytable.view

import com.tencent.kuikly.core.base.ComposeEvent
import com.example.kuiklytable.model.TableCell
import com.example.kuiklytable.model.TableColumn
import com.example.kuiklytable.model.TableRow

/**
 * KuiklyTable 事件类
 * 包含所有回调函数
 */
class KuiklyTableEvent : ComposeEvent() {
    /** 单元格点击 */
    var onCellClick: ((row: TableRow, column: TableColumn, cell: TableCell) -> Unit)? = null

    /** 单元格长按 */
    var onCellLongPress: ((row: TableRow, column: TableColumn, cell: TableCell) -> Unit)? = null

    /** 单元格编辑完成 */
    var onCellEdit: ((rowIndex: Int, columnKey: String, newValue: String) -> Unit)? = null

    /** 表头点击 */
    var onHeaderClick: ((column: TableColumn) -> Unit)? = null

    /** 行点击 */
    var onRowClick: ((row: TableRow, rowIndex: Int) -> Unit)? = null

    /** 滚动 */
    var onScroll: ((offsetX: Float, offsetY: Float) -> Unit)? = null

    // DSL 方法
    fun cellClick(handler: (row: TableRow, column: TableColumn, cell: TableCell) -> Unit) {
        onCellClick = handler
    }

    fun cellLongPress(handler: (row: TableRow, column: TableColumn, cell: TableCell) -> Unit) {
        onCellLongPress = handler
    }

    fun cellEdit(handler: (rowIndex: Int, columnKey: String, newValue: String) -> Unit) {
        onCellEdit = handler
    }

    fun headerClick(handler: (column: TableColumn) -> Unit) {
        onHeaderClick = handler
    }

    fun rowClick(handler: (row: TableRow, rowIndex: Int) -> Unit) {
        onRowClick = handler
    }

    fun scroll(handler: (offsetX: Float, offsetY: Float) -> Unit) {
        onScroll = handler
    }
}
