# AiStock_prices · AI 股票行情

基于 **Kuikly**（Kotlin Multiplatform UI 框架）的跨端 AI 股票行情原型 Demo。
内置 A 股自选列表、实时行情、分时/日K线图表，以及基于 LLM 的 AI 分析与解读（可配置任意 OpenAI 兼容服务）。

##  功能特性

| 模块 | 说明 |
|------|------|
|  自选股列表 | 内置 10 只知名 A 股；支持手动添加 / 删除，本地持久化 |
|  个股详情 | 报价区、分时走势图（面积填充）、日K线蜡烛图、行情详情表 |
|  AI 分析解读 | 趋势判断 / 买卖参考点位 / 操作建议 / 风险提醒 / 行情总结 |
|  可配置 LLM | Base URL / API Key / 模型运行时输入，支持自动读取可用模型 |
|  预设切换 | 保存后自动以模型名创建预设，一键切换（含 Key）、可编辑删除 |
|  本地缓存 | 行情快照落盘，接口临时失效页面不空白；60 秒轮询刷新 |
|  风险等级 | 详情页顶部小字色块展示风险（低/中/高），未配置 API 不显示 |
|  降级策略 | LLM 调用失败自动切换本地规则生成分析，演示链路不中断 |

> 涨红跌绿遵循 A 股配色惯例。

##  技术栈

- **框架**：[Kuikly](https://github.com/Tencent-TDS/KuiklyUI)（Kuikly DSL）+ Kotlin Multiplatform
- **平台**：Android 优先，业务代码全部位于 `shared/commonMain`，可扩展 iOS / HarmonyOS / H5
- **行情数据**：腾讯免费行情接口（实时报价 / 分时 / 日K，前复权）
- **AI 服务**：OpenAI 兼容 `chat/completions` 协议，任意服务商
- **构建**：Gradle 8.5 · AGP 8.2.2 · Kotlin 2.1.21 · Kuikly 2.7.0

##  快速开始

### 环境要求

| 工具 | 版本 |
|------|------|
| JDK | **17**（必须，18+ 会导致构建失败） |
| Android SDK | API 23+（compileSdk 34） |
| Gradle | 8.5（wrapper 自带） |

### 构建运行

```bash
# 构建 Android Debug APK
JAVA_HOME="C:\Program Files\Java\jdk-17" ./gradlew :androidApp:assembleDebug

# 产物位置
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

安装到设备/模拟器后启动，首页即自选股列表（自动拉取实时行情）。

### 配置 AI 分析

1. 首页底部任务栏切到「AI 设置」
2. 填写 **Base URL**（OpenAI 兼容，如 `https://api.deepseek.com/v1/chat/completions`）与 **API Key**
3. 点「按 URL + Key 获取可用模型」自动读取模型列表，或手动输入模型名
4. 保存后进入个股详情页会自动触发 AI 分析；也可点「开始 AI 分析」手动触发

**预设**：每次保存自动以模型名创建一个预设，可一键切换、删除。

##  工程结构

```
├── shared/                          # 跨端业务代码（核心）
│   └── src/commonMain/kotlin/com/example/aistock_prices/
│       ├── base/                    # BasePager、桥接模块、工具
│       ├── stock/
│       │   ├── data/                # 数据模型、腾讯接口客户端、本地缓存、自选管理
│       │   ├── ui/                  # 配色（A股红涨绿跌）、格式化工具
│       │   ├── ai/                  # AI 分析服务、配置表单组件、设置页
│       │   ├── StockListPage.kt     # 首页：自选列表 + 底部任务栏
│       │   └── StockDetailPage.kt   # 详情：报价/分时/K线/AI/行情表
│       └── RouterPage.kt            # 框架路由示例
├── chart/                           # 图表组件（LineChart / CandleStickChart 自研）
├── table/                           # 表格组件（KuiklyTable）
├── androidApp/                      # Android 宿主工程
└── iosApp/ / ohosApp/               # iOS / 鸿蒙宿主（脚手架预留）
```
