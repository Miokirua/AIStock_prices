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
    }

    private fun save(sp: SharedPreferencesModule, code: String, list: List<KeyLevelMark>) {
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
