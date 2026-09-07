package com.example.aistock_prices.stock.ai

/**
 * AI 分析服务运行时配置（在应用内输入并持久化）。
 */
data class AiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = ""
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

/**
 * 用户保存的配置预设（切换即切换整套配置，含 Key）。
 * 保存配置时自动以模型名创建一个预设；同 (baseUrl, apiKey) 则更新。
 *
 * @param enabled 是否启用：启用中的预设可被一键设为当前生效配置；
 *                停用的预设保留在列表中但不会被自动应用
 * @param failed 连接校验失败标记：保存时自动校验（URL/Key/模型），
 *               无法连接时标红展示，提醒用户修正
 */
data class AiPreset(
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean = true,
    val failed: Boolean = false
)

/**
 * 结构化关键价位（用于画 K 线参考线 + 点选匹配）。
 *
 * @param price 具体价格数值
 * @param type 价位类型：support(支撑) / resistance(压力)
 * @param label 简短标签，如「短线支撑」
 * @param desc 一句话说明
 */
data class KeyLevel(
    val price: Double,
    val type: String,
    val label: String,
    val desc: String = ""
) {
    companion object {
        const val TYPE_SUPPORT = "support"
        const val TYPE_RESISTANCE = "resistance"
    }
}

/**
 * 结构化指标注解（用于详情表对应行高亮 + 附 AI 解读）。
 *
 * @param key 指标键：pe/turnover/amplitude/volume/amount/avgPrice/high/low/open/prevClose
 * @param name 中文名，如「换手率」
 * @param comment 一句解读
 */
data class MetricInsight(
    val key: String,
    val name: String,
    val comment: String = ""
)

/**
 * AI 分析结果。
 *
 * @param trend 趋势判断标签，如 强势看多/温和看空/区间震荡
 * @param trendDesc 趋势判断说明
 * @param suggestion 操作建议
 * @param buyPoints 买入参考点位列表
 * @param sellPoints 卖出参考点位列表
 * @param risks 风险提醒列表
 * @param summary 行情总结
 * @param riskLevel 风险等级：低 / 中 / 高（详情页顶部展示）
 * @param source 来源：AI / 本地规则（降级）
 * @param markdown AI 深度分析正文（Markdown 格式，供底部可读展示；空则无正文）
 * @param keyLevels 结构化关键价位（支撑/压力，用于 K 线参考线与点选联动）
 * @param metrics 结构化指标注解（用于详情表行高亮与联动）
 */
data class AiAnalysisResult(
    val trend: String,
    val trendDesc: String,
    val suggestion: String,
    val buyPoints: List<String>,
    val sellPoints: List<String>,
    val risks: List<String>,
    val summary: String,
    val riskLevel: String = RISK_MID,
    val source: String,
    val markdown: String = "",
    val keyLevels: List<KeyLevel> = emptyList(),
    val metrics: List<MetricInsight> = emptyList()
) {
    companion object {
        const val SOURCE_AI = "AI"
        const val SOURCE_RULE = "本地规则"
        const val RISK_LOW = "低"
        const val RISK_MID = "中"
        const val RISK_HIGH = "高"
    }
}
