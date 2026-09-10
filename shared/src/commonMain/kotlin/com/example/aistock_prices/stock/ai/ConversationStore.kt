package com.example.aistock_prices.stock.ai

import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.random.Random

/**
 * AI 问答会话存储：SharedPreferences 持久化，跨启动保留。
 * 会话为全局共享（首页内联 Tab 与 ai_chat 独立页看到同一组会话）。
 */
object ConversationStore {

    private const val KEY = "ai_conversations"

    fun load(sp: SharedPreferencesModule): List<Conversation> {
        val raw = sp.getItem(KEY)
        if (raw.isBlank()) return emptyList()
        return try {
            val obj = JSONObject(raw)
            val arr = obj.optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id") ?: return@mapNotNull null
                if (id.isBlank()) return@mapNotNull null
                val msgArr = o.optJSONArray("messages") ?: JSONArray()
                val messages = (0 until msgArr.length()).mapNotNull { j ->
                    val m = msgArr.optJSONObject(j) ?: return@mapNotNull null
                    ChatMessage(
                        role = m.optString("role") ?: "",
                        content = m.optString("content") ?: "",
                        ts = m.optLong("ts") ?: 0L,
                        error = m.optBoolean("error") ?: false
                    )
                }
                Conversation(
                    id = id,
                    title = o.optString("title") ?: "",
                    stockCode = o.optString("stockCode")?.takeIf { it.isNotBlank() },
                    stockName = o.optString("stockName")?.takeIf { it.isNotBlank() },
                    messages = messages,
                    createdAt = o.optLong("createdAt") ?: 0L
                )
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun save(sp: SharedPreferencesModule, list: List<Conversation>) {
        val arr = JSONArray()
        list.forEach { c ->
            val msgArr = JSONArray()
            c.messages.forEach { m ->
                msgArr.put(
                    JSONObject().apply {
                        put("role", m.role)
                        put("content", m.content)
                        put("ts", m.ts)
                        put("error", m.error)
                    }
                )
            }
            arr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("title", c.title)
                    put("stockCode", c.stockCode ?: "")
                    put("stockName", c.stockName ?: "")
                    put("messages", msgArr)
                    put("createdAt", c.createdAt)
                }
            )
        }
        sp.setItem(KEY, JSONObject().apply { put("list", arr) }.toString())
    }

    fun newId(): String = "${DateTime.currentTimestamp()}_${Random.nextInt(100000)}"

    /** 新建通用会话（无关联股票） */
    fun newConversation(sp: SharedPreferencesModule): Conversation {
        val conv = Conversation(
            id = newId(),
            title = "新对话",
            createdAt = DateTime.currentTimestamp()
        )
        val list = load(sp).toMutableList()
        list.add(0, conv)
        save(sp, list)
        return conv
    }

    /** 查找（或创建）关联指定股票的会话；存在则复用 */
    fun findOrCreateForStock(sp: SharedPreferencesModule, code: String, name: String): Conversation {
        val list = load(sp).toMutableList()
        val existing = list.firstOrNull { it.stockCode == code }
        if (existing != null) return existing
        val conv = Conversation(
            id = newId(),
            title = name,
            stockCode = code,
            stockName = name,
            createdAt = DateTime.currentTimestamp()
        )
        list.add(0, conv)
        save(sp, list)
        return conv
    }

    fun delete(sp: SharedPreferencesModule, id: String) {
        save(sp, load(sp).filterNot { it.id == id })
    }

    /** 更新会话（追加消息/改名等），按 id 替换 */
    fun update(sp: SharedPreferencesModule, conv: Conversation) {
        val list = load(sp).toMutableList()
        val idx = list.indexOfFirst { it.id == conv.id }
        if (idx >= 0) list[idx] = conv else list.add(0, conv)
        save(sp, list)
    }
}
