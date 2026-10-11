# ApkVerTool v2.0.3 — APK 版本号修改器 + 体检工具

纯 framework 代码、无任何第三方 UI 库的安卓工具 App。选 APK → 读/改清单字段 →
包名联动 → 增删包内条目 → 对齐重打包 → V1/V2/V3(+V4) 重签名 → 输出可直接安装的新 APK。
支持 XAPK / APKS / split 多包容器。

- 包名: `com.seagull.apkvertool`  versionName: 2.0.3  versionCode: 26
- minSdk 26 / targetSdk 36（无 resources.arsc 的极简包由 `mkaxml.py` + `mkicon.py` 手搓生成）
- **版本号与更新日志单一真源：`version.py`**，改版本 / 改日志只改这一个文件

## 界面结构（分页 + Miuix 标准底栏）

主页四个分页：**改写 / 条目 / 体检 / 日志**
设置页四个分页：**外观 / 签名 / 输出 / 关于**
更新日志独立成 `ChangelogActivity`，一个版本一页、**左右滑动翻页（不要底栏）**，
页码同步到标题栏右侧（`📜 更新日志 3/19`）——版本多的时候滑到哪页一眼可见。

分页切换统一放在**底部导航栏**里。底栏**按 Miuix（compose-miuix-ui/miuix）源码的规格重写**，
默认形态就是 Miuix 的 `NavigationBar`（通栏贴底），参考源码留档在 `reference/miuix/`
（`NavigationBar.kt` 等原始文件 + 规格对照表）：

| 规格 | 取值 | 出处 |
|---|---|---|
| 底栏高度 | 64dp | `NavigationBarDefaults.ItemHeight` |
| 圆角 | 高度的一半（胶囊） | `FloatingToolbarDefaults.CornerRadius = 50.dp` |
| 距底部 | 26dp + 导航栏 inset | `FloatingNavigationBar` 的 `bottomPaddingValue` |
| 内边距 | 2dp | `FloatingNavigationBar` 的 `0.75.dp` |
| 投影 | elevation 6dp（对应 radius 10dp 软投影） | `FloatingNavigationBar` 的 `dropShadow(radius = 10.dp)` |
| 图标尺寸 | 26dp | `NavigationBarDefaults.IconSize` |
| 图标上边距 / 标签下边距 | 8dp / 8dp | `IconTopPadding` / `BottomPadding` |
| 标签字号 | 12sp | `NavigationBarDefaults.LabelFontSize` |
| 未选中透明度 | 0.4 | `NavigationBarDefaults.UnselectedAlpha` |
| 按下透明度 | 选中 0.5 / 未选中 0.6 | `SelectedPressedAlpha` / `UnselectedPressedAlpha` |
| 选中 / 未选中同色、**无胶囊底** | — | `NavigationBarItemColors.contentColor()` |
| 显示模式 | 图标+文字 / 仅图标 / 选中显文字 | `NavigationBarDisplayMode` |
| 角标（数字） | 16dp 圆 + 11sp（footnote2）+ `colorScheme.error` | `BadgeDefaults.LargeSize` |
| 角标（小红点） | 6dp 圆 | `BadgeDefaults.Size` |
| 角标位置 | 图标右上角（横向内缩 12dp、纵向内缩 14dp） | `BadgeWithContentHorizontal/VerticalOffset` |

默认的 `BAR_MIUIX` 与源码取值**完全一致，不做偏离**；只有可选的液态玻璃胶囊（`BAR_ICON`）
把选中色换成主题色，用来对齐酷安底栏观感。

底栏四种形态，由 `PagedActivity` 的 `BAR_MIUIX` / `BAR_ICON` / `BAR_CHIP` / `BAR_NONE` 选择：

| 形态 | 用在哪 | 样子 |
|---|---|---|
| `BAR_MIUIX`（默认） | 主页、设置页 | Miuix 标准 NavigationBar：通栏贴底 + 顶部细分割线，图标 + 文字，选中加粗、未选中 40% 透明，图标右上角可挂数字角标 / 小红点 |
| `BAR_ICON`（可选） | 主页、设置页 | 液态玻璃胶囊：悬浮 + 圆角胶囊 + 实时毛玻璃，选中项主题色 |
| `BAR_CHIP` | 历史形态（当前无页面使用） | 纯文字胶囊，**全部合并进同一个玻璃容器**横向滚动、垂直居中，选中项 accent 填充 |
| `BAR_NONE` | 更新日志页 | 一个底栏都不建：只有顶部标题栏 + 左右滑动翻页，内容一直铺到屏幕底部 |

底栏样式与显示模式在**设置页「外观」**里切换（`bar_style` / `bar_mode` 两个 SharedPreferences 项），
改动即时生效（`recreate()`）。角标接的是真实状态：改写页 = 已选文件数，条目页 = 待增删条目数。
对外接口：`setBarBadge(index, count)` 数字角标、`setBarDot(index, on)` 小红点（仅 `BAR_MIUIX` 生效）。

切页支持**左右拖动跟手 + 吸附**，由手写的 `SwipePager`（纯 framework 的极简 ViewPager）提供，
底部玻璃栏的高亮与页面位置双向同步。**底栏区域本身也可滑动切页**：底栏容器重写
`dispatchTouchEvent`，先把事件喂给 `SwipePager`，判定为横向拖动就整段吞掉（含 UP），
否则原样下发保证点击照常。

> 关于 Miuix：`compose-miuix-ui/miuix` 是 Compose Multiplatform 库（Kotlin + Compose 1.12），
> 引入它需要 Kotlin 工具链、Compose 编译器插件、完整 resources.arsc 与 Gradle；
> 本工程是 javac + 手搓 AXML + 584 字节 arsc + 无 Gradle 的极简链路，因此**按 Miuix 的视觉语言
> 在现有技术栈里原生实现**，效果一致而不动构建链。

每页是独立 ScrollView，只切换可见性：切页不丢输入内容，也不会再出现一条无限拉长的滚动页。
整站尺寸做了紧凑化（卡片内边距、间距、控件高度统一收窄）。

## 能力清单

### 清单改写（`AxmlPatch`，通用 AXML 引擎）
- versionName / versionCode / **versionCodeMajor（64 位版本号）** / minSdkVersion / targetSdkVersion
- **包名** / **应用名 android:label** / debuggable / extractNativeLibs / allowBackup
- uses-permission **增删**
- 属性缺失时**自动新增**（同步补 `RES_XML_RESOURCE_MAP` 框架属性 ID）
- **字符串池按原编码重建**：UTF-16 池保持 UTF-16，不强制转 UTF-8
- `rewritePool()`：按索引整池改写，块结构不变（包名联动用）

### 包名联动（`PackageRenamer` + `PkgRename`）
一次改包名，四处一起改：

| 目标 | 方式 | 长度限制 |
|---|---|---|
| AndroidManifest.xml | 整张字符串池按索引改写（package / authorities / 组件名 / 自定义权限全跟随） | 无 |
| classes*.dex | 字符串表重建 + **追加重指向**（只改 string_ids 的 4 字节偏移，不动代码） | **无** |
| resources.arsc | 全局值字符串池改写（索引稳定） | 无 |
| lib/**/*.so | 原地等长字节替换 | **仅等长**（so 内字符串按偏移引用，改长度会破坏 .rodata） |

匹配规则做了边界保护：`com.old.pkg` 后面必须跟 `.` 或结束，`com/old/pkg` 后面必须跟 `/` 或 `;`，
所以 `com.old.pkgX` 不会被误伤。dex 改写后会重算 **SHA-1 签名 + Adler-32 校验和**，
并同步 map_list 的 STRING_DATA_ITEM 项（保持 offset 递增）。

### 多包容器（`SplitBundle`）
- 识别 `.xapk` / `.apks` / 自建 split 压缩包（zip 内含 *.apk 且自身不是 APK）
- 容器内**每个子包**分别走完整流水线；split 分片没有 versionCode 时自动跳过版本字段
- **toc.pb 摘要回填**：按「旧内包 SHA-256 → 新内包 SHA-256」等长替换，避免摘要过期
- **manifest.json 同步**：version_name / version_code / package_name / target_sdk_version

### 重打包（`ZipAlignWriter`，通用 ZIP 引擎）
- **任意条目批量替换 / 新增 / 删除**（支持 `lib/armeabi-v7a/` 这类目录前缀删除）
- **完整 Zip64**：条目数 > 65535、单条目或总大小 > 4GB 不再溢出
- **保留原条目 extra field**（过滤旧 0xd935 对齐字段与旧 zip64 字段后重算）
- 对齐规则：`lib/**/*.so` 按 16384 字节（兼容 4K / 16K 页设备），其余 STORED 条目 4 字节
- 压缩级别可调
- **对齐体检报告**：逐条目真实数据偏移、所需对齐、异常定位；正确识别签名块前的 4KB 零填充

### 签名（`SignUtil` + `SelfSignedCert`）
- V1 / V2 / V3 / **V4** 方案可分别开关
- **验签详情**：签名者主体/颁发者/序列号/算法/有效期/SHA-256/SHA-1 指纹、调试证书识别
- `[V1* V2 V3]` 中的 `V1*` 表示「V1 文件已写入，但 minSdk ≥ 24 时系统不校验 V1」——正常现象
- **一键生成签名密钥**：纯 JDK 手写 DER 生成 RSA-2048 自签 X.509 v3 + PKCS#12，不依赖 BouncyCastle

### 体检（`ApkInfo` + `Arsc`）
包名 / 版本 / SDK / 体积 / 条目数 / DEX 数（含隐藏 dex 检测）/ ABI / 原生库数 / 权限统计 /
**加固壳识别**（360、梆梆、腾讯乐固、阿里、娜迦、几维、爱加密、百度等）/ **框架识别**
（Flutter、Unity IL2CPP、React Native、Hermes、Unreal、Cocos、Cordova…）/ 签名者指纹 /
**应用名（android:label 是资源引用时从 resources.arsc 解出真实字符串）**

### App 交互
- **多选批量**处理，逐个输出
- 输出后**一键安装** / **一键分享**，按钮就在「改写」页「开始修改」下面
- 输出先落到应用私有目录保证「安装/分享」总能拿到本地文件，再按需额外导出到设置页指定的 SAF 目录

### 安装（含未签名产物的 root 安装）
- **已签名产物**：走系统安装器（`ACTION_VIEW` + 自实现 `ApkProvider` 的 content:// URI）
- **未签名产物**（MT 式不重签输出）：系统安装器必然拒绝，改用 **root 静默安装**：
  `su -c "pm install -r -d --user 0 <apk>"`
- 点「安装」时会先弹窗说明前提：**① 设备已 root 且授予 su；② 系统已装核心破解
  （CorePatch / 核心破解类模块）并禁用了 APK 签名校验**。缺 ② 时即使拿到 root 也会报
  `INSTALL_PARSE_FAILED_NO_CERTIFICATES`，应用会自动识别这类错误并再次提示核心破解
- root 状态（`su -c id` 是否返回 `uid=0`）会缓存，避免反复弹授权

## 目录结构

```
├── version.py               # ★ 版本号 + 更新日志单一真源
├── build.sh                 # 一键构建（含 27 项核心引擎自检）
├── proguard-rules.pro       # R8 混淆规则（含全部避坑注释）
├── mkaxml.py                # 手搓 AXML 编译器：二进制 AndroidManifest 生成
├── mkicon.py                # 应用图标 PNG + 最小 resources.arsc 生成器
├── pack.py                  # 组包（arsc 对齐 + 双 dex + assets）并调用 CLI 签名
├── signkey.p12              # 内置签名密钥
└── src/
    ├── com/seagull/avt/core/      # 核心引擎（纯 JDK，桌面可测试）
    │   ├── AxmlPatch.java         #   通用 AXML 读写引擎
    │   ├── PkgRename.java         #   包名匹配/替换规则（dex/arsc/so/清单共用）
    │   ├── Dex.java               #   DEX 字符串表改写（任意长度 + 摘要重算）
    │   ├── Arsc.java              #   resources.arsc 解析 / 资源 ID 解析 / 全局池改写
    │   ├── PackageRenamer.java    #   包名联动编排（清单 + dex + arsc + so）
    │   ├── ZipAlignWriter.java    #   通用 ZIP 重打包 + Zip64 + 对齐体检
    │   ├── SignUtil.java          #   apksig 封装：多方案签名 + 验签详情 + 密钥生成
    │   ├── SelfSignedCert.java    #   最小 X.509 v3 自签证书生成器（手写 DER）
    │   ├── ApkPatcher.java        #   单包完整流水线（CLI / App / 容器共用）
    │   ├── SplitBundle.java       #   XAPK / APKS / split 容器处理
    │   ├── ApkInfo.java           #   APK 体检 + 加固/框架识别
    │   └── Sha256.java            #   摘要工具（toc.pb 回填、产物指纹）
    ├── com/seagull/avt/cli/AvtCli.java       # 桌面 CLI
    └── com/seagull/avt/app/                  # App UI（纯代码布局，无 XML 资源）
        ├── PagedActivity.java                # 分页外壳：顶栏 + 标签 + 页容器 + 通用样式
        ├── MainActivity.java                 # 改写 / 条目 / 体检 / 日志
        ├── SettingsActivity.java             # 外观 / 签名 / 输出 / 关于
        ├── ChangelogActivity.java            # 更新日志（一版本一页，无底栏 + 标题栏页码）
        ├── ApkProvider.java                  # 极简文件 ContentProvider（安装/分享用）
        ├── AvtVersion.java                   # 由 version.py 自动生成，勿手改
        └── AvtChangelog.java                 # 由 version.py 自动生成，勿手改
```

## 构建依赖（libs/，未随源码分发，约 170MB）

| 文件 | 用途 | 获取（任选镜像） |
|---|---|---|
| r8.jar | R8/D8 编译器 | https://dl.google.com/android/maven2/com/android/tools/r8/9.5.22/r8-9.5.22.jar |
| apksig.jar | 签名库 | https://dl.google.com/android/maven2/com/android/tools/build/apksig/9.5.0-alpha08/apksig-9.5.0-alpha08.jar |
| android.jar | API16 完整库模型（java.* 齐全，R8 依赖识别必需） | https://repo1.maven.org/maven2/com/google/android/android/4.1.1.4/android-4.1.1.4.jar |
| android28.jar | API28 编译模型（javac -cp 用） | https://repo1.maven.org/maven2/org/robolectric/android-all/9-robolectric-4913185-2/android-all-9-robolectric-4913185-2.jar |
| core-oj.jar / core-libart.jar | D8 编 apksig 时的运行时模型 | 从设备 `/apex/com.android.art/javalib/` 拷贝 |

## 构建

```
mkdir libs && cd libs   # 放入上表 jar
cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v2.0.3.apk
sh build2.sh fast       # 路线 A（真 Compose / miuix 底栏，需 kotlinc + aapt2 + aar/）：同一产物名
```

`build.sh` 会用 `build/` 下最新的历史产物当样本，先跑一遍 27 项核心引擎自检，失败即中断。

## 命令行用法

```
java -cp libs/apksig.jar:build/cli com.seagull.avt.cli.AvtCli <命令>

  info <apk>                            APK 体检（应用名会从 arsc 解析）
  read <apk>                            只读版本号 / SDK / 包名
  manifest <apk>                        清单摘要
  align <apk>                           对齐体检
  list <apk>                            列出全部条目
  res <apk> <0x7f11003e>                解析 resources.arsc 资源 ID
  dexstrings <apk> [dex] [关键字]        导出 dex 字符串表
  patch <in> <out> [选项]               通用改写（自动识别多包容器）
  bundle <in> <out> [选项]              强制按 XAPK / APKS 容器处理
  renamepkg <in> <out> <新包名> [--sign <p12>]   包名联动改写
  repack <in> <out> <name|-> <code|->  仅重打包（不签名）
  run <in> <out> <name|-> <code|-> <p12> <minSdk>
  sign <in> <out> <p12> <minSdk>        仅签名
  verify <apk>                          验签详情
  keygen <out.p12> [CN] [天数]          生成自签密钥
  selftest <apk>                        端到端自检（27 项断言）

patch / bundle 选项：
  --name V / --code N / --code-major N / --min-sdk N / --target-sdk N
  --package P（= --rename-pkg，联动 dex / arsc / so）/ --label L
  --debuggable true|false / --extract-native-libs true|false
  --utf8-pool                          强制把字符串池重建为 UTF-8
  --add-perm P / --del-perm P          权限增删（可重复）
  --drop ENTRY                         删除条目或目录前缀（可重复）
  --put ENTRY=FILE                     替换/新增条目（可重复）
  --keep-signature                     MT 式保留旧 V1 签名条目
  --sign <p12>                         输出前签名
```

示例：

```
# 改版本 + 加权限 + 删目录 + 换条目 + 签名
AvtCli patch app.apk out.apk --name 2.0.0 --code 200 --target-sdk 36 \
    --add-perm android.permission.POST_NOTIFICATIONS \
    --drop "lib/x86/" --put "assets/config.json=./config.json" \
    --sign signkey.p12

# 包名联动改名（dex 字符串表任意长度改写）
AvtCli renamepkg app.apk out.apk com.newco.app --sign signkey.p12

# 处理 XAPK / APKS 容器
AvtCli bundle game.xapk out.xapk --name 1.2.3 --code 123 --sign signkey.p12
```

## 内置签名密钥

`signkey.p12`：alias `avt`，密码 `avtSignKey2026`，RSA-2048 自签
（CN=ApkVerTool, OU=Seagull, O=Seagull）。工具级密钥：所有输出 APK 同指纹。
换密钥：设置页「导入密钥」或「一键生成新密钥」，也可替换 `assets/signkey.p12` 后重编。

## 验证记录（v2.0.3）

- 主页三组折叠（`addFoldGroup(key, title, defaultOpen)` + `toggleFold` + `isFoldOpen`）：
  📦 版本号（versionName / versionCode / versionCodeMajor，默认展开）、
  📱 安卓SDK版本（最小SDK / 目标SDK，默认收起）、🏷 应用标识（应用名 / 包名，默认收起）
- 提交时按 `isFoldOpen(key)` 决定读不读整组输入框：收起 = 整组不参与改写

## R8 shrink 实验记录（未启用）

想进一步压 dex 试过 R8，结论是**当前不可用**：R8 9.5.22 / 9.5.23 在本工程输入上直接内部崩溃
（`java.lang.NullPointerException: Cannot read field "n"`，堆栈在 `internal.v6.a`），
且与 JDK 版本（21 / 25 都崩）、`-dontshrink -dontoptimize -dontobfuscate`、`--no-desugaring` 都无关。
已定位到 `com.seagull.avt.bar.LiquidGlassView` 和 `libs-merged.jar` 里都有触发点，
属于 R8 自身 bug，不是配置问题。要再压体积只能走「不引 Compose」那条路（产物 471KB）。

## 验证记录（v2.0.2）

- `ZipAlignWriter.Edit.level` 默认值 `Deflater.BEST_SPEED(1)` → `BEST_COMPRESSION(9)`：
  实测两个 dex 压缩后 8.65MB → 7.62MB（deflate 对 dex 的压缩率 39% → 35%），整包省约 1.0MB
- 体积构成（v2.0.1，8.83MB）：dex 8.65MB（98%）、resources.arsc 146KB、res 13KB、
  `lib/arm64-v8a/libandroidx.graphics.path.so` 4.5KB、assets/signkey.p12 2.6KB、V1 签名 5KB
  → 想再小只能动 dex：Compose 全家桶（foundation 3556 类 + ui 3086 类 + runtime 1081 类）+ miuix 1626 类，
  自己的代码只有 132 个类

## 验证记录（v2.0.1）

- 主页「📱 安卓SDK版本」折叠区：默认收起（两个框 GONE，提交时直接跳过 minSdk / targetSdk），
  点标题展开成两行（各占一整行，长数字不再被并排挤成半宽），右侧文字在「展开 ▾ / 收起 ▴」间切换
- 选中 APK 后仍会自动把清单里的现值填进这两个框，展开即可看到并按需修改；收起则完全不参与改写

## 验证记录（v2.0.0）

2.0.0 = **1.8.0 以来所有修复的汇总版**（底栏 / 签名 / 输出链路），逐项记录见下面各版本小节，关键几条：

- 不重签产物保留原签名：CLI `repack` 产物签名块与原包**逐字节一致**（4096 字节，SHA-256 相同），
  `META-INF/AVT.SF` / `AVT.RSA` / `MANIFEST.MF` 都在
- 不重签产物的验签详情能列出「签名者 1（原签名，未重签）」，不再显示「无签名」
- SAF 导出：真机复现 `Insert not supported` 失败 → 改成 `createDocument` 优先后导出成功
- 指定输出目录后不再留两份：`files/out` 为空，产物只在 Download
- 更新日志页无底栏、页码与左右滑动正常（真机，18 页）
- 32.9MB / 4078 条目的系统 APK 在 CLI 与 App 两端跑通

## 验证记录（v1.8.4）

- root 静默安装的结果改成「日志 + toast + 对话框」三重提示，成功 / 被系统拒绝 / 其他失败分别给不同文案
- 成功判定放宽到退出码：`Success` 或 `(exit=0)` 且无 `failure` / `exception` 才算成功 ——
  Magisk su 下偶尔只回退出码，之前会被判成「安装失败，详见日志」
- 安装前先比对产物包名与本应用包名一致就提示「装完系统会重启本应用」

## 验证记录（v1.8.3）

- 指定输出目录时不再留两份：产物先落在 `getExternalFilesDir("out")`（App 的工作目录，
  安装 / 分享从这里取），导出到指定目录成功后立刻删掉中转副本，日志写「中转副本已清理」
- 中转副本删掉后，安装 / 分享改用指定目录的 uri：系统安装器直接吃 uri，
  root 静默安装会把 uri 流式拷回 `cacheDir` 再 `pm install`

## 验证记录（v1.8.2）

- 真机复现「输出位置」问题：32.9MB / 4078 条目的系统 APK 做不重签输出时报
  `SAF 导出失败: Insert not supported` —— 选中的目录 provider 不认 `insert`；
  改成 `createDocument` 优先后导出成功（两条都失败才提示换目录）
- 设置页选目录加了探针写入（建一个 `.avt_write_probe` 再删），不可写的目录当场提示、不写进配置
- 日志页自动滚到最新行；`pm install` 失败的 30 行 Java 堆栈压成 3 行 + 结论

## 验证记录（v1.8.1）

- **更新日志页去底栏**：18 个版本页全部创建成功（`addPage ver=1.8.1 mode=3 noBar=true bar=null` 无异常），
  左右滑动翻页与标题栏页码（`2/18`）双向同步正常
- **不重签输出保留原签名**（CLI `repack`，与 App 共用同一份 `ApkPatcher`）：
  产物里 `META-INF/AVT.SF`、`META-INF/AVT.RSA`、`MANIFEST.MF` 都在，
  且 `APK Sig Block 42` 签名块与原包**逐字节一致**（4096 字节，SHA-256 相同）
- **不重签产物的验签详情**：apksig 因摘要不匹配不回报证书 → 自行解析签名块取证书，
  按 SHA-256 去重后只列一条（`签名者 1（原签名，未重签）` + 说明性 WARN），不再显示「无签名」

## 验证记录（v1.8.0）

核心引擎自检 **27 项断言全部通过**：

- 清单改写往返（versionName / Code / CodeMajor / minSdk / targetSdk / 权限 / 池编码保持）
- 重打包 + 对齐体检、条目新增与删除、大条目边界
- 签名（含新生成的自签密钥）+ 验签 + 产物回读
- **DEX 字符串表任意长度改写** + SHA-1/Adler-32 摘要校验 + 残留检查
- **resources.arsc** 解析、全局池改写、`android:label` 资源引用还原
- **XAPK 容器**：2 个子包全部改写、manifest.json 同步、toc.pb 摘要回填
- **.so 等长原地替换**（含边界保护：`com.old.pkgX` 不误伤）+ 带 .so 的整包包名联动

真机验证：

- 真实第三方 APK（18.5MB、1589 条目、15 个 .so、UTF-16 池、extractNativeLibs=false）：
  改 4 个字段 + 加权限 + 加条目 → 重打包 → 签名 → 验签通过，`.so` 全部 16KB 对齐，池保持 UTF-16
- **包名联动实测**：`com.dajiu.stay` → `com.seagull.renamed.stay`，dex 里 528 条字符串改写
  （类描述符 `Lcom/dajiu/stay/...` 全部跟随），清单与签名同步更新
- Zip64 路径：构造 70009 条目包 → 重写输出 70006 条目，Zip64 EOCD + locator 正确，Python 全量 CRC 校验通过
- 端到端装机：用工具改自己的包（→ 1.8.1-e2e / code 19）→ `pm install` 成功 → 系统报告新版本 → 恢复

## 已知边界

- `.so` 内的包名引用**仅在等长改名时**才会被替换；长度不同会跳过并在报告里说明
- 顺序对齐扫描在遇到「文件区结束」时停止，其后是签名块与中央目录（报告中已说明）
- APKS 的 `toc.pb` 只做 SHA-256 摘要等长回填；若用 bundletool 严格校验，建议重新生成
- 不解析 resources.arsc 中除全局值池以外的其它字符串池（键名/类型名不参与包名联动）

## 历史坑备忘（本工程实测踩过，规则文件内有对应注释）

1. javac 25 对 `--release 8` 的 lambda 生成代理类 → R8 拒绝 → 全部改匿名内部类
2. R8 9.5.22 对 `extends/implements` 条件 keep 不可靠 → 一律类名/包名锚定
3. R8 shrink 剥离"仅被库内部调用"的方法（Runnable.run、OutputStream.write(int)）→ keep 兜底
4. apksig 含 lambda → 单独 D8 编 classes2.dex，不进 R8
5. Android 15+ 的 systemWindowInsetTop 掺兼容层假 inset → 沉浸式用 stableInsetTop
6. `extractNativeLibs=false` 要求 `lib/**/*.so` 在 zip 内 16KB 页对齐
7. 手搓 Manifest AXML 必须含 RES_XML_RESOURCE_MAP，否则 PM 找不到 android:name
8. R8 的 `--output` 目录必须**预先存在**，否则报 "Invalid output"
9. `ParcelFileDescriptor` 会让 R8 去解析 `java.net.*`，android.jar 缺这些模型 → 需 `-dontwarn java.net.**`
10. `zipfile.ZipFile(path,"w")` 默认 **STORED**，不写 `ZIP_DEFLATED` 会导致自产 APK 未 zipalign
11. apksig 会在签名块前插入 4KB 对齐零填充，定位签名块不能只按 magic 直接找
12. X.509 的 UTCTime 是 `tag 0x17 + 原始 ASCII 字节`，套两层 TLV 会被 JDK 拒绝解析
13. dex 字符串改长度后必须重算 SHA-1 签名与 Adler-32，并同步 map_list 的 STRING_DATA_ITEM 且保持 offset 递增
14. 自检不要依赖样本恰好包含某个字符串——样本可能是被 R8 全量重打包过的包，候选要自适应
15. Miuix 是 Compose Multiplatform 库，本工程的无 Gradle / 无 Kotlin / 手搓 arsc 链路无法直接承载
16. `pm install` 未签名 APK 在已装核心破解的设备上会直接 `Success`；未装时会报
    `INSTALL_PARSE_FAILED_NO_CERTIFICATES`，root 安装的错误分支要按这个字符串判定
17. EditText 会在按下时 `requestDisallowInterceptTouchEvent(true)`，把横向滑动全部吃掉，
    导致在输入框上滑不动页。`SwipePager` 覆写该方法始终 `super(false)`，改为在拦截判定上提高门槛
    （小幅度拖动仍归输入框保留文本选择，果断横滑才切页）
18. `input swipe` 时长太短（&lt;300ms）时产生的 MOVE 事件不足，测不出滑动效果；真机手测或
    用 600ms 左右的长滑动才可靠
19. 底栏高度必须按内容算：`图标上边距 + 图标高 + 标签行高 + 标签下边距 + 内边距`。
    只给 TextView 设 `textSize` 而不固定高度时，字体自带的行距会让内容溢出被裁切——
    正确做法是给图标固定高度并 `setIncludeFontPadding(false)`
20. 在底栏容器 `dispatchTouchEvent` 里转发触摸时，**拖动中的 UP 也必须吞掉**，
    否则 UP 会落到子项自己的点击逻辑上，一次横滑会变成"跳到那一项对应的页"
21. 「不重签输出」要管**两个**开关：`keepOldSignature`（V1 的 META-INF 条目）**和**签名块（V2/V3）搬运。
    只留 V1 条目、开关又默认关着时，产物看起来就是"一条签名都没留下"
22. `ACTION_OPEN_DOCUMENT_TREE` 必须带 `FLAG_GRANT_PERSISTABLE_URI_PERMISSION`，
    否则 `takePersistableUriPermission` 拿到的授权重启即失效，SAF 导出静默失败
23. SAF 的 `insert` **不覆盖**同名文档：重名要么先 `deleteDocument`，要么换名，否则第二次输出就报错
24. 页面改成 BAR_NONE（不建底栏）后，`bar` 字段就是 null，`addPage` 里所有碰 `bar` 的分支都要先判空
25. `ContentResolver.insert()` 到 SAF 目录**不是**所有 provider 都支持：MIUI「安全访问」直接抛
    `Insert not supported`，得用 `DocumentsContract.createDocument()`（反过来也有只认 insert 的 provider，
    所以两条都要试）
26. 日志 TextView 要自己滚到底（`Layout.getLineTop(lineCount) - height`），否则内容一多，
    看到的永远是开头那三行

## 开源引用

| 项目 | 用途 |
|---|---|
| [Miuix](https://github.com/compose-miuix-ui/miuix) | 底栏**直接采用其 Compose 实现**：`NavigationBar` / `FloatingNavigationBar` + `miuix-blur` 模糊与高光管线；配色由 App 主题注入 |
| AndroidX（activity / lifecycle）+ Jetpack Compose 运行时 | 承载 miuix 底栏与 edge-to-edge 布局 |
| apksig（Android Open Source Project） | APK 签名与验签 |
| R8 / D8（Android Open Source Project） | 代码压缩与 dex 生成 |

分页容器（`SwipePager`）、文件 `ContentProvider` 仍是手写实现，不依赖 androidx。
底栏交互效果另参考了酷安 App。

## 开发者

Seagull（海鸥）· AI Agent 构建链：javac + R8 + apksig + 手搓 AXML/arsc/dex 改写器
