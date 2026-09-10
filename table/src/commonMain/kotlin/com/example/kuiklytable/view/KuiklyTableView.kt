package com.example.kuiklytable.view

import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.layout.FlexDirection
import com.tencent.kuikly.core.layout.FlexJustifyContent
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.views.*
import com.example.kuiklytable.model.*
import com.example.kuiklytable.dsl.KuiklyTableConfig

class KuiklyTableView(
    private val tableColumns: List<TableColumn>,
    private val tableRows: List<TableRow>,
    private val tableConfig: KuiklyTableConfig
) : ComposeView<KuiklyTableAttr, KuiklyTableEvent>() {

    override fun createAttr(): KuiklyTableAttr = KuiklyTableAttr()
    override fun createEvent(): KuiklyTableEvent = KuiklyTableEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flex(1f)
                    flexDirection(FlexDirection.COLUMN)
                    backgroundColor(Color(ctx.tableConfig.backgroundColor))
                }

                // 表头 - 直接遍历，不用 if
                View {
                    attr {
                        flexDirectionRow()
                        backgroundColor(Color(ctx.tableConfig.headerBackgroundColor))
                        height(ctx.tableConfig.headerHeight)
                    }
                    ctx.tableColumns.forEach { col ->
                        View {
                            attr {
                                if (col.width != null) width(col.width!!) else flex(1f)
                                minWidth(col.minWidth)
                                flexDirectionRow()
                                alignItems(FlexAlign.CENTER)
                                justifyContent(
                                    when (col.headerAlignment) {
                                        CellAlignment.LEFT -> FlexJustifyContent.FLEX_START
                                        CellAlignment.CENTER -> FlexJustifyContent.CENTER
                                        CellAlignment.RIGHT -> FlexJustifyContent.FLEX_END
                                    }
                                )
                                paddingLeft(ctx.tableConfig.cellPaddingH)
                                paddingRight(ctx.tableConfig.cellPaddingH)
                                if (ctx.tableConfig.showColumnBorder) {
                                    borderRight(Border(ctx.tableConfig.borderLineWidth, BorderStyle.SOLID, Color(ctx.tableConfig.borderColor)))
                                }
                            }
                            Text {
                                attr {
                                    text(col.title)
                                    fontSize(ctx.tableConfig.headerFontSize)
                                    color(Color(ctx.tableConfig.headerTextColor))
                                    if (ctx.tableConfig.headerFontBold) fontWeightBold()
                                }
                            }
                        }
                    }
                }

                // 数据行 - 直接遍历
                ctx.tableRows.forEachIndexed { idx, row ->
                    val bg = row.backgroundColor ?: when {
                        // 关闭斑马纹时用表格底色，避免夜间模式回退到浅色默认值
                        !ctx.tableConfig.showZebraStripe -> ctx.tableConfig.backgroundColor
                        idx % 2 != 0 -> ctx.tableConfig.oddRowBackgroundColor
                        else -> ctx.tableConfig.evenRowBackgroundColor
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            backgroundColor(Color(bg))
                            height(ctx.tableConfig.rowHeight)
                            if (ctx.tableConfig.showRowBorder) {
                                borderBottom(Border(ctx.tableConfig.borderLineWidth, BorderStyle.SOLID, Color(ctx.tableConfig.borderColor)))
                            }
                        }
                        ctx.tableColumns.forEach { col ->
                            val cell = row.cell(col.key) ?: TableCell("")
                            View {
                                attr {
                                    if (col.width != null) width(col.width!!) else flex(1f)
                                    minWidth(col.minWidth)
                                    flexDirectionRow()
                                    alignItems(FlexAlign.CENTER)
                                    justifyContent(
                                        when (cell.alignment ?: col.alignment) {
                                            CellAlignment.LEFT -> FlexJustifyContent.FLEX_START
                                            CellAlignment.CENTER -> FlexJustifyContent.CENTER
                                            CellAlignment.RIGHT -> FlexJustifyContent.FLEX_END
                                        }
                                    )
                                    paddingLeft(ctx.tableConfig.cellPaddingH)
                                    paddingRight(ctx.tableConfig.cellPaddingH)
                                    if (cell.backgroundColor != null) {
                                        backgroundColor(Color(cell.backgroundColor!!))
                                    }
                                    if (ctx.tableConfig.showColumnBorder) {
                                        borderRight(Border(ctx.tableConfig.borderLineWidth, BorderStyle.SOLID, Color(ctx.tableConfig.borderColor)))
                                    }
                                }
                                Text {
                                    attr {
                                        text(cell.text)
                                        fontSize(cell.fontSize ?: ctx.tableConfig.cellFontSize)
                                        color(Color(cell.textColor ?: ctx.tableConfig.cellTextColor))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
