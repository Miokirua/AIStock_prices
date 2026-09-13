package com.example.aistock_prices.stock.data

import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 本地数据备份 / 恢复。
 *
 * 设计要点：**直接快照 SharedPreferences 的原始值**，而不是把各模型再序列化一遍。
 * - 好处一：无损。以后某个模型新增字段，备份/恢复不用跟着改（同一 App 版本内闭环）。
 * - 好处二：省事。Kuikly 的 SP 只有 getItem/setItem，没有 key 遍历能力，
 *   直接存「键 → 原始值」的映射，恢复时逐个 setItem 回去即可。
 *
 * 覆盖范围：自选列表（含置顶/分组归属）、分组顺序、关键位标记、AI 会话。
 * **不含** LLM 的 Base URL / API Key（属敏感凭据，不进剪贴板），也不含行情缓存。
 *
 * 导出走 [BridgeModule.copyToPasteboard]，导入靠页面里的多行输入框粘贴 —— 全程 commonMain，
 * 不需要三端宿主各写一套读写剪贴板的实现。
 */
object BackupStore {

    private const val FORMAT = "aistock-backup"

    /** 备份格式版本；导入时高于当前版本则拒绝（避免新版写出的内容被旧版误读） */
    private const val VERSION = 1

    // 与各 Store 内部使用的 SP 键保持一致（同上：直接操作原始值，不走模型序列化）
    private const val KEY_WATCHLIST = "watchlist_stocks"
    private const val KEY_GROUPS = "watchlist_groups"
    private const val KEY_CONVERSATIONS = "ai_conversations"
    private const val KEY_LEVELS_PREFIX = "key_levels_"

    /** 备份覆盖面，用于导出后的提示与导入前的确认文案 */
    data class Summary(
        val stocks: Int,
        val groups: Int,
        val levelCodes: Int,
        val levels: Int,
        val conversations: Int
    ) {
        val text: String
            get() = "自选 $stocks 只 · 分组 $groups 个 · 关键位 $levels 条（$levelCodes 只）· 会话 $conversations 个"
    }

    sealed class ImportResult {
        /** 导入成功（[summary] 为导入后的实际内容量） */
        data class Ok(val summary: Summary) : ImportResult()

        /** 不是本 App 的备份内容、版本不支持或 JSON 解析失败 */
        object BadFormat : ImportResult()

        /** 格式正确但里面没有任何数据 */
        object Empty : ImportResult()
    }

    /** 当前本机数据的覆盖面（导出前展示，让用户知道会带走什么） */
    fun summarize(sp: SharedPreferencesModule, watchlistCodes: List<String>): Summary {
        val levelCodes = LevelStore.codesWithLevels(sp, watchlistCodes)
        return Summary(
            stocks = countList(sp.getItem(KEY_WATCHLIST)),
            groups = countList(sp.getItem(KEY_GROUPS)),
            levelCodes = levelCodes.size,
            levels = levelCodes.sumOf { countList(sp.getItem(KEY_LEVELS_PREFIX + it)) },
            conversations = countList(sp.getItem(KEY_CONVERSATIONS))
        )
    }

    /**
     * 生成备份文本（JSON 字符串）。
     * [watchlistCodes] 用于兜底枚举关键位所属的股票（见 [LevelStore.codesWithLevels]）。
     */
    fun exportText(sp: SharedPreferencesModule, watchlistCodes: List<String>): String {
        val levels = JSONObject()
        LevelStore.codesWithLevels(sp, watchlistCodes).forEach { code ->
            val raw = sp.getItem(KEY_LEVELS_PREFIX + code)
            if (raw.isNotBlank()) levels.put(code, raw)
        }
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", DateTime.currentTimestamp())
            put("watchlist", sp.getItem(KEY_WATCHLIST))
            put("groups", sp.getItem(KEY_GROUPS))
            put("conversations", sp.getItem(KEY_CONVERSATIONS))
            put("levels", levels)
        }.toString()
    }

    /**
     * 从备份文本恢复。**覆盖式**：关键位会先整体清空再写入，不做合并，
     * 这样「恢复」的语义才是确定的（恢复到备份时的状态，而不是两者求并集）。
     */
    fun importText(
        sp: SharedPreferencesModule,
        text: String,
        watchlistCodes: List<String>
    ): ImportResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ImportResult.Empty

        val obj = try {
            JSONObject(trimmed)
        } catch (e: Throwable) {
            return ImportResult.BadFormat
        }
        if (obj.optString("format") != FORMAT) return ImportResult.BadFormat
        if (obj.optInt("version") > VERSION) return ImportResult.BadFormat

        val watchlist = obj.optString("watchlist")
        val groups = obj.optString("groups")
        val conversations = obj.optString("conversations")
        val levels = obj.optJSONObject("levels")

        if (watchlist.isBlank() && groups.isBlank() && conversations.isBlank() && levels == null) {
            return ImportResult.Empty
        }

        // 关键位：先整体清空（含兜底 code），再写入备份里的内容
        LevelStore.clearAll(sp, watchlistCodes)
        val importedLevelCodes = mutableListOf<String>()
        if (levels != null) {
            val keys = levels.keySet()
            keys.forEach { code ->
                val raw = levels.optString(code)
                if (code.isNotBlank() && raw.isNotBlank()) {
                    sp.setItem(KEY_LEVELS_PREFIX + code, raw)
                    importedLevelCodes.add(code)
                }
            }
        }
        LevelStore.rebuildIndex(sp, importedLevelCodes)

        // 其余三项：空值不写回，避免把「本就没有」误写成「已清空」
        // （尤其自选列表，Watchlist 在 raw 为空时会回退到内置默认 10 只）
        if (watchlist.isNotBlank()) sp.setItem(KEY_WATCHLIST, watchlist)
        if (groups.isNotBlank()) sp.setItem(KEY_GROUPS, groups)
        if (conversations.isNotBlank()) sp.setItem(KEY_CONVERSATIONS, conversations)

        return ImportResult.Ok(summarize(sp, Watchlist.stocks(sp).map { it.code }))
    }

    /** 数一数 `{"list":[...]}` 里的条目数，用于展示覆盖面 */
    private fun countList(raw: String): Int {
        if (raw.isBlank()) return 0
        return try {
            JSONObject(raw).optJSONArray("list")?.length() ?: 0
        } catch (e: Throwable) {
            0
        }
    }
}
