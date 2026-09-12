package com.example.aistock_prices.stock.data

/**
 * 自选股元数据：腾讯行情代码（带市场前缀）+ 本地维护的中文名称。
 * 说明：qt.gtimg.cn 实时接口回包为 GBK 编码，而 Kuikly 网络层固定 UTF-8 解码，
 * 中文名称会乱码，因此名称一律以本地维护为准（仅解析 ASCII 数字字段）。
 */
data class StockMeta(
    val code: String,   // 腾讯代码，如 sh600519 / sz000001
    val name: String,   // 本地维护的中文名称
    val pinned: Boolean = false, // 是否置顶（置顶项排在最前）
    /** 所属分组名；空串表示「未分组」（老数据无该字段时也落到空串，无需迁移） */
    val group: String = ""
) {
    val symbol: String get() = code.removePrefix("sh").removePrefix("sz").removePrefix("hk")
}

/** 实时行情快照 */
data class StockQuote(
    val code: String,
    val name: String,
    val price: Double,        // 最新价
    val prevClose: Double,    // 昨收
    val open: Double,         // 今开
    val high: Double,         // 最高
    val low: Double,          // 最低
    val change: Double,       // 涨跌额
    val changePercent: Double, // 涨跌幅 %
    val volume: Long,         // 成交量（手）
    val amount: Double,       // 成交额（万元）
    val turnover: Double,     // 换手率 %
    val amplitude: Double,    // 振幅 %
    val avgPrice: Double,     // 均价
    val time: String          // 行情时间 yyyyMMddHHmmss
) {
    /** 纯数字代码，如 600519 */
    val symbol: String get() = code.removePrefix("sh").removePrefix("sz").removePrefix("hk")
}

/** 分时数据点 */
data class MinutePoint(
    val time: String,   // HHmm
    val price: Double,
    val volume: Long,   // 手
    val amount: Double  // 元
)

/** 日K线 */
data class KLineBar(
    val date: String,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Long // 手
)

/** 内置默认自选股（首次启动展示，用户可增删） */
object Watchlist {
    private const val KEY_STOCKS = "watchlist_stocks"

    private val defaults = listOf(
        StockMeta("sh600519", "贵州茅台"),
        StockMeta("sz300750", "宁德时代"),
        StockMeta("sz002594", "比亚迪"),
        StockMeta("sh600036", "招商银行"),
        StockMeta("sz000001", "平安银行"),
        StockMeta("sz000858", "五粮液"),
        StockMeta("sh601318", "中国平安"),
        StockMeta("sh600900", "长江电力"),
        StockMeta("sh601012", "隆基绿能"),
        StockMeta("sz300059", "东方财富")
    )

    /** 当前自选列表：未保存过时返回内置默认；置顶项始终排在最前（稳定排序） */
    fun stocks(sp: com.tencent.kuikly.core.module.SharedPreferencesModule): List<StockMeta> {
        val raw = sp.getItem(KEY_STOCKS)
        val list = if (raw.isBlank()) defaults else parse(raw)
        return list.sortedByDescending { it.pinned }
    }

    /** 添加（已存在返回 false；新添加默认不置顶） */
    fun add(sp: com.tencent.kuikly.core.module.SharedPreferencesModule, meta: StockMeta): Boolean {
        val list = stocks(sp)
        if (list.any { it.code == meta.code }) return false
        save(sp, list + meta.copy(pinned = false))
        return true
    }

    /** 删除 */
    fun remove(sp: com.tencent.kuikly.core.module.SharedPreferencesModule, code: String): Boolean {
        val list = stocks(sp)
        val newList = list.filter { it.code != code }
        if (newList.size == list.size) return false
        save(sp, newList)
        return true
    }

    /** 置顶 / 取消置顶 */
    fun pin(sp: com.tencent.kuikly.core.module.SharedPreferencesModule, code: String, pinned: Boolean): Boolean {
        val list = stocks(sp)
        var changed = false
        val newList = list.map {
            if (it.code == code && it.pinned != pinned) {
                changed = true
                it.copy(pinned = pinned)
            } else it
        }
        if (!changed) return false
        save(sp, newList)
        return true
    }

    /** 是否为置顶 */
    fun isPinned(sp: com.tencent.kuikly.core.module.SharedPreferencesModule, code: String): Boolean {
        return stocks(sp).firstOrNull { it.code == code }?.pinned == true
    }

    // ---------------- 分组 ----------------
    // 归属模型是「单归属」：一只股票只属于一个分组，空串 = 未分组。
    // 分组名本身就是标识（不额外引入 id），重命名时同步改写股票上的 group 字段。

    private const val KEY_GROUPS = "watchlist_groups"

    /** 分组名长度上限（chip 上要显示，太长会撑破布局） */
    const val MAX_GROUP_NAME = 8

    /** 分组数量上限 */
    const val MAX_GROUPS = 12

    /** 「全部」是列表页的虚拟分组（聚合所有股票），不允许用户建同名分组 */
    const val GROUP_ALL = "全部"

    /** 未分组的展示名（内部仍用空串表示） */
    const val GROUP_NONE_LABEL = "未分组"

    /**
     * 规范化用户输入的分组名：折叠内部空白 + 截断超长。
     * 返回空串表示非法（空名），调用方应拒绝。
     */
    fun normalizeGroupName(raw: String): String {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) return ""
        if (name == GROUP_ALL || name == GROUP_NONE_LABEL) return ""
        return if (name.length > MAX_GROUP_NAME) name.substring(0, MAX_GROUP_NAME) else name
    }

    /** 全部分组名，按用户维护的顺序 */
    fun groups(sp: com.tencent.kuikly.core.module.SharedPreferencesModule): List<String> {
        val raw = sp.getItem(KEY_GROUPS)
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = com.tencent.kuikly.core.nvi.serialization.json.JSONObject(raw).optJSONArray("list")
                ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /** 新建分组；已存在或超出上限返回 false */
    fun addGroup(sp: com.tencent.kuikly.core.module.SharedPreferencesModule, name: String): Boolean {
        val clean = normalizeGroupName(name)
        if (clean.isEmpty()) return false
        val list = groups(sp)
        if (list.any { it == clean }) return false
        if (list.size >= MAX_GROUPS) return false
        saveGroups(sp, list + clean)
        return true
    }

    /** 重命名分组：同步改写组内股票的 group 字段，保持归属不丢 */
    fun renameGroup(
        sp: com.tencent.kuikly.core.module.SharedPreferencesModule,
        from: String,
        to: String
    ): Boolean {
        val clean = normalizeGroupName(to)
        if (clean.isEmpty() || from == clean) return false
        val list = groups(sp)
        if (from !in list) return false
        if (list.any { it == clean }) return false
        saveGroups(sp, list.map { if (it == from) clean else it })
        // 组内股票改挂新组名
        val stocks = stocks(sp)
        if (stocks.any { it.group == from }) {
            save(sp, stocks.map { if (it.group == from) it.copy(group = clean) else it })
        }
        return true
    }

    /**
     * 删除分组：只解散分组，组内股票回到「未分组」（仍留在自选里）。
     * 采用单归属模型，所以「谁在这个组里」等价于 `meta.group == name`，不需要额外的成员表。
     */
    fun removeGroup(sp: com.tencent.kuikly.core.module.SharedPreferencesModule, name: String): Boolean {
        val list = groups(sp)
        if (name !in list) return false
        saveGroups(sp, list.filter { it != name })
        val stocks = stocks(sp)
        if (stocks.any { it.group == name }) {
            save(sp, stocks.map { if (it.group == name) it.copy(group = "") else it })
        }
        return true
    }

    /** 把某只股票移动到目标分组（空串 = 未分组）。目标组不存在时返回 false */
    fun moveToGroup(
        sp: com.tencent.kuikly.core.module.SharedPreferencesModule,
        code: String,
        group: String
    ): Boolean {
        if (group.isNotEmpty() && group !in groups(sp)) return false
        val list = stocks(sp)
        val target = list.firstOrNull { it.code == code } ?: return false
        if (target.group == group) return false
        save(sp, list.map { if (it.code == code) it.copy(group = group) else it })
        return true
    }

    /**
     * 调整 [name] 在分组数组中的位置（数组顺序即 chips 显示顺序）。
     * [delta] 只接受 -1（上移）/ +1（下移）；越界或分组不存在时返回 false，不改数据。
     */
    fun moveGroup(
        sp: com.tencent.kuikly.core.module.SharedPreferencesModule,
        name: String,
        delta: Int
    ): Boolean {
        if (delta != -1 && delta != 1) return false
        val list = groups(sp)
        val i = list.indexOf(name)
        if (i < 0) return false
        val j = i + delta
        if (j < 0 || j >= list.size) return false
        val swapped = list.toMutableList()
        val tmp = swapped[i]
        swapped[i] = swapped[j]
        swapped[j] = tmp
        saveGroups(sp, swapped)
        return true
    }

    /** 统计各分组的股票数（key 为空串表示未分组），供 chip 上显示数量 */
    fun groupCounts(sp: com.tencent.kuikly.core.module.SharedPreferencesModule): Map<String, Int> {
        return stocks(sp).groupingBy { it.group }.eachCount()
    }

    private fun saveGroups(sp: com.tencent.kuikly.core.module.SharedPreferencesModule, list: List<String>) {
        val arr = com.tencent.kuikly.core.nvi.serialization.json.JSONArray()
        list.forEach { name ->
            arr.put(
                com.tencent.kuikly.core.nvi.serialization.json.JSONObject().apply { put("name", name) }
            )
        }
        sp.setItem(
            KEY_GROUPS,
            com.tencent.kuikly.core.nvi.serialization.json.JSONObject().apply { put("list", arr) }.toString()
        )
    }

    private fun save(sp: com.tencent.kuikly.core.module.SharedPreferencesModule, list: List<StockMeta>) {
        val arr = com.tencent.kuikly.core.nvi.serialization.json.JSONArray()
        list.forEach { meta ->
            arr.put(
                com.tencent.kuikly.core.nvi.serialization.json.JSONObject().apply {
                    put("code", meta.code)
                    put("name", meta.name)
                    put("pinned", meta.pinned)
                    put("group", meta.group)
                }
            )
        }
        sp.setItem(KEY_STOCKS, com.tencent.kuikly.core.nvi.serialization.json.JSONObject().apply { put("list", arr) }.toString())
    }

    private fun parse(raw: String): List<StockMeta> {
        return try {
            val obj = com.tencent.kuikly.core.nvi.serialization.json.JSONObject(raw)
            val arr = obj.optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val code = o.optString("code") ?: return@mapNotNull null
                if (code.isBlank()) return@mapNotNull null
                StockMeta(
                    code = code,
                    name = o.optString("name").ifBlank { code },
                    pinned = o.optBoolean("pinned") ?: false,
                    group = o.optString("group") ?: ""
                )
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /** 归一化用户输入：600519 -> sh600519；带前缀原样返回 */
    fun normalizeCode(input: String): String {
        val code = input.trim().lowercase()
        if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("bj")) {
            return code
        }
        if (code.length != 6 || code.any { !it.isDigit() }) return code
        return when (code[0]) {
            '6' -> "sh$code"
            '0', '3' -> "sz$code"
            '4', '8' -> "bj$code"
            else -> "sh$code"
        }
    }

    fun nameOf(code: String): String {
        return defaults.firstOrNull { it.code == code }?.name ?: code
    }
}
