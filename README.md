# AiStock_prices · AI 股票行情

基于 **Kuikly**（Kotlin Multiplatform UI 框架）的跨端 AI 股票行情原型 Demo。
  
内置 A 股自选列表、实时行情、分时/日 K 线图表、基于 LLM 的 AI 分析解读与多会话 AI 问答（可配置任意 OpenAI 兼容服务）。

当前版本：**v1.9.24**

| 平台        | 状态                                          |
| --------- | ------------------------------------------- |
| Android   | 已真机验证（v1.9.24 APK 可构建安装）                     |
| HarmonyOS | 宿主已适配（桥接 / 路由 / 主题 / 入口），需在 DevEco Studio 编译验证 |
| iOS       | 宿主已适配（桥接 / 路由 / 主题 / 入口），需在 macOS + Xcode 编译验证 |
| H5 / 小程序  | 未适配（需补宿主工程与跨域方案）                            |

## 功能特性

### 行情

| 模块    | 说明                              |
| ----- | ------------------------------- |
| 自选股列表 | 内置 10 只知名 A 股；支持手动添加 / 删除，本地持久化 |
| 置顶管理  | 左滑弹出操作菜单，置顶 / 取消置顶 / 删除一体化      |
| 个股详情  | 报价区、分时走势图（面积填充）、日 K 线蜡烛图、行情详情表  |
| 分时点选  | 点击分时图显示十字线与该时点价格气泡              |
| 本地缓存  | 行情快照落盘，接口临时失效页面不空白；60 秒轮询刷新     |
| 超时兜底  | 详情加载 8 秒未就绪自动使用本地缓存，避免一直空白      |

> 涨红跌绿遵循 A 股配色惯例。

### AI

| 模块      | 说明                                          |
| ------- | ------------------------------------------- |
| AI 分析解读 | 趋势判断 / 买卖参考点位 / 操作建议 / 风险提醒 / 行情总结          |
| AI 问答   | 多会话聊天（本地持久化），支持修改 / 删除 / 重新生成消息             |
| 股票代码块   | 回答中以 \`\`\`stock 标记的代码块自动渲染为实时行情卡片          |
| 可配置 LLM | Base URL / API Key / 模型运行时输入，「连接获取模型」下拉点选   |
| 预设管理    | 保存后自动以预设名/模型名创建预设；一键切换、编辑（名称/URL/Key/模型）、删除 |
| 风险等级    | 详情页顶部小字色块展示风险（低/中/高），未配置 API 不显示            |
| 降级策略    | LLM 调用失败自动切换本地规则生成分析，演示链路不中断                |
| 生成中断    | 停止按钮 / 退出页面自动取消在途请求（请求序号门控，避免旧响应串台）         |

### 体验

| 模块   | 说明                                        |
| ---- | ----------------------------------------- |
| 夜间模式 | 跟随系统 / 强制浅色 / 强制深色三态，菜单一键循环切换，深色偏好本地持久化   |
| 首启引导 | 首次启动询问是否观看 8 步功能引导，高亮挖孔 + 说明卡；菜单「功能说明」可重看 |
| 密钥安全 | API Key 密码态输入（掩码 + 眼睛切换）+ 输入框一键清空         |

## 技术栈

- **框架**：[Kuikly](https://github.com/Tencent-TDS/KuiklyUI)（Kuikly DSL）+ Kotlin Multiplatform
- **平台**：业务代码全部位于 `shared/commonMain`；Android 已验证，iOS / HarmonyOS 宿主已适配，H5 / 小程序未适配
- **行情数据**：腾讯免费行情接口（实时报价 / 分时 / 日 K，前复权）
- **AI 服务**：OpenAI 兼容 `chat/completions` 协议，任意服务商
- **构建**：Gradle 8.5 · AGP 8.2.2 · Kotlin 2.1.21 · Kuikly 2.7.0
- **图表 / 表格**：自研 `chart`（LineChart / CandleStickChart / BarChart）与 `table`（KuiklyTable）模块

## 快速开始

### 环境要求

| 工具          | 版本                                  |
| ----------- | ----------------------------------- |
| JDK         | **17**（必须，18+ 会导致构建失败）              |
| Android SDK | API 23+（compileSdk 34，targetSdk 30） |
| Gradle      | 8.5（wrapper 自带，无需预装）                |

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

### 构建 HarmonyOS

```bat
rem 前置：DevEco Studio 5.1.0+（API >= 18），并设置环境变量
rem   OHOS_SDK_HOME = <DevEco>\sdk\default\openharmony
rem   TOOL_HOME     = <DevEco 安装根目录>
build_ohos.bat            rem 默认 Debug，或 build_ohos.bat Release
```

脚本做三件事：用 `settings.ohos.gradle.kts` 配置集编译 `libshared.so`（Kotlin 2.0.21-KBA-010 工具链）→ 拷贝 so 到 `ohosApp/entry/libs/arm64-v8a/` → 拷贝 `libshared_api.h` 到 `ohosApp/entry/src/main/cpp/thirdparty/biz_entry/`，之后用 DevEco Studio 打开 `ohosApp` 签名运行。

关键约定：

- 鸿蒙走独立构建链路（`-c settings.ohos.gradle.kts`），依赖用 `-2.0.21-ohos` 变体；Markdown 为 `KuiklyMarkdown:1.0.6-2.0.21-ohos`，**共享层无需改动**
- `kotlinx-coroutines` / `kotlinx-serialization` 在鸿蒙链路中不引入（官方与镜像无 ohosArm64 klib 变体，业务代码也未使用）
- 页面导航走框架 `RouterModule`，由 `ohosApp/.../adapter/RouterAdapter.ets` 接管；主题由 `ThemeController.ets` + `PersistentStorage` 实现

### 构建 iOS

需要 macOS + Xcode + CocoaPods：

```bash
cd iosApp && pod install && open iosApp.xcworkspace
```

宿主已适配的部分：

- `HRBridgeModule`：实现 `toast` / `currentTimestamp` / `dateFormatter` / `setThemeMode` / `closePage`（iOS 侧 Module 无需注册，类名与共享层 `moduleName` 一致即可被运行时查找到）
- `ThemeController.swift`：三态主题持久化（UserDefaults）+ 通知触发页面重建；`KuiklyRenderViewController` 在 `p_mergeExtParamsWithOriditalParam` 中为每个页面注入 `isNightMode` / `themeMode`，**子页面（openPage 推入的）也会带上**
- `KRRouterHandler`：负责页面 push / pop；`Info.plist` 已补 `CFBundleDisplayName=AiStock` 与 ATS 放行（对齐 Android 的 cleartext 配置，AI Base URL 可能为非 HTTPS）

### 配置 AI 分析

1. 首页底部任务栏切到「AI 设置」
2. 填写 **Base URL**（OpenAI 兼容，如 `https://api.deepseek.com/v1/chat/completions`）与 **API Key**
3. 点击连接自动读取模型列表，或手动输入模型名
4. 保存后进入个股详情页会自动触发 AI 分析；也可点「开始 AI 分析」手动触发

**预设**：每次保存自动以模型名创建一个预设，可一键切换、删除。预设互斥启用，启用新预设会自动停用其他预设。

### AI 问答

1. 首页底部任务栏切到「AI 问答」，新建或切换会话
2. 直接提问（如"分析一下 贵州茅台"）；回答包含股票时可用 \`\`\`stock 标记引用实时行情
3. 点消息右侧「···」可修改（重新提问）/ 删除 / 重新生成该条消息
4. 会话自动本地持久化，重启应用不丢失

### 夜间模式

顶栏「☰」菜单中切换，顺序为 跟随系统 → 浅色 → 深色 → 跟随系统。
  
切换通过宿主 `AppCompatDelegate.setDefaultNightMode` 触发页面重建，配色由 `shared` 的 `ThemePalette` 语义 token 统一提供。

三端重建机制等价：Android 用 Activity recreate；iOS 用 `NotificationCenter` 通知 + SwiftUI `.id()` 重建；鸿蒙用 `AppStorage` 版本号 + `@Watch` 销毁重建 Kuikly 实例。三端都在创建页面时注入 `isNightMode` / `themeMode`（与 `BasePager` 读取的键名一致）。

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
├── androidApp/                      # Android 宿主工程（已验证）
│   └── src/main/java/.../           # ThemeController（主题持久化与重建）、桥接模块、各类 Adapter
├── iosApp/                          # iOS 宿主（已适配业务）
│   └── iosApp/
│       ├── ThemeController.swift    # 主题三态 + UserDefaults + 重建通知
│       ├── ContentView.swift        # 入口：pageName = stock_list
│       └── KuiklyExpand/            # HRBridgeModule（桥接）、KRRouterHandler（路由）、VC 封装
├── ohosApp/                         # 鸿蒙宿主（已适配业务）
│   └── entry/src/main/ets/
│       ├── pages/Index.ets          # 入口：默认页面 stock_list，监听主题版本重建
│       ├── entryability/            # EntryAbility（PersistentStorage 主题持久化）
│       └── kuikly/                  # ThemeController.ets、KRBridgeModule.ets、各类 Adapter
├── settings.ohos.gradle.kts         # 鸿蒙构建配置集（含 chart / table）
├── build.ohos.gradle.kts            # 鸿蒙根构建脚本（Kotlin 2.0.21-KBA-010）
├── shared/build.ohos.gradle.kts     # 鸿蒙变体依赖（-2.0.21-ohos）
└── build_ohos.bat                   # 鸿蒙产物一键构建 + 拷贝
```

## 数据源与说明

- 行情：腾讯免费公开接口，仅供学习演示，请勿用于商业用途或高频调用
- AI：需自备 OpenAI 兼容服务的 Key，应用内不内置任何密钥，配置仅保存在本机
- 本项目为技术演示，所有分析与问答结果**不构成任何投资建议**

## 已知限制

- 行情为轮询拉取，非推送；非交易时段数据为上一交易日快照
- iOS / 鸿蒙宿主代码尚未在对应 IDE 中编译验证，首次编译可能需按注释意图微调 API 或工程配置
- 鸿蒙端 `dateFormatter` / `currentTimestamp` 为同步桥接调用，模板默认 `syncMode() = false`；若鸿蒙上「列表更新时间」显示为空，将 `KRBridgeModule.ets` 的 `syncMode()` 改为 `true` 再验证
