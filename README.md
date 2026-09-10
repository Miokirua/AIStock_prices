# AiStock_prices · AI 股票行情

基于 **Kuikly**（Kotlin Multiplatform UI 框架）的跨端 AI 股票行情原型 Demo。
内置 A 股自选列表、实时行情、分时/日 K 线图表、基于 LLM 的 AI 分析解读与多会话 AI 问答（可配置任意 OpenAI 兼容服务）。

当前版本：**v1.9.22**（Android 已真机验证；iOS / 鸿蒙为脚手架预留）

## 功能特性

### 行情

| 模块 | 说明 |
|------|------|
| 自选股列表 | 内置 10 只知名 A 股；支持手动添加 / 删除，本地持久化 |
| 置顶管理 | 长按或点「···」弹出操作菜单，置顶 / 取消置顶 / 删除一体化 |
| 左滑操作 | 列表项左滑露出操作按钮，与操作菜单互不冲突 |
| 个股详情 | 报价区、分时走势图（面积填充）、日 K 线蜡烛图、行情详情表 |
| 分时点选 | 点击分时图显示十字线与该时点价格气泡 |
| 本地缓存 | 行情快照落盘，接口临时失效页面不空白；60 秒轮询刷新 |
| 超时兜底 | 详情加载 8 秒未就绪自动使用本地缓存，避免一直空白 |

> 涨红跌绿遵循 A 股配色惯例。

### AI

| 模块 | 说明 |
|------|------|
| AI 分析解读 | 趋势判断 / 买卖参考点位 / 操作建议 / 风险提醒 / 行情总结 |
| AI 问答 | 多会话聊天（本地持久化），支持修改 / 删除 / 重新生成消息 |
| 股票代码块 | 回答中以 ```stock 标记的代码块自动渲染为实时行情卡片 |
| 可配置 LLM | Base URL / API Key / 模型运行时输入，「连接获取模型」下拉点选 |
| 预设管理 | 保存后自动以预设名/模型名创建预设；一键切换、编辑（名称/URL/Key/模型）、删除 |
| 风险等级 | 详情页顶部小字色块展示风险（低/中/高），未配置 API 不显示 |
| 降级策略 | LLM 调用失败自动切换本地规则生成分析，演示链路不中断 |
| 生成中断 | 停止按钮 / 退出页面自动取消在途请求（请求序号门控，避免旧响应串台） |

### 体验

| 模块 | 说明 |
|------|------|
| 夜间模式 | 跟随系统 / 强制浅色 / 强制深色三态，菜单一键循环切换，深色偏好本地持久化 |
| 首启引导 | 首次启动询问是否观看 8 步功能引导，高亮挖孔 + 说明卡；菜单「功能说明」可重看 |
| 密钥安全 | API Key 密码态输入（掩码 + 眼睛切换）+ 输入框一键清空 |

## 技术栈

- **框架**：[Kuikly](https://github.com/Tencent-TDS/KuiklyUI)（Kuikly DSL）+ Kotlin Multiplatform
- **平台**：Android 优先，业务代码全部位于 `shared/commonMain`，可扩展 iOS / HarmonyOS / H5
- **行情数据**：腾讯免费行情接口（实时报价 / 分时 / 日 K，前复权）
- **AI 服务**：OpenAI 兼容 `chat/completions` 协议，任意服务商
- **构建**：Gradle 8.5 · AGP 8.2.2 · Kotlin 2.1.21 · Kuikly 2.7.0
- **图表 / 表格**：自研 `chart`（LineChart / CandleStickChart / BarChart）与 `table`（KuiklyTable）模块

## 快速开始

### 环境要求

| 工具 | 版本 |
|------|------|
| JDK | **17**（必须，18+ 会导致构建失败） |
| Android SDK | API 23+（compileSdk 34，targetSdk 30） |
| Gradle | 8.5（wrapper 自带，无需预装） |

### 构建运行

```bash
# 构建 Android Debug APK（Windows 下显式指定 JDK 17）
JAVA_HOME="C:\Program Files\Java\jdk-17" ./gradlew :androidApp:assembleDebug

# 产物位置
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

安装到设备/模拟器后启动，首页即自选股列表（自动拉取实时行情）。应用名与包名：

- 桌面显示名：`AiStock`（`androidApp/src/main/res/values/strings.xml` 的 `app_name`）
- 包名：`com.example.aistock_prices`（`androidApp/build.gradle.kts` 的 `applicationId`）

### 配置 AI 分析

1. 首页底部任务栏切到「AI 设置」
2. 填写 **Base URL**（OpenAI 兼容，如 `https://api.deepseek.com/v1/chat/completions`）与 **API Key**
3. 点「按 URL + Key 获取可用模型」自动读取模型列表，或手动输入模型名
4. 保存后进入个股详情页会自动触发 AI 分析；也可点「开始 AI 分析」手动触发

**预设**：每次保存自动以模型名创建一个预设，可一键切换、删除。预设互斥启用，启用新预设会自动停用其他预设。

### AI 问答

1. 首页底部任务栏切到「AI 问答」，新建或切换会话
2. 直接提问（如"分析一下 贵州茅台"）；回答包含股票时可用 ```stock 标记引用实时行情
3. 点消息右侧「···」可修改（重新提问）/ 删除 / 重新生成该条消息
4. 会话自动本地持久化，重启应用不丢失

### 夜间模式

顶栏「☰」菜单中切换，顺序为 跟随系统 → 浅色 → 深色 → 跟随系统。
切换通过宿主 `AppCompatDelegate.setDefaultNightMode` 触发页面重建，配色由 `shared` 的 `ThemePalette` 语义 token 统一提供。

## 工程结构

```
├── shared/                          # 跨端业务代码（核心）
│   └── src/commonMain/kotlin/com/example/aistock_prices/
│       ├── base/                    # BasePager、桥接模块、工具扩展
│       ├── stock/
│       │   ├── data/                # 数据模型、腾讯接口客户端、本地缓存、自选管理
│       │   ├── ui/                  # 主题色板（Theme.kt）、格式化与通用 UI
│       │   ├── ai/                  # AI 服务、问答视图、配置与预设管理
│       │   │   ├── AiAnalysisService.kt   # AI 分析（含本地规则降级）
│       │   │   ├── AiChatView.kt          # 多会话问答视图（消息操作菜单）
│       │   │   ├── AiChatPage.kt          # 问答页（退出自动中断生成）
│       │   │   ├── AiConfigView.kt        # LLM 配置表单
│       │   │   ├── AiConfigPage.kt        # 配置页
│       │   │   ├── PresetDetailPage.kt    # 预设新建/编辑
│       │   │   ├── ResultDetailPage.kt    # 分析结果详情
│       │   │   └── ConversationStore.kt   # 会话持久化
│       │   ├── StockListPage.kt     # 首页：自选列表 + 底部任务栏 + 首启引导
│       │   └── StockDetailPage.kt   # 详情：报价/分时/K线/AI/行情表
│       └── RouterPage.kt            # 框架路由示例
├── chart/                           # 图表组件（Line / CandleStick / Bar，自研渲染器）
├── table/                           # 表格组件（KuiklyTable）
├── androidApp/                      # Android 宿主工程
│   └── src/main/java/.../           # ThemeController（主题持久化与重建）、桥接模块、各类 Adapter
└── iosApp/ / ohosApp/               # iOS / 鸿蒙宿主（脚手架预留，未适配业务）
```

## 数据源与说明

- 行情：腾讯免费公开接口，仅供学习演示，请勿用于商业用途或高频调用
- AI：需自备 OpenAI 兼容服务的 Key，应用内不内置任何密钥，配置仅保存在本机
- 本项目为技术演示，所有分析与问答结果**不构成任何投资建议**

## 更新日志（v1.9.x）

| 版本 | 内容 |
|------|------|
| v1.9.22 | 修复安装后应用名显示为包名的问题：manifest 补 `android:label` |
| v1.9.21 | 引导高亮孔改真透明（四块遮罩拼孔）、菜单项对位修正、结束后恢复交互 |
| v1.9.20 | 引导遮罩修复（坐标改为相对 dp 公式，跨分辨率稳定）+ 菜单文本对齐 |
| v1.9.19 | 引导全部 8 步统一走布局规则计算，不再依赖 `convertFrame` |
| v1.9.18 | 修复首启引导高亮孔位置错乱 |
| v1.9.17 | 修复首启引导浮窗定位错误 |
| v1.9.16 | 修复首启引导第一步不可见 |
| v1.9.15 | 新增首次启动引导浮窗（8 步） |
| v1.9.14 | 列表行价格/涨跌严格双列对齐 |
| v1.9.13 | 列表对齐与 AI 问答修复 |
| v1.9.12 | 修复 K 线页「问 AI」闪退 |
| v1.9.11 | AI 问答交互与配置修复 |
| v1.9.10 | 分时图点选交互：十字线 + 价格气泡 |
| v1.9.5~9 | 夜间模式、键盘遮挡、密钥掩码与清空、数据 AI 融合联动等（详见 `git log`） |

## 已知限制

- 引导高亮孔为直角矩形（Kuikly 无遮罩裁剪 API）
- 仅 Android 端完成完整真机验证；iOS 需 macOS、鸿蒙需 DevEco Studio 才能构建
- 行情为轮询拉取，非推送；非交易时段数据为上一交易日快照
