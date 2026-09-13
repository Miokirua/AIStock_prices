package com.example.aistock_prices.stock.data

import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.math.abs

/** 关键位类型（支撑 / 压力 / 成本） */
enum class LevelKind(val label: String) {
    SUPPORT("支撑"),
    RESISTANCE("压力"),
    COST("成本");

    companion object {
        fun of(name: String?): LevelKind =
            values().firstOrNull { it.name == name } ?: SUPPORT
    }
}

/** 用户手动标记的某个关键价位 */
data class KeyLevelMark(
    val price: Double,
    val kind: LevelKind = LevelKind.SUPPORT,
    /** 备注（当前版本未开放编辑，留作字段兼容） */
    val note: String = ""
)

/** 添加结果，供调用方给出准确反馈 */
enum class AddLevelResult { ADDED, DUPLICATE, FULL }

/**
 * 关键位备忘：用户看 K 线时手动标记的价位（支撑/压力/成本），
 * 按股票代码分别持久化到 SharedPreferences（key = `key_levels_<code>`）。
 *
 * 与 AI 分析产出的 `keyLevels` 相互独立——展示时由页面合并（用户标记的线更"实"，
 * 属于用户自己下的判断；AI 的属于参考）。这里只管存取，不参与渲染。
 */
object LevelStore {

    private const val KEY_PREFIX = "key_levels_"
    /**
     * 「有标记的股票代码」索引。
     * Kuikly 的 SP 模块只有 getItem/setItem，没有 key 遍历能力，备份导出时必须靠一份
     * 自维护的索引才知道该读哪些 `key_levels_<code>`。见 [codesWithLevels]。
     */
    private const val KEY_INDEX = "key_levels_index"
    /** 单只股票最多保留的标记数：K 线上线太多会糊成一片，也防止误点堆积 */
    private const val MAX_PER_STOCK = 12
    /** 同类型下价格差小于该值视为同一价位（浮点容差） */
    private const val SAME_PRICE_EPS = 0.005

    /** 读取某只股票的标记，按价格从高到低排序（图上从上往下看更顺） */
    fun levels(sp: SharedPreferencesModule, code: String): List<KeyLevelMark> {
        if (code.isBlank()) return emptyList()
        val raw = sp.getItem(KEY_PREFIX + code)
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val price = o.optDouble("price") ?: return@mapNotNull null
                if (price <= 0.0) return@mapNotNull null
                KeyLevelMark(
                    price = price,
                    kind = LevelKind.of(o.optString("kind")),
                    note = o.optString("note") ?: ""
                )
            }.sortedByDescending { it.price }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun count(sp: SharedPreferencesModule, code: String): Int = levels(sp, code).size

    /** 添加标记：同类型同价位不重复添加，超过上限返回 [AddLevelResult.FULL] */
    fun add(sp: SharedPreferencesModule, code: String, mark: KeyLevelMark): AddLevelResult {
        if (code.isBlank() || mark.price <= 0.0) return AddLevelResult.DUPLICATE
        val list = levels(sp, code)
        if (list.any { it.kind == mark.kind && abs(it.price - mark.price) < SAME_PRICE_EPS }) {
            return AddLevelResult.DUPLICATE
        }
        if (list.size >= MAX_PER_STOCK) return AddLevelResult.FULL
        save(sp, code, list + mark)
        return AddLevelResult.ADDED
    }

    /**
     * 按「价格 + 类型」精确删除。
     * ⚠️ 不能用价格单键：同一个价位可以同时被标成支撑和压力，那是两条独立记录，
     * 只按价格过滤会把它们一起删掉（真机验证时踩到过）。
     * 由 [add] 的重复判定保证 (price, kind) 在单只股票内唯一。
     */
    fun remove(sp: SharedPreferencesModule, code: String, price: Double, kind: LevelKind): Boolean {
        val list = levels(sp, code)
        val rest = list.filterNot { it.kind == kind && abs(it.price - price) < SAME_PRICE_EPS }
        if (rest.size == list.size) return false
        save(sp, code, rest)
        return true
    }

    /** 清空某只股票的标记（Kuikly 的 SP 模块没有 removeItem，写空串即可，读取侧按 isBlank 兜底） */
    fun clear(sp: SharedPreferencesModule, code: String) {
        sp.setItem(KEY_PREFIX + code, "")
        indexRemove(sp, code)
    }

    /**
     * 所有存在标记的股票代码，供备份导出枚举。
     *
     * ⚠️ [watchlistCodes] 是必要的兜底：索引是后加的，此前标记过的股票不会出现在索引里，
     * 只靠索引会让老用户的标记在导出时被静默丢掉。
     */
    fun codesWithLevels(
        sp: SharedPreferencesModule,
        watchlistCodes: List<String> = emptyList()
    ): List<String> {
        val set = LinkedHashSet<String>()
        loadIndex(sp).forEach { if (levels(sp, it).isNotEmpty()) set.add(it) }
        watchlistCodes.forEach { if (levels(sp, it).isNotEmpty()) set.add(it) }
        return set.toList()
    }

    // ---------------- 索引维护 ----------------

    /**
     * 备份恢复用：清空所有已知标记（索引里的 + [extraCodes] 兜底的），并重置索引。
     * 只动 `key_levels_*` 与索引键，不碰其他 SP 数据。
     */
    fun clearAll(sp: SharedPreferencesModule, extraCodes: List<String> = emptyList()) {
        val codes = LinkedHashSet<String>()
        codes.addAll(loadIndex(sp))
        codes.addAll(extraCodes)
        codes.forEach { sp.setItem(KEY_PREFIX + it, "") }
        saveIndex(sp, emptyList())
    }

    /** 备份恢复用：直接写入原始值之后，用它把索引重建回来 */
    fun rebuildIndex(sp: SharedPreferencesModule, codes: List<String>) {
        saveIndex(sp, codes.filter { levels(sp, it).isNotEmpty() })
    }

    private fun loadIndex(sp: SharedPreferencesModule): List<String> {
        val raw = sp.getItem(KEY_INDEX)
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                arr.optString(i)?.takeIf { it.isNotBlank() }
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun saveIndex(sp: SharedPreferencesModule, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        sp.setItem(KEY_INDEX, JSONObject().apply { put("list", arr) }.toString())
    }

    private fun indexAdd(sp: SharedPreferencesModule, code: String) {
        val list = loadIndex(sp)
        if (code in list) return
        saveIndex(sp, list + code)
    }

    private fun indexRemove(sp: SharedPreferencesModule, code: String) {
        val list = loadIndex(sp)
        if (code !in list) return
        saveIndex(sp, list.filterNot { it == code })
    }

    private fun save(sp: SharedPreferencesModule, code: String, list: List<KeyLevelMark>) {
        // 标记清空后从索引里摘掉，避免索引无限增长
        if (list.isEmpty()) indexRemove(sp, code) else indexAdd(sp, code)
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("price", m.price)
                    put("kind", m.kind.name)
                    put("note", m.note)
                }
            )
        }
        sp.setItem(KEY_PREFIX + code, JSONObject().apply { put("list", arr) }.toString())
    }
}
