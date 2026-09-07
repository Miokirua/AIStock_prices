package com.example.kuiklytable.dsl

import com.tencent.kuikly.core.base.ViewContainer
import com.example.kuiklytable.model.*
import com.example.kuiklytable.view.KuiklyTableView

class KuiklyTableConfig {
    var headerBackgroundColor = 0xFFF5F7FA
    var headerTextColor = 0xFF333333
    var headerFontSize = 14f
    var headerFontBold = true
    var cellTextColor = 0xFF333333
    var cellFontSize = 13f
    var borderColor = 0xFFE0E0E0
    var borderLineWidth = 0.5f
    var evenRowBackgroundColor = 0xFFFFFFFF
    var oddRowBackgroundColor = 0xFFFAFAFA
    var cellPaddingH = 12f
    var cellPaddingV = 10f
    var headerHeight = 44f
    var rowHeight = 40f
    var showHeader = true
    var showRowBorder = true
    var showColumnBorder = true
    var showZebraStripe = true
    var showOuterBorder = true
    var stickyHeader = true
    var editable = true
}

fun ViewContainer<*, *>.KuiklyTable(
    tableData: TableData,
    block: KuiklyTableConfig.() -> Unit = {}
) {
    val config = KuiklyTableConfig().apply(block)
    // 数据通过构造函数传入，body() 调用时已可用
    addChild(KuiklyTableView(tableData.visibleColumns, tableData.rows, config)) {}
}

// ==================== 数据构建 DSL ====================

fun tableData(block: TableDataBuilder.() -> Unit): TableData {
    return TableDataBuilder().apply(block).build()
}

class TableDataBuilder {
    private val columns = mutableListOf<TableColumn>()
    private val rows = mutableListOf<TableRow>()

    fun column(
        key: String,
        title: String,
        width: Float? = null,
        minWidth: Float = 60f,
        alignment: CellAlignment = CellAlignment.LEFT,
        headerAlignment: CellAlignment = CellAlignment.CENTER
    ) {
        columns.add(TableColumn(key = key, title = title, width = width, minWidth = minWidth, alignment = alignment, headerAlignment = headerAlignment))
    }

    fun row(block: RowBuilder.() -> Unit) {
        rows.add(RowBuilder().apply(block).build())
    }

    fun build(): TableData = TableData(columns, rows)
}

class RowBuilder {
    private val cells = mutableMapOf<String, TableCell>()
    private var rowBackgroundColor: Long? = null

    fun cell(key: String, value: String) {
        cells[key] = TableCell(value)
    }

    /** 设置整行背景色（覆盖斑马纹/主题默认色），用于行级高亮 */
    fun backgroundColor(color: Long) {
        rowBackgroundColor = color
    }

    fun build(): TableRow = TableRow(cells, backgroundColor = rowBackgroundColor)
}
