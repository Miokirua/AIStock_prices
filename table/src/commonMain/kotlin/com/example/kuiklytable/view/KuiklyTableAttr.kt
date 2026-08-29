package com.example.kuiklytable.view

import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.reactive.handler.observable
import com.example.kuiklytable.model.TableData

/**
 * KuiklyTable 属性类
 * 所有属性使用 by observable 实现响应式更新
 */
class KuiklyTableAttr : ComposeAttr() {
    // ==================== 数据 ====================
    var tableData: TableData by observable(TableData(emptyList(), emptyList()))

    // ==================== 主题 ====================
    var headerBackgroundColor by observable(0xFFF5F7FA)
    var headerTextColor by observable(0xFF333333)
    var headerFontSize by observable(14f)
    var headerFontBold by observable(true)
    var cellTextColor by observable(0xFF333333)
    var cellFontSize by observable(13f)
    var borderColor by observable(0xFFE0E0E0)
    var borderLineWidth by observable(0.5f)
    var evenRowBackgroundColor by observable(0xFFFFFFFF)
    var oddRowBackgroundColor by observable(0xFFFAFAFA)
    var editingBorderColor by observable(0xFF4F8FFF)
    var cellPaddingH by observable(12f)
    var cellPaddingV by observable(10f)
    var headerHeight by observable(44f)
    var rowHeight by observable(40f)

    // ==================== 功能开关 ====================
    var showHeader by observable(true)
    var showRowBorder by observable(true)
    var showColumnBorder by observable(true)
    var showZebraStripe by observable(true)
    var showOuterBorder by observable(true)
    var stickyHeader by observable(true)
    var editable by observable(true)
}
