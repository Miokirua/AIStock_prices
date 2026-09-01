package com.example.aistock_prices.stock.ai

/**
 * AI 问答消息（多轮对话）。
 *
 * @param role "user" / "assistant"
 * @param content 原始文本；assistant 消息为 Markdown，可内嵌 ```stock 代码块标记行情卡片
 * @param error 是否为错误占位消息（请求失败）
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val ts: Long = 0L,
    val error: Boolean = false
)

/**
 * AI 问答会话（可关联某只股票）。
 *
 * @param stockCode 关联股票代码；null = 通用会话（手动新建）
 */
data class Conversation(
    val id: String,
    val title: String,
    val stockCode: String? = null,
    val stockName: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = 0L
) {
    /** 会话展示名：股票会话用股票名，通用会话用标题 */
    val displayName: String get() = stockName ?: title.ifBlank { "新对话" }
}

/** AI 消息渲染分段：markdown 文本 / 股票卡片标记 */
data class ChatSegment(
    val type: String,   // "markdown" / "stock"
    val text: String,
    val code: String = "",
    val name: String = ""
)

/**
 * 解析 AI 回复中的 ```stock 代码块（股票卡片标记），
 * 按出现顺序拆分为 markdown 与 stock 片段。
 */
fun parseChatSegments(content: String): List<ChatSegment> {
    if (!content.contains("```stock")) return listOf(ChatSegment("markdown", content))
    val pattern = Regex("```stock([\\s\\S]*?)```")
    val result = mutableListOf<ChatSegment>()
    var lastEnd = 0
    pattern.findAll(content).forEach { m ->
        if (m.range.first > lastEnd) {
            result.add(ChatSegment("markdown", content.substring(lastEnd, m.range.first)))
        }
        val body = m.groupValues[1].trim()
        val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val parts = firstLine.trim().split(Regex("\\s+"))
        val code = parts.firstOrNull()?.trim() ?: ""
        val name = parts.drop(1).joinToString(" ")
        result.add(ChatSegment("stock", body, code, name))
        lastEnd = m.range.last + 1
    }
    if (lastEnd < content.length) {
        result.add(ChatSegment("markdown", content.substring(lastEnd)))
    }
    return result.filter { it.type != "markdown" || it.text.isNotBlank() }
}
