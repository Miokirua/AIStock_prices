# AiStock_prices · AI 股票行情

基于 **Kuikly**（Kotlin Multiplatform UI 框架）的跨端 AI 股票行情原型 Demo。
  
内置 A 股自选列表（分组管理 / 名称搜索添加 / 多选批量）、实时行情、分时与日周月 K 线图表（缩放 · 平移 · 十字光标读数 · 关键位标记）、基于 LLM 的 AI 分析解读与多会话 AI 问答（可配置任意 OpenAI 兼容服务）。

| 平台        | 状态                                                                             |
| --------- | ------------------------------------------------------------------------------ |
| Android   | **已真机验证**（当前 v1.9.35，APK 可构建安装）                                                |
| iOS       | 宿主已适配，**已通过 GitHub Actions（macos-14）编译验证**，模拟器 Debug 包可构建              |
| HarmonyOS | 宿主与构建链路已打通（共享层出 `libshared.so` + 桥接 / 路由 / 主题 / 入口） |

## 预览
<img width="873" height="1920" alt="a43ea2fd7d899ce6228d2b427daeaa39_720" src="https://github.com/user-attachments/assets/5e0025a7-d7af-4a52-bb17-450aca69995e" /><img width="873" height="1920" alt="0836360a99c46011e19aa9ad7a95ccc8_720" src="https://github.com/user-attachments/assets/40437611-0fc3-431c-833c-6f0fe9b5596a" /><img width="873" height="1920" alt="b75a800f6f3811b39c8455c07e7e9462_720" src="https://github.com/user-attachments/assets/58576401-f039-4386-aafa-918e38ea7741" />
<img width="873" height="1920" alt="8dfcb1161879288c9b0d688c72a2e9cf_720" src="https://github.com/user-attachments/assets/76bd1bae-d634-4300-841a-195a3e3e723e" /><img width="873" height="1920" alt="ea796c54e043acf48a12151da71cd256" src="https://github.com/user-attachments/assets/bdb28276-6ae5-4dc5-9203-b8c0eac03ab3" /><img width="873" height="1920" alt="607754e60357a5216ab7d711fe2644d3" src="https://github.com/user-attachments/assets/26eb77be-76a9-4770-947c-031bacf86433" />

## 演示视频


## 功能特性

### 行情

| 模块    | 说明                                                          |
| ----- | ----------------------------------------------------------- |
| 自选股列表 | 内置 10 只知名 A 股；支持**按代码 / 中文名称 / 拼音搜索添加**，本地持久化              |
| 分组管理  | 自定义分组（≤12 组，组名 ≤8 字），一股单归属；chips 切换分组，长按分组名可重命名 / 调整顺序 / 删除 |
| 多选批量  | 长按浮层进入多选：全选 / 取消全选，批量**删除 / 移动分组 / 置顶 / 取消置顶**              |
| 置顶管理  | 左滑呼出操作条（置顶切换 / 问 AI / 删除）；置顶项恒排在分组最前                    |
| 表头排序  | 点表头按 名称 / 最新价 / 涨跌幅 排序，再点一次切换升降序                            |
| 个股详情  | 报价区、分时走势图（面积填充）、K 线蜡烛图 + 成交量、行情详情表                          |
| 分时点选  | 点击分时图显示十字线与该时点价格气泡                                          |
| K 线交互  | 双指捏合缩放、单指水平平移、长按十字光标读数（锁定后单击清除）、右上角「＋标记」快捷记关键位              |
| K 线周期  | 日 K / 周 K / 月 K 一键切换（前复权）                                    |
| 关键位备忘 | 手动标记支撑 / 压力价位（单股 ≤12 个，同价同类型去重），与 AI 产出的关键位合并画参考线            |
| 行情时间  | 列表页与详情页展示**行情本身时间**（非本机时刻），并区分「缓存」与「本机刷新时刻」                  |
| 本地缓存  | 行情快照落盘，接口临时失效页面不空白；60 秒轮询刷新                                 |
| 超时兜底  | 详情加载 8 秒未就绪自动使用本地缓存，避免一直空白                                  |

> 涨红跌绿遵循 A 股配色惯例。

### 数据与备份

| 模块    | 说明                                                                   |
| ----- | -------------------------------------------------------------------- |
| 备份导出  | 一键把**自选与分组 / 关键位 / AI 会话**导出成文本并复制到剪贴板（直接对 SP 原始值做快照）                 |
| 备份恢复  | 粘贴备份文本即可还原（覆盖式，带二次确认）；恢复后自动重置到「全部」分组，界面与数据一致。不含 LLM Key 与行情缓存 |

### AI

| 模块      | 说明                                          |
| ------- | ------------------------------------------- |
| AI 分析解读 | 趋势判断 / 买卖参考点位 / 操作建议 / 风险提醒 / 行情总结          |
| AI 问答   | 多会话聊天（本地持久化），支持修改 / 删除 / 重新生成消息             |
| 快捷问句    | 输入框上方随上下文变化的快捷问句 chips，点一下即发送                |
| 消息操作    | 长按消息可复制 / 引用追问 / 重新生成 / 删除                   |
| 股票代码块   | 回答中以 \`\`\`stock 标记的代码块自动渲染为实时行情卡片          |
| 可配置 LLM | Base URL / API Key / 模型运行时输入，「连接获取模型」下拉点选   |
| 预设管理    | 保存后自动以预设名 / 模型名创建预设；一键切换、编辑（名称 / URL / Key / 模型）、删除 |
| 风险等级    | 详情页顶部小字色块展示风险（低 / 中 / 高），未配置 API 不显示        |
| 降级策略    | LLM 调用失败自动切换本地规则生成分析，演示链路不中断                |
| 生成中断    | 停止按钮 / 退出页面自动取消在途请求（请求序号门控，避免旧响应串台）         |
| 长回复折叠   | 分析结果超过 120 字时在会话里只显示节选，全文进独立的「结果详情」页         |

### 体验

| 模块   | 说明                                                    |
| ---- | ----------------------------------------------------- |
| 夜间模式 | 跟随系统 / 强制浅色 / 强制深色三态，菜单一键循环切换，深色偏好本地持久化               |
| 首启引导 | 首次启动询问是否观看 **11 步**功能引导（高亮挖孔 + 说明卡）；菜单「功能说明」可重看      |
| 就地提示 | 分组 / 排序 / K 线 / 消息操作四处的一次性轻提示，点「知道了」后不再出现              |
| 密钥安全 | API Key 密码态输入（掩码 + 眼睛切换）+ 输入框一键清空                     |
| 空态引导 | 无自选、分组为空时给出下一步该点哪里                                    |

## 技术栈

- **框架**：[Kuikly](https://github.com/Tencent-TDS/KuiklyUI)（Kuikly DSL）+ Kotlin Multiplatform
- **平台**：业务代码全部位于 `shared/commonMain`；Android 已真机验证，iOS / HarmonyOS 宿主已适配
- **行情数据**：腾讯免费行情接口（实时报价 / 分时 / K 线，前复权）+ 搜索联想接口
- **AI 服务**：OpenAI 兼容 `chat/completions` 协议，任意服务商
- **构建**：Gradle 8.5 · AGP 8.2.2 · Kotlin 2.1.21 · Kuikly 2.7.0
- **图表 / 表格**：自研 `chart`（LineChart / CandleStickChart / BarChart，含手势交互）与 `table`（KuiklyTable）模块

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

# 直接装到已连接设备
JAVA_HOME="C:\Program Files\Java\jdk-17" ./gradlew :androidApp:installDebug

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
- `initKuikly` 入口由 KSP 自动生成（无需手写），对应 C++ 侧 `api->kotlin.root.initKuikly()`
- 页面导航走框架 `RouterModule`，由 `ohosApp/.../adapter/RouterAdapter.ets` 接管；主题由 `ThemeController.ets` + `PersistentStorage` 实现

### 构建 iOS

iOS 产物**只能在 macOS 上编译**（Kotlin/Native 需要 Xcode 工具链）。有 Mac 时：

```bash
./gradlew :shared:generateDummyFramework   # pod install 前置，跳过会导致运行时崩溃
cd iosApp && pod install && open iosApp.xcworkspace
```

`generateDummyFramework` → `pod install` → `xcodebuild`（模拟器 Debug），
产物 `iosApp-simulator-debug`（`.app`）在 Actions 运行页的 Artifacts 下载。
当前状态：**编译已跑通**（共享层与 iOS 宿主代码均通过，模拟器包约 8.4 MB）。

宿主已适配的部分：

- `HRBridgeModule`：实现 `toast` / `currentTimestamp` / `dateFormatter` / `setThemeMode` / `closePage` / `copyToPasteboard`（iOS 侧 Module 无需注册，类名与共享层 `moduleName` 一致即可被运行时查找到）
- `ThemeController.swift`：三态主题持久化（UserDefaults）+ 通知触发页面重建；`KuiklyRenderViewController` 在 `p_mergeExtParamsWithOriditalParam` 中为每个页面注入 `isNightMode` / `themeMode`，**子页面（openPage 推入的）也会带上**
- `KRRouterHandler`：负责页面 push / pop；`Info.plist` 已补 `CFBundleDisplayName=AiStock` 与 ATS 放行（对齐 Android 的 cleartext 配置，AI Base URL 可能为非 HTTPS）

## 常用操作

### 自选股管理

- **添加**：顶栏「☰」→「添加自选股」，输入**代码 / 中文名称 / 拼音首字母**（如 `600519` / `茅台` / `mt`），在下方结果里点选即加入；弹窗不自动关闭，可连续添加多只
- **分组**：chips 行末尾「＋」新建分组；长按分组名可重命名 / 调整顺序 / 删除（删组后组内股票回到「未分组」，仍留在自选里）
- **移动**：长按任意股票行 → 选择目标分组
- **多选批量**：长按股票行 → 浮层底部「批量选择…」→ 勾选后底部出现 删除 / 移动分组 / 置顶 / 取消置顶
- **单条操作**：左滑股票行呼出操作条

### 查看与分析

1. 点自选列表任意股票进入详情页：报价 → 分时图 → K 线 → 行情表 → AI 分析
2. K 线上双指捏合缩放、单指水平拖动平移、长按进入十字光标读数（再单击清除）
3. K 线右上角「＋标记」把当前价记成关键位，或从标题行入口按最新价标记
4. 底部任务栏「AI 问答」进入多会话聊天；消息长按可复制 / 引用追问 / 重新生成 / 删除

### 配置 AI 分析

1. 顶栏「☰」→「AI 设置」
2. 填写 **Base URL**（OpenAI 兼容，如 `https://api.deepseek.com/v1/chat/completions`）与 **API Key**
3. 点击连接自动读取模型列表，或手动输入模型名
4. 保存后进入个股详情页会自动触发 AI 分析；也可点「开始 AI 分析」手动触发

**预设**：每次保存自动以预设名 / 模型名创建一个预设，可一键切换、编辑、删除。预设互斥启用，启用新预设会自动停用其他预设。

### 夜间模式

顶栏「☰」菜单中切换，顺序为 跟随系统 → 深色 → 浅色 → 跟随系统。
  
切换通过宿主 `AppCompatDelegate.setDefaultNightMode` 触发页面重建，配色由 `shared` 的 `ThemePalette` 语义 token 统一提供。

三端重建机制等价：Android 用 Activity recreate；iOS 用 `NotificationCenter` 通知 + SwiftUI `.id()` 重建；鸿蒙用 `AppStorage` 版本号 + `@Watch` 销毁重建 Kuikly 实例。三端都在创建页面时注入 `isNightMode` / `themeMode`（与 `BasePager` 读取的键名一致）。

## 工程结构

```
├── shared/                          # 跨端业务代码（核心）
│   └── src/commonMain/kotlin/com/example/aistock_prices/
│       ├── base/                    # BasePager、桥接模块、工具扩展
│       ├── stock/
│       │   ├── data/                # 数据与服务层（全部本地持久化都在这里）
│       │   │   ├── StockModels.kt        # 模型 + Watchlist（默认自选 / 分组 / 批量操作）
│       │   │   ├── StockRepository.kt    # 腾讯行情 + 分时 + K线 + 搜索联想接口
│       │   │   ├── StockCache.kt         # 行情快照缓存
│       │   │   ├── LevelStore.kt         # 关键位备忘（key_levels_<code>）
│       │   │   └── BackupStore.kt        # 备份导出 / 恢复（对 SP 原始值做快照）
│       │   ├── ui/                  # 主题色板（Theme.kt）、格式化与通用 UI
│       │   ├── ai/                  # AI 服务、问答视图、配置与预设管理
│       │   │   ├── AiAnalysisService.kt   # AI 分析（含本地规则降级）
│       │   │   ├── AiChatView.kt          # 多会话问答视图（消息操作菜单 / 快捷问句）
│       │   │   ├── AiChatPage.kt          # 问答页（退出自动中断生成）
│       │   │   ├── AiConfigView.kt        # LLM 配置表单
│       │   │   ├── AiConfigPage.kt        # 配置页
│       │   │   ├── PresetDetailPage.kt    # 预设新建/编辑
│       │   │   ├── ResultDetailPage.kt    # 分析结果详情
│       │   │   └── ConversationStore.kt   # 会话持久化
│       │   ├── StockListPage.kt     # 首页：自选列表 + 分组 + 多选 + 底部任务栏 + 首启引导
│       │   └── StockDetailPage.kt   # 详情：报价/分时/K线/关键位/AI/行情表
│       └── RouterPage.kt            # 框架路由示例
├── chart/                           # 图表组件（Line / CandleStick / Bar，自研渲染器 + 手势）
├── table/                           # 表格组件（KuiklyTable）
├── androidApp/                      # Android 宿主工程（已验证）
│   └── src/main/java/.../           # ThemeController（主题持久化与重建）、桥接模块、各类 Adapter
├── iosApp/                          # iOS 宿主（已适配业务）
│   └── iosApp/
│       ├── ThemeController.swift    # 主题三态 + UserDefaults + 重建通知
│       ├── ContentView.swift        # 入口：pageName = stock_list
│       └── KuiklyExpand/            # Modules（HRBridgeModule 桥接）、Handler（路由）、VC 封装
├── ohosApp/                         # 鸿蒙宿主（已适配业务）
│   └── entry/src/main/ets/
│       ├── pages/Index.ets          # 入口：默认页面 stock_list，监听主题版本重建
│       ├── entryability/            # EntryAbility（PersistentStorage 主题持久化）
│       └── kuikly/                  # ThemeController.ets、modules/KRBridgeModule.ets、各类 Adapter
├── settings.ohos.gradle.kts         # 鸿蒙构建配置集（含 chart / table）
├── build.ohos.gradle.kts            # 鸿蒙根构建脚本（Kotlin 2.0.21-KBA-010）
├── shared/build.ohos.gradle.kts     # 鸿蒙变体依赖（-2.0.21-ohos）
└── build_ohos.bat                   # 鸿蒙产物一键构建 + 拷贝
```

## 数据源与说明

- 行情：腾讯免费公开接口（实时报价 / 分时 / K 线 / 搜索联想），仅供学习演示，请勿用于商业用途或高频调用
- AI：需自备 OpenAI 兼容服务的 Key，应用内不内置任何密钥，配置仅保存在本机
- 备份文本包含自选 / 关键位 / AI 会话内容，**粘贴给别人等于把这些数据一起给出**，请自行留意
- 本项目为技术演示，所有分析与问答结果**不构成任何投资建议**

## 已知限制

- 行情为轮询拉取，非推送；非交易时段数据为上一交易日快照
- 搜索仅返回 **A 股**（沪深北），港美股与权证结果被过滤掉；「全选」只作用于**当前分组可见**的股票，不支持跨组全选
- 关键位单股上限 12 个；自定义分组上限 12 个（组名 ≤8 字）
- 备份恢复为**覆盖式**，会替换本机现有自选 / 关键位 / AI 会话（弹窗有二次确认）
