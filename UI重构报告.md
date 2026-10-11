# ApkVerTool UI 重构报告

> 目标：彻底摒弃现有 UI 实现（含 miuix / 自研玻璃底栏等），改用 **Compose Material 3** 重建界面；
> 清理全部 emoji；新布局尽量贴近旧观感；**功能实现一行不动**。
> 编制日期：2026-10-11 ｜ 基线版本：v2.0.3 (code 26) ｜ 设计规范来源：`D:\MaterialDesign\AGENTS.md`

---

## 0. 摘要

| 项 | 结论 |
| :--- | :--- |
| **可行性** | ✅ 可行。工程无 XML 布局、无 `R.*` 引用，UI 全部是"纯代码 Java View"，替换面清晰可控 |
| **推荐路线** | 保留 `core/` 引擎与手搓构建链（`build2.sh`），只把 **UI 层**整体重写为 Compose M3 |
| **要删除的外部 UI 依赖** | miuix-ui / miuix-blur / miuix-icons / miuix-core / miuix-shader / miuix-squircle（6 个 artifact）+ 自研 `LiquidGlassView` / `GlassShaders` |
| **要新增的依赖** | `androidx.compose.material3:material3-android:1.4.0`（+ `material-icons-core`，M3 本体不传递携带图标库） |
| **emoji 清理量** | 真实图形 emoji **约 37 处**（代码字符串）+ 4 处（更新日志正文） |
| **功能风险** | 低。业务方法（选文件 / 改写 / 体检 / 安装 / 分享 / 密钥 / 输出）全部原样保留，只换壳 |
| **改动规模** | 重写约 3 个 Activity 的视图层 ≈ 2000 行；`core/` 13 个文件、`cli/` 零改动 |
| **需你拍板** | **8 个决策点**（见 §7），其中 4 个直接影响观感差异 |

---

## 1. 现状勘查

### 1.1 UI 相关文件清单

| 文件 | 行数 | 性质 | 处置 |
| :--- | ---: | :--- | :--- |
| `src/com/seagull/avt/app/PagedActivity.java` | 1184 | **UI 外壳基类**：顶栏 + 自研分页器 + 底栏（三种形态）+ 一整套控件工厂（card/label/note/field/button/segmented…） | 🔴 整体废弃，重写为 Compose `AppScaffold` |
| `src/com/seagull/avt/app/MainActivity.java` | 1268 | 主页 4 页（改写 / 条目 / 体检 / 日志）+ 全部业务流程 | 🟡 拆：视图层重写，业务流程保留 |
| `src/com/seagull/avt/app/SettingsActivity.java` | 660 | 设置 4 页（外观 / 签名 / 输出 / 关于）+ 密钥与输出目录业务 | 🟡 同上 |
| `src/com/seagull/avt/app/ChangelogActivity.java` | 135 | 更新日志逐版本分页 | 🟡 视图重写 |
| `src/com/seagull/avt/app/SwipePager.java` | 246 | **自研 ViewPager**（横向拖动跟手 + 吸附 + 底栏触摸转发） | 🔴 由 Compose `HorizontalPager` 取代 |
| `src/com/seagull/avt/app/ApkProvider.java` | 83 | 文件 ContentProvider（安装/分享用） | ⚪ 与 UI 无关，不动 |
| `src/com/seagull/avt/bar/LiquidGlassView.java` | 409 | **自研液态玻璃底栏**（AGSL `RuntimeShader` 真折射） | 🔴 删除 |
| `src/com/seagull/avt/bar/GlassShaders.java` | 213 | 上面那位用的 AGSL 着色器源码 | 🔴 删除 |
| `src-kotlin/com/seagull/avt/bar/AvtBottomBar.kt` | 280 | **miuix Compose 底栏**（`NavigationBar` / 玻璃款） | 🔴 删除 |
| `src-kotlin/com/seagull/avt/bar/AvtBarBridge.kt` | 128 | Java↔Compose 桥 + emoji→miuix 图标映射 | 🔴 删除 |
| `src-kotlin/com/seagull/avt/bar/BitmapBackdrop.kt` | 64 | miuix `Backdrop` 实现 | 🔴 删除 |
| `src/com/seagull/avt/core/*.java` | 13 个 | APK 引擎（解析 / 改写 / 签名 / 对齐 / 打包） | ✅ **完全不动** |
| `src/com/seagull/avt/cli/AvtCli.java` | — | 桌面 CLI（`info/verify/selftest`，27 项断言） | ✅ **完全不动**（重构后的回归验证器） |

### 1.2 UI 技术现状（关键事实）

1. **没有任何 XML 布局**。全工程唯一的 XML 是 `AndroidManifest.xml`；`res/` 目录下只有一个由 `mkicon.py` 现场生成的 `mipmap/ic_launcher.png`。
2. **没有任何 `R.*` 引用**（`R.layout` / `R.id` / `R.string` … 全为零）。当前界面 100% 由 Java 代码 `new LinearLayout(...)` 这样搭出来。
3. 控件的"皮肤"是**代码画的 `GradientDrawable`**（圆角、描边、渐变），颜色是 `initTheme()` 里**硬编码的 int**（浅色 / 深色各一套，蓝紫 indigo 系）。
4. 页面切换是**自研 `SwipePager`**（`ViewGroup` + `Scroller` + `VelocityTracker`），底栏点击 → `pager.setCurrentItem()`。
5. 底部导航**有三套实现并存**：
   - `BAR_MIUIX`（默认）：手写模仿 miuix `NavigationBar.kt` 的规格（64dp 行高 + 顶部 1dp 分割线 + 26dp 图标 + 角标）；
   - `BAR_ICON`（"悬浮底栏"）：真正的 miuix Compose + 自研液态玻璃；
   - `BAR_CHIP` / `BAR_NONE`：文字胶囊 / 无底栏（更新日志用）。
6. 主题切换靠 `attachBaseContext()` 改 `Configuration.uiMode` + `recreate()`——**这套机制可以原样保留**，Compose 侧读 `isSystemInDarkTheme()` 即可。

### 1.3 要摒弃的"UI 资源引用"清单

**（A）源码级引用点（共 7 处）**

| # | 位置 | 引用内容 |
| :--- | :--- | :--- |
| 1 | `PagedActivity.java:40-44` | `import com.seagull.avt.bar.{AvtBarFactory, AvtBarHandle, BarCallback, GlassSink, LiquidGlassView}` |
| 2 | `PagedActivity.java:38` | `import androidx.compose.ui.platform.ComposeView`（为承载 miuix 底栏） |
| 3 | `PagedActivity.java:657-731` | `buildComposeBar()` + `captureBackdrop()`：miuix 底栏接线与 backdrop 位图喂给 miuix |
| 4 | `PagedActivity.java:746-811` | `buildMiuixBar()` / `setBarBadge()` / `setBarDot()`：手写复刻 miuix NavigationBar 规格 |
| 5 | `src-kotlin/.../bar/*.kt` | `top.yukonga.miuix.kmp.*`（NavigationBar / MiuixIcons / MiuixTheme / ThemeController / miuix-blur） |
| 6 | `SettingsActivity.java:359-382` | 「开源引用」卡片里的 Miuix 条目与 GitHub 链接 |
| 7 | `SettingsActivity.java:138-168` | 「底栏样式（Miuix 标准底栏 / 悬浮底栏）」+「底栏显示模式（3 档）」两组设置 |

**（B）依赖级（`deps_fetch3.py` 的 `ROOTS`）**

```
top.yukonga.miuix.kmp:miuix-ui-android:0.9.4
top.yukonga.miuix.kmp:miuix-blur-android:0.9.4
top.yukonga.miuix.kmp:miuix-icons-android:0.9.4
androidx.activity:activity-compose:1.13.0        ← 保留（Compose 宿主必需）
```
> 实测：miuix 依赖树连带拖进了 `miuix-core` / `miuix-shader` / `miuix-squircle` 与 `com.materialkolor:material-color-utilities` 等，共 **84 个 artifact / 23.1 MB**。剔除 miuix 后闭包会明显变小。

**（C）构建级（`build2.sh`）**

- `[3b/8]` 编 `src/com/seagull/avt/bar/*.java` → `build/bar.jar`
- `[4/8]` 用 kotlinc + Compose 编译器插件编 `src-kotlin/com/seagull/avt/bar/*.kt` → `build/ktclasses`
- `[6/8]` D8 把 `build/bar.jar` / `build/kt.jar` 一起打进 dex
> 这三步**不会取消**（重写后仍要编 Compose Kotlin），只是**输入源码换名换目录**。

**（D）仅供参照、不参与编译**

- `reference/miuix/`（4 个文件：miuix 的 `NavigationBar.kt` 源码 + 中文文档）——重写后失去参照价值，建议随源码一并删除或归档。

---

## 2. emoji 全量清单（对应你的第 3 条要求）

全工程非 ASCII 符号共 43 种，按性质分三档：

### T1 · 真实图形 emoji（共 ~37 处代码 + 4 处日志正文）

| emoji | 出现处 | 语义 |
| :--- | :--- | :--- |
| 📦 | 底栏"条目"页、折叠组"版本号"、日志正文 | 包 / 条目 |
| 📱 | 折叠组"安卓SDK版本"、日志正文 | SDK |
| 🏷 | 折叠组"应用标识"、日志正文 | 标签 |
| 🩺 | 底栏"体检"页 | 体检 |
| 📜 | 底栏"日志"、更新日志页标题、设置页按钮、日志正文 | 日志 |
| 🎨 | 底栏"外观"、设置页 | 外观 |
| 🔑 | 底栏"签名"、设置页按钮 | 密钥 |
| 📁 / 📂 | 底栏"输出"、按钮"选择文件…"/"选择文件夹"/"导入密钥" | 文件夹 |
| ℹ️ | 底栏"关于" | 信息 |
| 📤 / 📥 | 按钮"分享"/"安装" | 导出 / 导入 |
| 🚀 ✏️ 💡 ➕ 🗑 📋 📐 🔏 🧹 ✎ 💾 ⭐ ⚙️ | 各按钮与卡片标题 | 见 §3.6 逐项映射 |

### T2 · 状态 / 装饰符号（是否算 emoji 需你定，见 §7-Q5）

`✓` `✗`（安装结果）、`●`（状态前缀）、`▾` `▴`（折叠三角）、`↺`（恢复默认）、`↔`（注释）

### T3 · 纯排版符号（**建议保留**，一般不视为 emoji）

`→`（文案箭头）、`①` `②`（步骤序号）、`·` `•`（列表点）、`—` `…` `≈` `≥`
`─` `═`（**日志分隔线**，共 64 处，属日志排版而非界面装饰）

> ⚠️ 更新日志正文里也夹着 emoji（`📦 📱 🏷 ▾ ▴`，共 4 处）——这些来自 `CHANGELOG.md`，
> 由 `version.py` 自动生成到 `AvtChangelog.java`。要清就得改 `CHANGELOG.md` 并重新生成。

---

## 3. 可行性结论与依据

### 3.1 为什么可行

1. **替换面封闭**：没有 XML 布局、没有 `R.*`、没有第三方 View 库（M3 Views / AppCompat / Material Components 都没引）。所有 UI 都收在 4 个 Activity + PagedActivity 基类里，边界非常干净。
2. **依赖可得**：`androidx.compose.material3:material3-android:1.4.0`（稳定版）与 `compose-bom:2026.09.00` 在阿里云镜像上均实测可达，与 `AGENTS.md` 的版本基线一致。
3. **Compose 运行时已在位**：现有闭包里已有 `androidx.compose.runtime / ui / foundation / animation`（1.12.0）与 `activity-compose`，M3 只是在其上加一层。
4. **构建链已验证可跑**：v2.0.3 已用 `sh build2.sh fast` 完整出货（7.80 MB，条目与出货包零差异），说明 kotlinc + Compose 编译器插件 + D8 这条路是通的——重写只是换源码，不换管线。
5. **有回归验证器**：`AvtCli selftest`（27 项）+ `info` / `verify` 可对重构前后的 APK 做逐项比对，功能是否被误伤一跑便知。

### 3.2 与 `AGENTS.md` 的硬约束对齐

| AGENTS.md 约束 | 本方案 |
| :--- | :--- |
| ❌ 用 M2（`androidx.compose.material`） | 只用 `material3` |
| ❌ 新建 XML 布局 / 用 MDC-Android | 纯 Compose，零 XML |
| ❌ 在 `Modifier` 里手画阴影模拟海拔 | 用 `surfaceContainer*` + `tonalElevation` |
| ❌ 混用 Miuix | miuix 全部删除 |
| ✅ 颜色只用 `MaterialTheme.colorScheme.*` | 见 §3.4 |
| ✅ `Scaffold.innerPadding` 必须消费 | 全部走 Scaffold |
| ✅ `enableEdgeToEdge()` | 已在用，保留 |
| ✅ 版本走单一版本源 | `deps_fetch3.py` 的 `ROOTS` 作为唯一版本真源 |

### 3.3 风险评级

| 风险项 | 等级 | 说明与对策 |
| :--- | :---: | :--- |
| 功能被误伤 | 🟢 低 | 业务方法整段保留，只改调用入口；有 `selftest` 回归 |
| 观感与原版不一致 | 🟡 中 | M3 的圆角 / 字号 / 间距与旧硬编码值不同，需按 §3.5 贴齐 |
| 构建链适配 | 🟡 中 | 需要改 `deps_fetch3.py` + `build2.sh`；M3 需显式补 `material-icons-core` |
| 包体变大 | 🟡 中 | M3 + 图标库会增体积（预计 +1～3 MB）；可按需裁剪图标 |
| 真机验证缺失 | 🔴 高 | 本机 **无 Android 设备、无模拟器镜像**，只能静态验证 + 你手上真机实测 |
| Compose 版本冲突 | 🟢 低 | 解析器"高版本优先"，M3 1.4.0 要 foundation 1.8.1，现有闭包是 1.12.0，取高即兼容 |

---

## 4. 方案设计

### 4.1 目标架构（分层）

```
┌─────────────────────────────────────────────────────────────┐
│  Compose UI 层（新增，Kotlin）                                │
│    theme/  AvtTheme.kt  Color.kt  Type.kt  Shape.kt          │
│    ui/     AppScaffold.kt（顶栏 + 底栏 + HorizontalPager）      │
│            MainScreen / SettingsScreen / ChangelogScreen      │
│            components/  FoldGroup / InfoCard / LogPane / …    │
├─────────────────────────────────────────────────────────────┤
│  UI 状态层（新增，Kotlin，可 @Stable 状态持有者）                │
│    MainUiState    —— 输入框值 / 队列 / 待替换表 / 日志行         │
│    SettingsUiState —— 各开关 / 密钥状态 / 输出路径               │
│    （以 mutableStateOf / mutableStateListOf 暴露给 Compose）    │
├─────────────────────────────────────────────────────────────┤
│  业务层（保留，Java，Activity 内）                              │
│    选文件 / 入队 / 折叠判定 / startRun / doRun / processOne      │
│    体检 / 验签 / 安装（root）/ 分享 / 密钥 / 输出目录             │
│    —— 方法体不动，只把「读控件 / 写控件」换成「读状态 / 写状态」      │
├─────────────────────────────────────────────────────────────┤
│  引擎层（完全不动，Java）                                      │
│    core/  ApkPatcher · ApkInfo · AxmlPatch · SignUtil ·       │
│           SplitBundle · ZipAlignWriter · …（13 个文件）        │
└─────────────────────────────────────────────────────────────┘
```

**关键点：Activity 依然是"逻辑宿主"。** 它继续持有 `queue` / `putMap` / `busy` / `lastOutput` 等业务字段，
继续跑 `new Thread(...)` 做重活；Compose 侧只负责"显示状态"和"把用户操作转成方法调用"。
这样"不动功能实现"是**字面意义上成立**的——业务方法一行不改。

### 4.2 屏幕与导航映射

| 旧结构 | 新结构（M3 + Compose） |
| :--- | :--- |
| `SwipePager`（横向拖动切页） | `androidx.compose.foundation.pager.HorizontalPager` |
| 顶栏（`LinearLayout` + 标题 `TextView` + 右侧文字按钮） | `Scaffold(topBar = TopAppBar(...))`；右侧文字按钮 → `TextButton`（`完成`）/ `IconButton`（`设置`） |
| 底栏 `NavigationBar`（miuix 手写 / 玻璃） | M3 `NavigationBar` + `NavigationBarItem` |
| 底栏数字角标 / 小红点 | `BadgedBox` + `Badge` |
| 底栏图标 emoji | M3 `Icons.*`（矢量） |
| 无底栏页（更新日志） | 同一 `Scaffold`，`bottomBar = {}`，顶栏标题右侧显示 `n/N` |
| 主题切换 `uiMode` + `recreate()` | 保留原机制；Compose 读 `isSystemInDarkTheme()` + 本地 `theme_mode` |

**主页 4 页 / 设置 4 页 / 更新日志 N 页** 全部映射为 `HorizontalPager` 的页，底栏项数量与顺序不变：

- 主页：`改写(✏️) · 条目(📦) · 体检(🩺) · 日志(📜)` → `Icons.Edit / Icons.FolderZip(或 Inventory2) / Icons.MonitorHeart(或 HealthAndSafety) / Icons.ReceiptLong`
- 设置：`外观(🎨) · 签名(🔑) · 输出(📁) · 关于(ℹ️)` → `Icons.Palette / Icons.Key / Icons.FolderOpen / Icons.Info`
- 角标：改写页 = 已选文件数，条目页 = 待替换条目数（逻辑不变，`setBarBadge(0/1, n)` → 状态驱动）

### 4.3 控件替换对照表（你的第 2 条要求）

| # | 旧控件（`PagedActivity` 工厂方法） | 用途 | **M3 目标组件** | 备注 |
| :-- | :--- | :--- | :--- | :--- |
| 1 | `card()` / `card(bg)` | 圆角 16dp 卡片 | `Card` / `OutlinedCard` / `ElevatedCard`（`shape = shapes.medium` 12dp） | 取 `surfaceContainer` 底色 |
| 2 | 页面根 `ScrollView` | 纵向滚动 | `Column(Modifier.verticalScroll(...))` 或 `LazyColumn` | 长列表优先 `LazyColumn` |
| 3 | `label()` 12.5sp 粗体次色 | 卡片小标题 | `Text(style = MaterialTheme.typography.labelLarge, color = onSurfaceVariant)` | |
| 4 | `note()` 12.5sp 说明 | 辅助说明 | `Text(bodySmall, onSurfaceVariant)`；表单内可作 `Text`/`supportingText` | |
| 5 | `field()` / `fieldNum()` | 单行输入 | `OutlinedTextField(label/placeholder, singleLine)`；数字 → `keyboardOptions = KeyboardType.Number` | ⚠️ M3 无"hint"，用 `placeholder` |
| 6 | `etDrop` 多行输入 | 批量条目名 | `OutlinedTextField(minLines = 3, maxLines = 8)` | |
| 7 | `glassButton()` 玻璃主按钮 | 主操作（选择文件 / 开始修改 / 打开更新日志） | `Button`（填充）+ `Icon` | 玻璃质感属"旧 UI 资源"，移除 |
| 8 | `bigButton(s, true)` 强调 | 多数主按钮 | `Button` 或 `FilledTonalButton` | |
| 9 | `bigButton(s, false)` 次要 | 取消 / 恢复 / 清空 | `OutlinedButton` 或 `FilledTonalButton` | M3 强调度分级见 AGENTS.md §3.4 |
| 10 | 带 emoji 的 `Button` | 图标 + 文字按钮 | `Button { Icon(...); Spacer; Text(...) }` | 图标尺寸用 `ButtonDefaults.IconSize` |
| 11 | 顶栏右侧 `textButton()` | 完成 / 设置 | `TextButton` / `IconButton` | |
| 12 | `segmented()` 自绘胶囊分段 | 枚举选项（底栏样式 / 显示模式） | `SingleChoiceSegmentedButtonRow` + `SegmentedButton` | ⚠️ 这两个设置项本身要重定（§7-Q2） |
| 13 | `Switch` | 开关（主题 / 重签名 / 签名方案 ×4） | `Switch`（+ `thumbContent` 打勾） | 一对一 |
| 14 | `divider()` | 分隔线 | `HorizontalDivider(color = outlineVariant)` | |
| 15 | `kv()` 键值对 | 关于页信息行 | `ListItem(headlineContent, supportingContent)` 或 `Column(Text, Text)` | |
| 16 | `link()` 外链行 | GitHub 链接 | `TextButton` 或 `Text(primary, onClick)` | |
| 17 | 折叠分组标题行 | 可点开合 | `Surface(onClick, shape = small)` + `Icon(ExpandMore/ExpandLess, 旋转动画)` + `AnimatedVisibility` | ⚠️ 折叠语义（收起=整组不参与改写）**必须原样保留** |
| 18 | 日志 `TextView` + `ScrollingMovementMethod` | 执行日志 | `LazyColumn` + `Text(fontFamily = FontFamily.Monospace)`，或 `Text` + `verticalScroll` + 自动滚底 | 日志追加由 `log()` 改为写 `mutableStateListOf` |
| 19 | 系统 `AlertDialog` | 条目名输入 / root 安装确认 / 结果提示 | M3 `AlertDialog`（icon + title + text + confirm/dismiss） | ⚠️ 规范：取消左、确定右；危险操作 confirm 用 `error` |
| 20 | `Toast` | 轻提示 | 保留 `Toast`，或改 `Snackbar`（`SnackbarHost`） | 见 §7-Q6 |
| 21 | 更新日志「当前版本」标签（`GradientDrawable` 圆角胶囊） | 版本标记 | `AssistChip` 或 `Surface(primaryContainer, shape = extraSmall)` | |
| 22 | 更新日志列表 `•` + 文本 | 变更条目 | `ListItem` 或 `Row(Icon(FiberManualRecord, 4dp), Text)` | |
| 23 | `SwipePager` + 底栏转发触摸 | 翻页 | `HorizontalPager`（自带手势与吸附） | 自研 `SwipePager` 整个删除 |
| 24 | `LiquidGlassView` 液态玻璃底栏 | 悬浮底栏样式 | **移除该项设置**，或退化为 `NavigationBar(containerColor = surfaceContainer.copy(alpha))` | 见 §7-Q3 |
| 25 | `GradientDrawable` 代码画背景 | 所有卡片 / 输入框 / 按钮底色 | `Surface` / `Card` 的 `color` + `shape` + `border` | 不再手画 |
| 26 | `applyBackgroundBlur()` 实时模糊 | 玻璃按钮背景模糊 | 移除 | 属玻璃体系残留 |

### 4.4 主题 / 设计 token 映射

旧代码在 `initTheme()` 里硬编码 16 个色值，逐一有 M3 语义槽位对应：

| 旧字段 | 浅色值（示例） | **M3 colorScheme 槽位** |
| :--- | :--- | :--- |
| `cBg` | `#F2F4F8` | `background` / `surface` |
| `cCard` | `#FFFFFF` | `surfaceContainer` |
| `cInk` | `#111827` | `onSurface` |
| `cSub` | `#6B7280` | `onSurfaceVariant` |
| `cLine` | `#E5E7EB` | `outlineVariant` |
| `cAccent` | `#4F46E5` | `primary`（`onPrimary = #FFF`） |
| `cFieldBg` | `#F9FAFB` | `surfaceContainerHighest` |
| `cFieldStroke` | `#E5E7EB` | `outline` |
| `cHint` | `#9CA3AF` | `onSurfaceVariant`（降透明） |
| `cLogCard` | `#F8FAFC` | `surfaceContainerLowest` |
| `cLogText` | `#334155` | `onSurfaceVariant`（或 `tertiary` 作代码色） |
| `cBtnSecondaryBg` | `#EEF2FF` | `secondaryContainer` |
| `cBtnSecondaryStroke` | `#C7D2FE` | `outline` |
| `cError` | `#DC2626` | `error` |
| `night` | 深色那套 12 个值 | `darkColorScheme(...)` 同槽位 |

字号：旧代码散落 `12.5f / 13f / 14f / 15f / 19f / 22f` → 收敛到 `typography` 的
`labelLarge(14) / bodyMedium(14) / bodySmall(12) / titleLarge(22)`。
圆角：旧 `dp(16)` / `dp(12)` / `dp(10)` → `shapes.medium(12)` / `large(16)` / `small(8)`。

> ⚠️ **配色来源需你二选一**（§7-Q4）：
> **(a) 固定主题**——用 `#4F46E5` 作种子色，按 Material Theme Builder 规则生成全套 tonal palette，
> 观感最接近现状（推荐先走这条，符合"尽量保持一致"）；
> **(b) 动态取色**——`dynamicLightColorScheme(context)`（API 31+），跟随壁纸，最"正统 M3"但**观感会变**。

### 4.5 "新布局尽量与旧一致"的落地策略

1. **骨架不变**：顶栏 + 内容 + 底栏三段式，页面顺序、底栏项名称与顺序、角标含义全部保持。
2. **信息层级不变**：每页的卡片顺序、卡片标题与正文、说明文字逐字保留（只去掉 emoji）。
3. **交互不变**：左右滑动切页保留（`HorizontalPager`）、折叠分组的开合语义保留、长按类交互无变化。
4. **尺寸就近取值**：
   - 卡片圆角 16dp → `shapes.large`(16dp) 或 `medium`(12dp)，**建议取 `medium`**（M3 惯例）；
   - 卡片间距 `dp(6)` 上下 → `Arrangement.spacedBy(8.dp)`；
   - 卡片内边距 `dp(13)` → `16.dp`；
   - 页面左右内边距 `dp(12)` → `16.dp`。
   > 这几处会带来 1～4dp 的观感差异，属"对齐 M3 规范"的必然代价，列出来供你知情。
5. **底栏尺寸**：旧 64dp 行高 → M3 `NavigationBar` 默认 80dp（含 indicator）。**这是最大的一处观感差异**，若要压到 64dp 需自定义 `NavigationBarItem` 高度（§7-Q1 一并定）。

### 4.6 emoji 处理策略（对应你的第 3 条）

| 档 | 处理方式 |
| :--- | :--- |
| **T1 图形 emoji** | **全部删除**。有语义的改为 M3 矢量图标（见 §4.2 / §4.7），无必要的直接去掉 |
| **T2 状态符号** | 按功能替换：`✓` → `Icons.Check`，`✗` → `Icons.Close`（或仅用颜色区分）；`●` → 小圆点 `Box`；`▾▴` → `Icons.ExpandMore/ExpandLess` + 旋转动画；`↺` → `Icons.Refresh` |
| **T3 排版符号** | **建议保留**（`① ② · → ─ ═`）；日志分隔线 `────` `════` 属引擎输出格式，改动会影响日志可读性 |
| **更新日志正文** | 需改 `CHANGELOG.md` → 重跑 `version.py genjava` 重新生成 `AvtChangelog.java` |

### 4.7 emoji → M3 图标映射建议

| 旧 emoji | 出现位置 | M3 图标 |
| :--- | :--- | :--- |
| ✏️ | 底栏「改写」 | `Icons.Edit` |
| 📦 | 底栏「条目」、折叠组「版本号」 | `Icons.Inventory2` / `Icons.FolderZip` |
| 🩺 | 底栏「体检」 | `Icons.HealthAndSafety` / `Icons.MonitorHeart` |
| 📜 | 底栏「日志」、更新日志标题 | `Icons.ReceiptLong` |
| 🎨 | 底栏「外观」 | `Icons.Palette` |
| 🔑 | 底栏「签名」、按钮 | `Icons.Key` / `Icons.VpnKey` |
| 📁 / 📂 | 底栏「输出」、选择文件 / 文件夹 | `Icons.FolderOpen` / `Icons.FileOpen` |
| ℹ️ | 底栏「关于」 | `Icons.Info` |
| 📱 | 折叠组「安卓SDK版本」 | `Icons.PhoneAndroid` |
| 🏷 | 折叠组「应用标识」 | `Icons.Label` / `Icons.Badge` |
| 📥 / 📤 | 按钮「安装」/「分享」 | `Icons.Download` / `Icons.Share` |
| 🚀 | 按钮「开始修改 →」 | `Icons.PlayArrow`（文字去掉箭头） |
| ➕ / 🗑 | 添加替换条目 / 清空列表 | `Icons.Add` / `Icons.DeleteOutline` |
| 🧹 | 清空日志 | `Icons.DeleteSweep` / `Icons.CleaningServices` |
| 💾 | 保存密码 | `Icons.Save` |
| 📋 / 📐 / 🔏 | 体检报告 / 对齐体检 / 验签详情 | `Icons.Article` / `Icons.Straighten` / `Icons.VerifiedUser` |
| 💡 / ⭐ / ⚙️ / ✎ / 💡 | 提示卡片 / GitHub / 设置 | `Icons.Lightbulb` / `Icons.Star` / `Icons.Settings` / `Icons.EditNote` |

> 图标来源：`material-icons-core`（基础集，体积小）或 `material-icons-extended`（1.7.8，全量但体积大）。
> **建议**：先用 core，缺哪个再从 extended 补；`AGENTS.md` 已提示 extended 不受 BOM 管理且体积大。

### 4.8 "不动功能"的边界（对应你的第 5 条）

**绝对不动：**
- `src/com/seagull/avt/core/**.java`（13 个文件，全部引擎）
- `src/com/seagull/avt/cli/AvtCli.java`
- `src/com/seagull/avt/app/ApkProvider.java`
- `version.py` / `mkres.py` / `merge_jars.py` / `mkicon.py` / `pack2.py` / `extract_jni.py` 的**业务逻辑**
- `AndroidManifest.xml` 的组件声明与权限
- 所有 `SharedPreferences` 键名（`theme_mode` / `resign` / `scheme_v1..v4` / `use_custom` / `key_pass` / `output_uri` / `bar_mode` / `bar_style`）
  > 保留键名 = 老用户升级后设置不丢，这点很重要。

**只换壳的部分：**
- 输入的读取：`etName.getText().toString()` → `uiState.versionName`
- 状态的回填：`etName.setText(v)` → `uiState.versionName = v`
- 日志：`tvLog.setText(tvLog.getText() + s)` → `logs.add(s)`
- 按钮使能：`btnInstall.setEnabled(x)` → `uiState.canInstall = x`
- 折叠状态：`foldOpenState` map → `uiState.foldOpen`（`SnapshotStateMap`）

**必须逐字保留的业务语义（易踩坑，重构时重点核对）：**
1. 折叠分组「收起 = 整组字段不参与改写」；
2. 「输出到指定目录成功 → 删掉私有目录中转副本」；
3. 同名 SAF 文档「先删再写」；
4. root 安装的成功判定（`Success` 或 `(exit=0)` 且不含 `Failure/Exception`）；
5. 不重签产物「保留原签名数据」（MT 式）；
6. SAF 授权位补齐 + 目录可写探针。

---

## 5. 构建链改造方案

### 5.1 依赖替换（`deps_fetch3.py`）

```python
# 改后（去掉全部 miuix，补 material3 与图标库）
ROOTS = [
    ("androidx.compose.material3", "material3-android", "1.4.0"),
    ("androidx.compose.material",  "material-icons-core-android", "1.7.8"),
    ("androidx.activity",          "activity-compose", "1.13.0"),
]
```

> ⚠️ **必须显式加 `material-icons-core`**。已核查 `material3-android:1.4.0` 的 POM：
> 它依赖 foundation 1.8.1 / ui 1.8.2 / runtime 1.9.0 / material-ripple / animation-core 等，
> **但不包含 `material-icons-core`**——`Icons.Default.*` 会编译不过。
> 另外现有闭包里已有 `androidx.compose.ui:ui-android:1.12.0` 等更高版本，解析器取高版本，不会冲突。

### 5.2 源码目录调整（`build2.sh`）

| 步骤 | 改动 |
| :--- | :--- |
| `[3b/8]` javac 编 `src/com/seagull/avt/bar/*.java` | **整段删除**（`bar/` 目录下的玻璃实现不再需要） |
| `[4/8]` kotlinc 编 `src-kotlin/com/seagull/avt/bar/*.kt` | 改为 `src-kotlin/com/seagull/avt/ui/**/*.kt` + `theme/**/*.kt` |
| `[5/8]` javac 编 App | classpath 去 `build/barclasses`；`-cp` 的 `build/ktclasses` 保留 |
| `[6/8]` D8 | 去掉 `build/bar.jar` |
| `[2/8] merge_jars` / `[3/8] mkres` / `[8/8] pack2` | **不变** |

### 5.3 备选：迁移到 Gradle（不推荐，仅列出）

按 `AGENTS.md` 模板建 `settings.gradle.kts` + `app/build.gradle.kts` + `libs.versions.toml`，
用 AGP 8.13.2 / Gradle 8.14.2 构建。

- 优点：标准工程，IDE 友好，Compose/资源链由 AGP 托管。
- 缺点：**等于放弃工程的核心资产**——`deps_fetch3.py`（POM 手工建图）、`mkres.py`（手搓 aapt2 资源链）、
  `pack2.py`（自定义组包/对齐/签名）、`AvtCli selftest` 与自定义打包流程都要重新接进 Gradle；
  且首次 Gradle 构建要下 AGP 全家（时间成本高）。
- **结论：本次重构不引入 Gradle**，继续用手搓链，风险最小。

---

## 6. 改动清单（文件级）

### 6.1 删除

```
src/com/seagull/avt/bar/LiquidGlassView.java
src/com/seagull/avt/bar/GlassShaders.java
src-kotlin/com/seagull/avt/bar/AvtBottomBar.kt
src-kotlin/com/seagull/avt/bar/AvtBarBridge.kt
src-kotlin/com/seagull/avt/bar/BitmapBackdrop.kt
src/com/seagull/avt/app/SwipePager.java
reference/miuix/                       （4 个参考文件，失去意义）
aar/ aar-res/ aar-manifest/ pom-cache/ （换依赖源后需重新解析）
```

### 6.2 新增（Kotlin）

```
src-kotlin/com/seagull/avt/theme/AvtTheme.kt      主题入口（固定色 / 动态色）
src-kotlin/com/seagull/avt/theme/Color.kt         调色板（由种子色生成的 tonal palette）
src-kotlin/com/seagull/avt/theme/Type.kt          字阶覆盖（如需要）
src-kotlin/com/seagull/avt/theme/Shape.kt         圆角（如需要）
src-kotlin/com/seagull/avt/ui/AppScaffold.kt      顶栏 + NavigationBar + HorizontalPager
src-kotlin/com/seagull/avt/ui/MainScreen.kt       主页 4 页
src-kotlin/com/seagull/avt/ui/SettingsScreen.kt   设置 4 页
src-kotlin/com/seagull/avt/ui/ChangelogScreen.kt  更新日志 N 页
src-kotlin/com/seagull/avt/ui/state/MainUiState.kt
src-kotlin/com/seagull/avt/ui/state/SettingsUiState.kt
src-kotlin/com/seagull/avt/ui/components/*.kt      FoldGroup / InfoCard / LogPane / SegmentedField …
```

### 6.3 修改

| 文件 | 改动内容 |
| :--- | :--- |
| `src/com/seagull/avt/app/PagedActivity.java` | 删除全部视图代码（1184 行 → 归零或仅保留极薄的公共常量）；改为 Compose 宿主 |
| `src/com/seagull/avt/app/MainActivity.java` | 保留业务方法；`onCreate` 改 `setContent {}`；视图字段 → 状态 |
| `src/com/seagull/avt/app/SettingsActivity.java` | 同上；顺带移除「开源引用」里的 Miuix 条目与底栏相关设置 |
| `src/com/seagull/avt/app/ChangelogActivity.java` | 改为 Compose 分页 |
| `deps_fetch3.py` | `ROOTS` 换依赖（§5.1） |
| `build2.sh` | 编 Kotlin 的源目录换路径；去掉 bar 包相关步骤（§5.2） |
| `CHANGELOG.md` | 清 emoji（可选，见 §7-Q5b） |
| `version.py` | 仅当其生成的 changelog 需去 emoji 时才动（一般不必改代码） |

### 6.4 明确不动

```
src/com/seagull/avt/core/**            （13 个文件，引擎）
src/com/seagull/avt/cli/AvtCli.java
src/com/seagull/avt/app/ApkProvider.java
mkres.py / merge_jars.py / mkicon.py / pack2.py / extract_jni.py
AndroidManifest.xml（组件与权限声明）
所有 SharedPreferences 键名
```

---

## 7. 待你确认的决策点（看完报告后微调）

| # | 问题 | 选项 | 我的建议 |
| :-- | :--- | :--- | :--- |
| **Q1** | **导航形态**：是否保留"左右滑动切页"？M3 `NavigationBar` 默认 80dp 高，旧底栏是 64dp | (a) 保留滑动 + `HorizontalPager`；(b) 改成标准单页 + 底栏切换（无横滑）；(c) 保留滑动但底栏压到 64dp | **(a)**，观感最接近；底栏高度差异若要抹平，需自定义 item 高度 |
| **Q2** | **底栏两个设置**：「底栏样式（Miuix / 悬浮）」「显示模式（图标+文字 / 仅图标 / 选中显文字）」怎么办 | (a) 整组删除；(b) 保留显示模式（映射 `alwaysShowLabel`，但"选中显文字" M3 无原生支持）；(c) 换为 M3 的底栏高度/标签两项新设置 | **(a) 删除整组**——这两项本就是 miuix 体系的产物，删掉最干净 |
| **Q3** | **液态玻璃底栏**（`LiquidGlassView`，属自研非 miuix）是否一并删除 | (a) 删；(b) 保留为可选样式 | **(a) 删**——它只服务于"悬浮底栏"，删了设置就彻底 |
| **Q4** | **配色**：固定蓝紫主题 vs Material You 动态取色 | (a) 固定（由 `#4F46E5` 生成 tonal palette）；(b) 动态取色（API 31+，观感会变）；(c) 默认固定 + 设置里给动态开关 | **(a)** 先固定，符合"尽量保持一致" |
| **Q5a** | **emoji 清理范围** | (a) 只清 T1 图形 emoji；(b) T1 + T2 状态符号；(c) T1 + T2 + T3 全部 | **(b)**——`✓✗●▾▴` 这类确实"显得杂"，且都能换成 M3 图标 |
| **Q5b** | **更新日志正文里的 emoji**（来自 `CHANGELOG.md`）要不要一起清 | (a) 清（改 `CHANGELOG.md` 并重新生成）；(b) 保留（属历史文本） | **(b) 保留**，历史记录一般不做二次加工 |
| **Q6** | **`Toast` → `Snackbar`？** | (a) 保留 Toast；(b) 全改 Snackbar；(c) 重要结果用 Snackbar，轻提示保留 Toast | **(c)** |
| **Q7** | **构建链**：继续手搓 `build2.sh` 还是迁 Gradle | (a) 手搓（改依赖源即可）；(b) Gradle（要重新接自研打包链） | **(a)** |
| **Q8** | **分隔线风格**：日志里的 `──── 体检报告 ────` / `════ 完成 ════` 是否保留 | (a) 保留（引擎输出格式）；(b) 改为 M3 标题样式 | **(a) 保留**——属日志内容而非界面装饰 |

---

## 8. 验证方案

| 关卡 | 手段 | 判据 |
| :--- | :--- | :--- |
| **编译** | `sh build2.sh fast` | `EXIT=0`，产出 `build/ApkVerTool-v2.0.3.apk` |
| **引擎回归** | `AvtCli selftest build/ApkVerTool-v2.0.3.apk` | **≥ 26/27**（现状就是 26/27，第 12 项为既有断言缺陷，与 UI 无关） |
| **包结构** | 与出货包对比 zip 条目集合 | 条目差异仅限"新增/删除的 UI 类与依赖"，无意外丢失 |
| **签名** | `AvtCli verify` | V1*/V2/V3 验签通过 |
| **功能** | `AvtCli info` + 手工过一遍主流程 | 包名 / 版本 / SDK / dex 数 / so 数 / 权限数一致 |
| **观感** | **需要你在真机上跑**（本机无设备、无模拟器镜像） | 逐页截图与旧版对照 |

> ⚠️ **本机限制**：`D:\Android Sdk` 里没有 `cmdline-tools`，`system-images` 为空 → **起不了模拟器**。
> 重构后的观感验收必须靠你的真机。我这边能保证的是：能编译、能出货、引擎自检不退化、包结构合理。

---

## 9. 工作分解（预估，待 Q1–Q8 定稿后展开）

| 阶段 | 内容 | 产出 |
| :--- | :--- | :--- |
| S1 | 主题层 + `AppScaffold` 骨架（顶栏 / NavigationBar / HorizontalPager） | 能跑起一个空壳 App |
| S2 | 控件库：卡片 / 折叠组 / 输入框 / 分段 / 信息行 / 日志面板 / 对话框封装 | `ui/components/` |
| S3 | `MainScreen`（4 页）——**工作量最大**，含折叠组与日志 | 主页可用 |
| S4 | `SettingsScreen`（4 页）+ `ChangelogScreen` | 设置与日志可用 |
| S5 | 业务接线（状态 ↔ 原业务方法）+ emoji 清理 | 功能对等 |
| S6 | 删除旧 UI 代码 / miuix 依赖 / 构建脚本改造 | 构建通过 |
| S7 | 回归验证 + 出包交付 | `build/ApkVerTool-*.apk` |

---

*本报告仅为方案，未改动任何源码。等你确认 §7 后再进入实施。*

---

## 10. 实施结果（2026-10-11 追加）

> 本节在报告之后补记。§1–§9 是方案原文（未改动）；下面是**实际动手后的结果与偏差**。

### 10.1 你的 8 个决策 → 落地情况

| 决策 | 落地 |
| :--- | :--- |
| Q1 (a) 保留滑动切页 | `HorizontalPager` + M3 `NavigationBar`（默认 80dp，**未**压到 64dp） |
| Q2 (a) 删整组底栏设置 | 「外观」页现在只剩「主题」卡片，底栏两组设置已消失 |
| Q3 (a) 删液态玻璃底栏 | `LiquidGlassView` / `GlassShaders` 已删除，`bar_style` 键不再被读 |
| Q4 白底 + 浅蓝清新 | `ui/theme/Color.kt` 固定 `lightColorScheme`，`primary = #2E7CC4`，`surface = #FFFFFF` |
| Q5a (c) 清掉 T1+T2+T3 | 图形 emoji、`✓✗●▾▴↺` 全清；`①②·→•` 换成 M3 表述（序号改 `1. 2.`、列表点改 `BulletLine`） |
| Q5b (b) changelog 正文保留 | `CHANGELOG.md` / `AvtChangelog.java` 一字未动 |
| Q6 (c) 分级提示 | 轻提示（未选文件、条目名为空、任务进行中…）留 `Toast`；结果类走 `Snackbar`（`SnackbarHostState`）；安装成功/被拒/失败走 M3 `AlertDialog` |
| Q7 (b) 迁 Gradle | 已迁。`:core` / `:cli` / `:app` 三模块，`gradle :app:assembleRelease` 出包 |
| Q8 (b) 分隔线改标题样式 | 日志用 `LOG_SECTION` 哨兵前缀，`LogPane` 按 `titleSmall` + primary 渲染小节标题 |

### 10.2 文件级结果

**删除**：`PagedActivity.java`(1184) · `SwipePager.java`(246) · `bar/LiquidGlassView.java`(409) ·
`bar/GlassShaders.java`(213) · `src-kotlin/.../bar/{AvtBottomBar,AvtBarBridge,BitmapBackdrop}.kt`
（`reference/miuix/` 留档未删，已不再参与编译）

**新增（Compose）**：
```
app/src/main/kotlin/com/seagull/avt/ui/
  theme/{Color.kt, AvtTheme.kt}
  AppShell.kt            Scaffold + TopAppBar + NavigationBar + BadgedBox + HorizontalPager
  MainScreen.kt          改写 / 条目 / 体检 / 日志 4 页 + 3 个对话框
  SettingsScreen.kt      外观 / 签名 / 输出 / 关于 4 页
  ChangelogScreen.kt     一版一页，无底栏，页码在标题
  UiState.kt             MainUiState / SettingsUiState（业务状态镜像）
  components/AvtComponents.kt   Card / Field / SwitchRow / FoldGroup / LogPane / Button / BulletLine…
  icons/AvtIcons.kt      20 个自抽 ImageVector（845 行，规避 material-icons-extended 的体积）
app/src/main/java/com/seagull/avt/app/AvtBaseActivity.java
```
**重写（业务逐行等价，只换「读写控件 → 读写状态」）**：
`MainActivity.java` · `SettingsActivity.java` · `ChangelogActivity.java`

### 10.3 ⚠️ 一处必须记下的偏差：**Compose BOM 在本机根本编不过**

`AGENTS.md` 写的「AGP 8.13.2 + BOM 2026.09.00 已验证可编译」**不成立**。BOM 会把
`ui / foundation / runtime / animation / material-ripple` 全解析到 **1.12.1**，而它的
`META-INF/com/android/build/gradle/aar-metadata.properties` 写着
`minCompileSdk=37` + `minAndroidGradlePluginVersion=9.1.0` → AGP 8.13.2 直接失败；
而升 AGP 9.1 又要求 Gradle 9.x（本机 8.14.2）→ 死锁。

**处置**：弃用 BOM，改显式锁版本（理由已写进 `gradle/libs.versions.toml`）：

| 依赖 | 采用版本 | minCompileSdk / minAGP |
| :--- | :--- | :--- |
| compose ui / foundation / runtime / graphics | **1.11.2** | 35 / 8.6.0 ✅ |
| material3 | **1.4.0** | 35 / 8.6.0 ✅ |
| material-icons-core | 1.7.8 | 34 / 1.0.0 ✅ |
| androidx.core:core-ktx | **1.18.0** | 36 / 8.9.1 ✅（1.19.0 要 AGP 9.1 ✗） |
| activity-compose | 1.13.0 | 36 / 8.9.1 ✅ |

> **1.11.2 = 最后一个还被 AGP 8.13.2 接受的 Compose 主线版本。**
> 注意：Gradle 里声明显式版本**压不住** BOM 的约束（冲突时取高版本），所以必须整个去掉 BOM。

### 10.4 出包与回归

| 关卡 | 结果 |
| :--- | :--- |
| 编译 | `gradle :app:assembleRelease` **BUILD SUCCESSFUL** |
| 产物 | `app/build/outputs/apk/release/app-release.apk`，8,715,178 B（出货包 8,192,072 B，**+0.5 MB**） |
| 标识 | 包名 / 应用名 / 版本 `2.0.3(26)` / minSdk 26 / targetSdk 36 **与出货一致** |
| 签名 | 验签通过 `[V1* V2 V3]`，指纹 `C6:88:BF:26…` **与出货完全一致**（升级安装不会被拒） |
| 权限 | 2 → 3（多出的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 是 androidx.core 自动加的签名级权限） |
| 引擎回归 | 见下 |

**`AvtCli selftest`：出货包 26/27，新包 25/27 —— 多挂的一项不是功能回退。**
`[7] DEX 字符串表改写` 的候选包名是**自适应**挑的、且只扫 `classes.dex`；新包的
`com.seagull.apkvertool` 字面量落到了 `classes2.dex`，于是回退挑中 kotlinx 的 `_COROUTINE`，
而断言用**子串**计数残留（`zz_COROUTINEdeep9` 里仍含 `_COROUTINE`）→ 必然 FAIL。
与既知的 `[12]` 属同一类「断言依赖样本字符串」的脆弱假设。
**旁证**：对新包手工跑一次完整链路（改版本 + 改包名 + 加权限 + 重签名）全部通过 ——
清单池改写 / DEX 7 条字符串 / arsc / 原生库长度规则 / 对齐 / V1V2V3 验签均正常。
按 §4.8 约定 **`AvtCli.java` 一行未动**。

### 10.5 需要你在真机上确认的点

1. 4 个主页 + 4 个设置页的观感是否接受（卡片圆角 16→12dp、页面边距 12→16dp、底栏 64→80dp 属 M3 对齐代价）；
2. 左右滑动切页 + 底栏点击是否同步；
3. 「开始修改」后台线程写入日志是否流畅（现在日志走 `SnapshotStateList`，自动滚到底）；
4. 深浅色切换（设置 → 外观）是否即时生效；
5. 选文件后「版本号/包名」是否自动回填、角标数字是否跟随。


---

## 11. 追加：液态玻璃悬浮底栏 + minSdk 抬到 Android 13（2026-10-11 下午）

参考 `D:\LiquidGlass\液态玻璃开源项目分析报告.md`（Kyant0/AndroidLiquidGlass）。
要求：**带液态玻璃效果的悬浮底栏**，且 **minSdk 直接设为 Android 13 (API 33)**。

### 11.1 依赖选型：为什么用 2.0.0 而不是最新的 2.0.1

| 版本 | 依赖的 JetBrains Compose | → androidx compose | 自身 minCompileSdk | 本工程可否用 |
| :--- | :--- | :--- | :--- | :--- |
| `backdrop 2.0.1`（最新） | 1.12.0 | 1.12.x（**要求 AGP 9.1**） | 37 | ❌ 撞 AGP 死锁 |
| **`backdrop 2.0.0`（选用）** | **1.11.0** | **1.11.1** | 37 | ✅ |
| `backdrop 2.0.0-rc01` | 1.11.0 | 1.11.1 | 37 | ✅（rc 不选） |
| `backdrop 2.0.0-alpha03` | 1.10.1 | 1.10.x | 36 | ✅（alpha 不选） |

**2.0.1 用不了**：它把 `androidx.compose.ui:ui` 抬到 1.12.x，而那个版本的
`aar-metadata.properties` 写着 `minAndroidGradlePluginVersion=9.1.0` → AGP 8.13.2 直接编不过；
升 AGP 9.x 又要求 Gradle 9.x（本机 8.14.2）⇒ 与 §5 是同一个死锁。

**2.0.0 可用**：`gradle :app:dependencies` 实测确认，所有 `androidx.compose.*` 仍解析到 **1.11.2**；
JetBrains 那层（`org.jetbrains.compose.*`）只是 KMP 跳转壳，**dex 里一个类都没有**（已验证）。

配套形状库用 `io.github.kyant0:shapes:1.2.0`（提供 `Capsule()` 连续圆角胶囊）。

### 11.2 代价：compileSdk 36 → 37

`backdrop` 自己的 AAR 元数据写着 `minCompileSdk=37`（2.0.0-rc01 起都是 37）。
本机已装 `android-37.0` 平台与 `build-tools 37.0.0`，所以：
`compileSdk = 37`、`buildToolsVersion = "37.0.0"`。
AGP 8.13.2 官方"只测试到 36"，会打 warning →
`gradle.properties` 里 `android.suppressUnsupportedCompileSdk=37,37.0`（**两个值都要写**，只写 37 摁不掉）。

### 11.3 minSdk 26 → 33 的两个连带影响

1. **好事**：玻璃的灵魂 `lens`（边缘折射）走 `RuntimeShader`，只有 API 33+ 才有。
   抬到 33 后代码里**不需要任何 `Build.VERSION.SDK_INT` 降级分支**，
   `vibrancy()` / `blur()`（API 31+）也一并覆盖。
2. **坑**：**minSdk ≥ 28 时 AGP 默认把 `classes*.dex` 原样存储（Stored / 0% 压缩）**。
   实测 APK 从 8.71 MB 涨到 **24.21 MB**（三倍）。
   本工具是"用户之间互传 APK"的形态，下载体积优先，已显式退回压缩：
   ```kotlin
   packaging { dex { useLegacyPackaging = true } }
   ```

### 11.4 实现（两个文件）

**新增 `app/src/main/kotlin/com/seagull/avt/ui/glass/GlassBottomBar.kt`**

```kotlin
Row(
    Modifier.fillMaxSize()
        .drawBackdrop(
            backdrop = backdrop,
            shape = { Capsule() },
            effects = {
                vibrancy()                    // ① 提饱和
                blur(10.dp.toPx())            // ② 雾化
                lens(20.dp.toPx(), 26.dp.toPx(), depthEffect = true)  // ③ 边缘折射
            },
            highlight = { Highlight.Default },                       // 45° 亮边
            shadow = { Shadow(28.dp, DpOffset(0.dp, 10.dp), …) },     // 悬浮投影
            onDrawSurface = { drawRect(surfaceColor) },              // 可读性表层
        )
)
```

- **effects 顺序铁律**：`color filter ⇒ blur ⇒ lens`（写错视觉就不对）。
- 选中指示器是**第二个 `drawBackdrop`**：`lens(9dp, 11dp, chromaticAberration = true)`，
  等于在玻璃上再叠一个"放大镜"，这才是"液态"的关键一笔（官方演示那套的简化版）。
  位置用 `pagePosition = currentPage + currentPageOffsetFraction`，所以**横滑时跟手**，
  而不是等翻页结束才跳。
- 高度 64dp / 左右外边距 24dp / 距底 12dp；`GlassBarReservedHeight = 88dp` 供外部让位。

**重写 `app/src/main/kotlin/com/seagull/avt/ui/AppShell.kt`**

```
Box                                        ← 浮层容器
 ├─ Scaffold(bottomBar = {})               ← 内容；底栏槽刻意留空
 │    Modifier.layerBackdrop(backdrop)
 └─ GlassBottomBar(align BottomCenter)     ← 玻璃浮层，Scaffold 的兄弟节点
```

三个必须做对的地方：

1. **底栏不能放进 `Scaffold` 的 `bottomBar` 槽**（报告 §6.1）——那个槽会给底栏预留空间，
   内容不会从玻璃底下经过 → 玻璃后面没东西可采 → 折射和悬浮同时失效。
2. **`rememberLayerBackdrop { drawRect(背景色); drawContent() }` 必须先补底色**
   （官方教程第 2 步的坑）——只录内容的话玻璃边缘会采到"空"，出现透明像素。
3. **浮层必须是 `Scaffold` 的兄弟节点**，不能被录进图层 ——
   否则形成"A 画进 B、B 又画 A"的循环采样，直接 `SIGSEGV`（报告 §7.3）。

配套：页面 `contentPadding` 底部统一 +88dp（内容能滚到玻璃底下）；Snackbar 让位同高度；
更新日志页 `showBottomBar = false` → 不出浮层也不留白。

### 11.5 验证

| 项 | 结果 |
| :--- | :--- |
| 编译 | `gradle :app:assembleRelease` **BUILD SUCCESSFUL** |
| 产物 | `ApkVerTool-v2.0.3-glass.apk`，**8,714,825 B**（与 §10 的 M3 版 8,715,178 B 基本一致） |
| SDK | **min 33 / target 36**，包名 / 应用名 / 版本 `2.0.3(26)` 不变 |
| 签名 | `[V1* V2 V3]` 通过，指纹 `C6:88:BF:26…` 与出货一致 → **可覆盖升级** |
| dex 类 | `Lcom/kyant/backdrop/*`、`Lcom/kyant/shapes/*`、`GlassBottomBarKt` 均已进 dex |
| 引擎回归 | 新包 **25/27**，失败的两项与 §10 完全相同的既存脆弱断言（**不是回退**） |
| 端到端 | 改版本 + 改包名 + 加权限 + 重签名 **全链路通过** |

### 11.6 待真机确认

1. 玻璃胶囊的**通透度 / 折射强度**是否合适（想更"水"→ 调大 `lens` 的 `refractionAmount`；
   想更雾 → 调大 `blur`；字看不清 → 调大 `onDrawSurface` 的 alpha）；
2. 选中指示器跟手滑动是否顺滑；
3. 内容滚到底时能否正常**从玻璃底下经过**（这是"悬浮"成立与否的判据）；
4. 深色主题下的可读性（深色表层 alpha 是 0.45，可能要调）。

---

## 12. 修复：悬浮底栏的两个真机问题（2026-10-11 上午）

真机截图（`screenshot/`）暴露两个问题，都在 `ui/glass/GlassBottomBar.kt`，与引擎无关。

### 12.1 问题一：选中指示器底色太透，选中态"糊"

**现象**：选中项的胶囊几乎看不出形状，下面的页面内容直接透上来。

**根因**：指示器自己的 `drawBackdrop` 只挂了 `lens`（**没有** `vibrancy` / `blur`），
所以它采样到的就是**原始页面内容**并加以放大，再叠一层 `accent.copy(alpha = 0.14f)` ——
几乎等于只有一层 14% 的淡蓝，等于没底色。

**修法**：底色换成 M3 的 `primaryContainer` 并给到接近不透明的 alpha；
选中文字/图标色同步从 `primary` 换成配对的 `onPrimaryContainer`（对比度更好）。

| | 修改前 | 修改后 |
| :--- | :--- | :--- |
| 浅色底色 | `primary @ 0.14f` | `primaryContainer @ 0.92f`（`#D3E7FB`） |
| 深色底色 | `primary @ 0.22f` | `primaryContainer @ 0.88f`（`#0F4C77`） |
| 选中图标/文字 | `primary` | `onPrimaryContainer` |

### 12.2 问题二："日志"的指示器溢出玻璃边界

**现象**：最后一项的胶囊右端**顶出**玻璃胶囊的圆角（正好 10dp）。

**根因**：坐标系算错了。
`tabWidth = maxWidth / tabs.size` 用的是 `BoxWithConstraints` 的**外框**宽度，
但指示器活在 `Row.padding(GlassBarInset = 5.dp)` **内部**，内容区比外框窄 `2 × 5dp`。
最后一项 `offset = 3 × tabWidth`，右边界 = `4 × tabWidth` = 外框宽度
→ 比内容区多出 `2 × GlassBarInset = 10dp`，正好盖在玻璃的圆角上。

**修法**（三处，互相独立）：

```kotlin
// ① 算宽度时先扣掉两侧内缩 —— 这是根治
private val IndicatorInset = 3.dp
val tabWidth = (maxWidth - GlassBarInset * 2) / tabs.size

// ② 指示器自身再内缩一点，末端不贴边，视觉上"浮"在玻璃里
.offset(x = tabWidth * pagePosition + IndicatorInset)
.width(tabWidth - IndicatorInset * 2)

// ③ 双保险：内容区整体按胶囊裁剪，任何情况下都画不出玻璃之外
Box(modifier = Modifier.fillMaxSize().clip(Capsule()))
```

**附带修掉一个隐藏错位**：图标是按「内框宽度 ÷ 4」排布的（`weight(1f)`），
而指示器原来按「外框宽度 ÷ 4」定位，两者不一致 → 越靠右偏得越多（最后一项累计 7.5dp）。
现在两者共用同一个 `tabWidth`，**指示器与图标严格同心**。

数值示例（360dp 宽屏、左右各 24dp 外边距、5dp 内缩）：

| | 修改前 | 修改后 |
| :--- | :--- | :--- |
| 内容区宽度 | 302dp | 302dp |
| `tabWidth` | 312 / 4 = **78dp**（用错外框） | 302 / 4 = **75.5dp** |
| 末项指示器右边界 | 5 + 4×78 = **317dp** ❌ 溢出 10dp | 3 + 4×75.5 − 3 = **302dp** ✅ 内缩 3dp |

### 12.3 验证

| 项 | 结果 |
| :--- | :--- |
| 编译 | `gradle :app:assembleRelease` **BUILD SUCCESSFUL**（34s） |
| 产物 | `ApkVerTool-v2.0.3-glass.apk`，**8,714,826 B**（与修复前 8,714,825 B 持平） |
| SDK / 版本 | min 33 / target 36，`2.0.3(26)` 不变 |
| 签名 | `[V1* V2 V3]` 通过，指纹与出货一致 → **可覆盖升级** |

改动只涉及 UI 层 Kotlin，`core` / `cli` 一行未动。

---

## 13. 追加：按键缩放 + 拖拽切换 Tab（照搬官方 LiquidBottomTabs，2026-10-11）

要求：把官方 demo 里那版**按压缩放 + 拖拽切换 Tab** 的液态玻璃底栏照搬进来。

源码出处：`D:/LiquidGlass/AndroidLiquidGlass-kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/`
（`components/LiquidBottomTabs.kt` 288 行 + `components/LiquidBottomTab.kt` 49 行 +
`utils/DampedDragAnimation.kt` 137 行 + `utils/InteractiveHighlight.kt` 121 行 +
`utils/DragGestureInspector.kt` 85 行）。

### 13.1 它凭什么"只有选中的那个 tab 是蓝的"

这是整版的机关：**同一份 tab 内容被渲染两遍**。

| | 层 | 作用 |
| :--- | :--- | :--- |
| Row ① | 外层胶囊 64dp | 可见的那份，图标/文字**一律中性色**（`onSurfaceVariant`） |
| Row ② | 同尺寸副本 56dp | `.alpha(0f)` 看不见，但 `.layerBackdrop(tabsBackdrop)` 把它**录进专属图层**；内容是**accent 色**，按下时整体放大 1.2× |
| Box ③ | 拖拽胶囊 56dp × 1/格数 | 采样 `rememberCombinedBackdrop(页面图层, tabsBackdrop)` —— 所以**只有胶囊里能看到被放大的蓝色图标** |

`.alpha(0f)` 之所以不影响录制：modifier 链是 `alpha(0f) → layerBackdrop → …`，
alpha 在 `layerBackdrop` **外层**，而录制是把链内层的内容画进独立的 `GraphicsLayer`，
外层 alpha 只管"别画到屏幕上"，管不到已经录好的图层。

`.graphicsLayer(colorFilter = ColorFilter.tint(accent))` 那招**故意没用**：它是把整层染成
一个纯色剪影，角标也会跟着变成一坨蓝色、数字看不清。改为给内容传 `accent: Boolean`
只染图标/文字，角标保持 M3 原色。

### 13.2 移植进来的 4 个文件

| 文件 | 内容 |
| :--- | :--- |
| `ui/glass/DragGestureInspector.kt` | 拖拽识别器。与 `detectDragGestures` 的区别：Initial pass 先抢一次 down、**全程不 consume** —— 按下即可开始拖（没有 touch slop 等待），且不抢下层手势 |
| `ui/glass/DampedDragAnimation.kt` | 拖拽驱动器：并行的 5 条弹簧（页位置 / 按压进度 / scaleX / scaleY / 速度）。`pressedScale = 78/56`，所以按下时胶囊从 56dp 鼓到 78dp（会鼓出 64dp 的外层胶囊 7dp，这是官方观感） |
| `ui/glass/InteractiveHighlight.kt` | 按下/拖动时跟着手指画一团径向渐隐白光（AGSL RuntimeShader + `BlendMode.Plus`） |
| `ui/glass/GlassBottomBar.kt` | 重写为官方三段式结构 + 与 pager 的双向联动 |

**等价替换（为了不引非必要依赖，行为一致）**：

- `androidx.compose.ui.util.{fastCoerceIn, fastRoundToInt, lerp, fastFirstOrNull}` → stdlib
  （`coerceIn` / `roundToInt` / 手写 lerp / `firstOrNull`）。这几个函数其实在 compose-ui
  的**独立制品** `ui-util` 里，不在 `ui-android` 里，本工程没显式引它。
- `kotlin.time.Clock.System.now()` → `System.nanoTime()/1_000_000`
  （VelocityTracker 只吃时间差，单调时钟即可，顺带免掉 `kotlin.time` 的实验性 opt-in）。
- 官方 `expect suspend fun awaitFrame()` → 直接 `withFrameNanos { }`。

### 13.3 与 pager 的三条联动（官方没有，本工程必须有）

官方 demo 里底栏是"自己管自己"的（`selectedTabIndex` 是本地 state），
本工程的分页真源是 `HorizontalPager`，所以补了三条线，并刻意避开互拽：

| 方向 | 做法 |
| :--- | :--- |
| 拖胶囊 → 页面 | 松手吸附到最近一格 → `currentIndex` 变 → `onSelect(index)` → `animateScrollToPage` |
| 内容横滑 → 胶囊 | `snapshotFlow { currentPage + offsetFraction }` → `updateValue(pos)`，**跟手**（不是翻完才跳） |
| 点 tab | 走外部页号 → 内部 `currentIndex` 同步，同上 |

**怎么区分"这次翻页是谁发起的"**（否则两边会互拽）：

- 内层 `currentIndex` 变化时，只有 **`index != 外部 currentPage`** 才动手
  —— 那一定是底栏自己拖出来的；内容横滑引起的翻页走"跟随"那条线，不再叠一次按压动画；
- 主动推 pager 之前先开 `pagerGate`（记下目标页），**等 pager 走到目标页再放开**跟随。
  否则 pager 的补间会从出发页开始播，把胶囊从目标页拽回出发页；
- `pagerGate` 有 1200ms 兜底，防止那次补间被打断后"跟随"永久卡住。

### 13.4 顺手修掉一个手册没提的坑：底栏拖动会顺带滚页面

浮层底栏和 `HorizontalPager` 在底栏区域是**重叠命中**的，同一串指针事件两边都收得到，
所以拖底栏时页面也会跟着滑。加了个 `swallowDrags()`：挂在底栏**根节点**、
在 **Main pass** 里 consume 移动事件。

顺序刚好合适（Main pass 是从叶子往根派发的）：

```
胶囊的拖拽识别 → 各 Tab 的点击 → swallowDrags → z 序更低的 Pager
```

等轮到 `swallowDrags` 时自己的手势都处理完了，Pager 才看到"已被消费"而放弃。
**不能**放 Initial pass —— 那是从根往叶子，会把胶囊自己的拖拽一起掐掉
（`inspectDragGestures` 见到 `isConsumed` 就退出）。

### 13.5 观感与旧版的差别

| 项 | 旧版（M3 + 静态胶囊） | 新版（照搬官方） |
| :--- | :--- | :--- |
| 选中态 | 常显的浅蓝胶囊（`primaryContainer@0.92`） | 胶囊静止时**只有 10% 压暗 + 一块被放大的蓝色图标**，按下才出放大镜/高光/投影 |
| 按压 | 图标 scale 0.84（按下去变小） | **整条胶囊横向鼓 16dp + 拖拽胶囊 56→78dp，图标放大到 1.2×** |
| 拖拽 | 只能横滑页面 | 按住胶囊可以**直接拖到别的 tab**，松手吸附；甩得快还会横向拉伸 |
| 位置跟手 | 指示器跟手横滑 | 胶囊既跟手指，也跟页面横滑 |

### 13.6 验证

| 项 | 结果 |
| :--- | :--- |
| 编译 | `gradle :app:assembleRelease` **BUILD SUCCESSFUL** |
| 产物 | `ApkVerTool-v2.0.3-drag.apk`，**8,747,597 B**（玻璃静态版 8,714,826 B，**+32 KB**） |
| SDK / 版本 | min 33 / target 36，`2.0.3(26)` 不变 |
| 签名 | `[V1* V2 V3]` 通过，指纹与出货一致 → **可覆盖升级** |
| dex | `GlassBottomBarKt` / `DampedDragAnimation` / `InteractiveHighlight` / `DragGestureInspectorKt` 均已在包内 |

改动只在 `ui/glass/` 与 `ui/AppShell.kt`（传参 + 同页守卫），`core` / `cli` 一行未动。

### 13.7 待真机确认

1. 按住底栏胶囊：整条胶囊是否鼓出来、图标是否放大 1.2×、胶囊是否 56→78dp；
2. 按住胶囊左右拖：是否跟手、松手是否吸附到最近的 tab 并翻页；
3. 拖底栏时**页面不应该跟着滑**（就是 13.4 那条）；
4. 内容横滑时胶囊是否跟手；
5. 静止时选中项是否认得出来（现在只有 10% 压暗 + 放大的蓝图标，
   如果觉得"选中态不够明显"，把 `onDrawSurface` 里那行
   `Color.Black.copy(0.1f)` 的 alpha 往上提即可）。
