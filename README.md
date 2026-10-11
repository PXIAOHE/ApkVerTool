# ApkVerTool — Gradle 构建工程

APK 版本号修改器 + 体检工具。纯自研引擎，不依赖 apktool / aapt2 的资源链：
AXML、resources.arsc、DEX 字符串表、ZIP 打包四个二进制格式全部手写读写。

- 包名 `com.seagull.apkvertool`，versionName `2.1.0`，versionCode `27`
- minSdk 33 / targetSdk 36 / compileSdk 37
- 三模块：`:core`（引擎，纯 JDK）｜`:cli`（桌面命令行）｜`:app`（Android + Compose M3）

---

## 1. 环境要求

| 项 | 版本 | 说明 |
| :--- | :--- | :--- |
| JDK | **17 或 21** | 编译与运行 Gradle 都用它；必须是带 `javac` 的 JDK，不能是 jre-only |
| Gradle | **8.14.2** | 已带 wrapper（`./gradlew`），不需要单独装 |
| Android SDK | platform **android-37.0** + build-tools **37.0.0** | `compileSdk = 37`，低版本平台编不过 |
| 网络 | 能访问 `maven.aliyun.com` / `dl.google.com` | 依赖源已配阿里云镜像优先 |

`local.properties` 里的 `sdk.dir` 指向你本机的 SDK：

```properties
sdk.dir=/path/to/android-sdk
```

也可以不写这个文件，改成设环境变量 `ANDROID_HOME`。

> **为什么必须 compileSdk 37**：`io.github.kyant0:backdrop:2.0.0` 的 AAR 元数据写着
> `minCompileSdk=37`，AGP 会硬报错。这是液态玻璃底栏的代价，不要随手降。

---

## 2. 构建

```bash
# Android APK（产物：app/build/outputs/apk/release/app-release.apk）
./gradlew :app:assembleRelease

# 桌面 CLI（产物：cli/build/install/cli/bin/cli）
./gradlew :cli:installDist

# 只编引擎
./gradlew :core:build
```

Windows 上用 `gradlew.bat`。首次构建会从阿里云拉依赖（约 800 MB），之后走 `~/.gradle` 缓存。

### 在 aarch64（Android 容器 / ARM 服务器）上构建

Android SDK 的 `aapt2` 只有 x86_64 二进制，ARM 机器直接跑会 `Exec format error`。
本仓库带了 `scripts/build-here.sh`，用 `qemu-user` 把 x86_64 的 aapt2 透明跑起来：

```bash
sh scripts/build-here.sh :app:assembleRelease
```

它做三件事：设 `JAVA_HOME`/`ANDROID_HOME`/`GRADLE_USER_HOME`，
并在非 x86_64 时追加 `-Pandroid.aapt2FromMavenOverride=<qemu wrapper>`。
普通 x86_64 机器**不需要**这个脚本，直接 `./gradlew` 即可。

首次准备工具链（Gradle + Android SDK）见 `scripts/setup-toolchain.sh`。

---

## 3. 目录结构

```
├── settings.gradle.kts          # 三模块 + 仓库（阿里云优先）
├── build.gradle.kts             # 根：插件别名（apply false）
├── gradle/
│   ├── libs.versions.toml       # ★ 依赖版本单一真源（含全部版本选择理由）
│   └── wrapper/                 # Gradle wrapper（已换阿里云镜像，见文件内注释）
├── gradle.properties            # JVM 参数、AndroidX、compileSdk 37 warning 抑制
├── local.properties             # 本机 SDK 路径（不进版本库）
├── version.py                   # ★ 版本号 + 更新日志单一真源
├── signkey.p12                  # 内置签名密钥（PKCS12 / alias avt）
├── libs/apksig.jar              # :core 唯一外部依赖（签名/验签）
│
├── core/                        # 引擎（纯 JDK，桌面可跑）
│   └── src/main/java/com/seagull/avt/core/
│       ├── AxmlPatch.java       #   通用 AXML 读写引擎
│       ├── PkgRename.java       #   包名匹配/替换规则
│       ├── Dex.java             #   DEX 字符串表改写（任意长度 + 摘要重算）
│       ├── Arsc.java            #   resources.arsc 解析 / 资源 ID 解析 / 全局池改写
│       ├── PackageRenamer.java  #   包名联动编排（清单 + dex + arsc + so）
│       ├── ZipAlignWriter.java  #   通用 ZIP 重打包 + Zip64 + 对齐体检
│       ├── SignUtil.java        #   apksig 封装：多方案签名 + 验签详情 + 密钥生成
│       ├── SelfSignedCert.java  #   手写 DER 的最小 X.509 v3 自签证书
│       ├── ApkPatcher.java      #   单包完整流水线（CLI / App / 容器共用）
│       ├── SplitBundle.java     #   XAPK / APKS / split 容器处理
│       ├── ApkInfo.java         #   APK 体检 + 加固 / 框架识别
│       └── Sha256.java          #   摘要工具
│
├── cli/                         # 桌面 CLI：info / patch / renamepkg / sign / verify / selftest …
│
├── app/                         # Android 应用
│   ├── src/main/java/…/app/     #   Activity 宿主（业务逻辑，implements MainHost/SettingsHost/ChangelogHost）
│   ├── src/main/kotlin/…/ui/    #   Compose Material 3 界面
│   │   ├── AppShell.kt          #     AvtScaffold：顶栏 + 悬浮玻璃底栏 + HorizontalPager
│   │   ├── MainScreen.kt        #     改写 / 条目 / 体检 / 日志 四页
│   │   ├── SettingsScreen.kt    #     外观 / 签名 / 输出 / 关于 四页
│   │   ├── ChangelogScreen.kt   #     一版本一页，无底栏
│   │   ├── UiState.kt           #     状态持有者（Java ↔ Compose 桥）
│   │   ├── components/          #     AvtCard / AvtField / FoldGroup / LogPane …
│   │   ├── bars/                #     底栏样式（三选一）
│   │   │   ├── BottomBarStyle.kt   #   样式枚举 + SharedPreferences 读写（键 bar_style）
│   │   │   └── M3BottomBar.kt      #   M3 形状 + 液态玻璃同款模糊
│   │   ├── glass/               #     液态玻璃底栏（两种）
│   │   │   ├── GlassBottomBar.kt       #   拖拽版：按压缩放 + 拖拽切页（+ 三个配套文件）
│   │   │   └── GlassStaticBottomBar.kt #   静态版：常显浅蓝胶囊 + 跟手折射
│   │   ├── icons/AvtIcons.kt    #     20 个自抽 ImageVector
│   │   └── theme/               #     固定配色（白底 + 浅蓝 #2E7CC4）
│   └── src/main/assets/signkey.p12
│
├── legacy/                      # 历史手搓链（build.sh / build2.sh / mk*.py / patch_*.py / miuix 参考）
├── dist/                        # 历次预编译产物 + 截图（不属源码）
└── scripts/                     # 本机工具链准备与构建入口
```

`legacy/` 与 `dist/` 是归档，**不参与 Gradle 构建**，可以整个删掉。

---

## 3.5 底栏样式（三种，设置页切换）

三种样式合在一套代码里，**设置页「外观 → 底栏样式」** 三选一，改动立即生效，
返回主页时主页底栏也跟着换（机制与主题切换同源：写 pref → `onResume` 比对 → `recreate()`）。

| 样式 | 落盘 id | 实现 | 对应 APK |
| :--- | :--- | :--- | :--- |
| 液态玻璃 · 动态（默认） | `glass_drag` | `ui/glass/GlassBottomBar.kt` + `DampedDragAnimation` / `InteractiveHighlight` / `DragGestureInspector` | `dist/ApkVerTool-v2.0.3-drag.apk` |
| 液态玻璃 · 静态 | `glass_static` | `ui/glass/GlassStaticBottomBar.kt` | `dist/ApkVerTool-v2.0.3-glass.apk` |
| Material 3（毛玻璃） | `m3` | `ui/bars/M3BottomBar.kt` | `dist/ApkVerTool-v2.0.3-m3.apk` |

**三种样式共用同一条布局路径**：内容录进 backdrop 图层，底栏是 `Box` 上的浮层。

```
Box                                  ← 浮层的容器
 ├─ Scaffold(Modifier.layerBackdrop) ← 页面内容；bottomBar 槽刻意留空
 └─ 底栏浮层(align BottomCenter)      ← 盖在内容之上
```

为什么三种都得这样 —— 因为**三种都要采背景**：

| 样式 | 采背景的用途 |
| :--- | :--- |
| M3 | `vibrancy + blur`（毛玻璃，无 lens；`RectangleShape` 通栏） |
| 液态玻璃 · 静态 | `vibrancy + blur + lens`（胶囊边缘折射） |
| 液态玻璃 · 拖拽 | 同上 + 拖拽胶囊对 `tabsBackdrop` 的二次采样 |

底栏**不能**放进 `Scaffold` 的 `bottomBar` 槽：那个槽会给底栏预留空间，
内容不会从底栏底下经过 → 没东西可采 → 模糊／折射／悬浮全部失效。

三种样式的差异只剩**形状与交互**：M3 是通栏直角 + `NavigationBarItem` 默认配色；
玻璃两种是 24dp 外边距的悬浮胶囊，选中态不同（见各自文件）。

> M3 的顶部**没有分割线**。注意：`drawBackdrop` 的 `highlight` 参数**有默认值**
> （`Highlight.Default`，45° 边缘高光）。玻璃胶囊是圆角，高光顺着弧线走看不出来；
> 但 M3 是**通栏直角矩形**，高光会在顶边拉成一条 **1 像素亮线**，极易被误判成
> "分割线没删干净"。所以 M3 必须显式写 `highlight = { Highlight.Default.copy(alpha = 0f) }`。

### 踩坑记录：底栏顶边渗线

两个都会在底栏顶边留下一条 1px 亮线，都排查过：

| 现象 | 真因 | 处理 |
| :--- | :--- | :--- |
| 顶边一条 1px 亮线 + 4px 衰减尾 | `drawBackdrop` 默认 `highlight`（45° 边缘高光），直角矩形上拉成直线 | 显式 `alpha = 0f` 关掉 |
| 顶边一条 1px 亮线（无衰减） | `layerBackdrop` 挂在 `Scaffold` 上，图层高度比屏幕矮一条底栏，底栏骑在图层边界上采样越界 | 图层宿主换成外层 `fillMaxWidth()` 整屏 `Box` |

判据（可复现）：`screencap`（**不加** `-p`，输出原始像素）后逐像素扫一条底栏后面
没有文字的列，看相邻明度跳变。修好后最大跳变应 < 3（修之前是 16~87）。

```bash
# 注意：screencap 抓的是真实帧缓冲，必须先确认目标 App 在前台且持有焦点
am start -n com.seagull.apkvertool/com.seagull.avt.app.MainActivity
dumpsys activity activities | grep topResumedActivity
screencap /sdcard/s.raw
```

两种玻璃样式共用同一套外观常量（`GlassBarHeight` / `GlassBarMarginHorizontal` /
`GlassBarMarginBottom` / `GlassBarReservedHeight` / `GlassBarInset`）与 `swallowDrags()`，
差别只在交互与选中态：

| 项 | 静态 | 拖拽 |
| :--- | :--- | :--- |
| 选中态 | **常显**浅蓝胶囊（`primaryContainer@0.92`）+ 边缘折射 | 静止时 10% 压暗 + 被放大的蓝图标 |
| 按压 | 图标 scale → 0.84 | 整条胶囊横向鼓 16dp，图标放大 1.2× |
| 拖拽 | 不支持 | 按住胶囊可拖到别的 tab，松手吸附 |
| 跟手 | 指示器跟 `pagerPosition` | 胶囊既跟手指也跟页面 |

新增一个样式要动的地方：`BottomBarStyle` 加枚举项 → 写一个 composable →
`AppShell` 的 `when` 加分支。设置页的选项列表是 `BottomBarStyle.entries` 遍历出来的，
不用改。

---

## 4. 版本号 / 更新日志怎么改

单一真源是 `version.py`：

```bash
python3 version.py genjava app/src/main/java/com/seagull/avt/app
```

它会重新生成 `AvtVersion.java` 与 `AvtChangelog.java`。**这两个文件不要手改。**
改版本号 / 改日志只改 `version.py` 里的 `VERSION_NAME` / `VERSION_CODE` / `CHANGELOG`。
（`app/build.gradle.kts` 里的 `versionCode` / `versionName` 目前是独立写死的，
两边要一起改，见「已知不一致」。）

---

## 5. 几个不要随手改的地方

- **不要引入 Compose BOM**。BOM 会把 Compose 抬到 1.12.x，而那个版本的 AAR 元数据要求
  AGP ≥ 9.1 + Gradle 9.x，与本工程 AGP 8.13.2 / Gradle 8.14.2 死锁。必须显式锁版本。
- **`io.github.kyant0:backdrop` 不要升到 2.0.1**（同样撞 AGP 9.x）；`2.0.0` 是能用的最后一版。
- **不要引入 `material-icons-extended`**：本工程 minify 关闭，会让 dex 膨胀几十 MB。
  需要的图标在 `ui/icons/AvtIcons.kt` 里已经抽好了。
- **`packaging { dex { useLegacyPackaging = true } }` 不要删**：minSdk ≥ 28 时 AGP 默认不压缩 dex，
  删掉 APK 会从 ~8.7 MB 涨到 ~24 MB。
- **不要开 R8**（`isMinifyEnabled = false`）：本工程是「库只 D8 不 R8」的历史约定，
  Compose 与自研回调在 R8 下是崩溃重灾区（见 CHANGELOG 1.4.x 系列修复）。
- `gradle.properties` 里的 `android.suppressUnsupportedCompileSdk=37,37.0` **两个值都要写**，
  只写 `37` 摁不掉 AGP 的 warning。

---

## 6. 签名

`signkey.p12`（PKCS12，alias `avt`，口令 `avtSignKey2026`）同时给 release 和 debug 用，
证书 SHA-256 指纹 `C6:88:BF:26:A7:F8:CB:A7:8F:BE:C1:25:3F:E3:FD:31:A7:0F:71:3C:F2:26:C9:91:15:43:E4:E0:A5:3A:38:F7`
—— 与出货包一致，**能覆盖升级安装**。

- 本地自测可以直接用；
- **要正式对外发布，请换成你自己的签名**（改 `app/build.gradle.kts` 的 `signingConfigs`）。
- 证件副本也在 `app/src/main/assets/signkey.p12`（应用内「重签名」功能读的是它）。

### ⚠️ 这把密钥是公开的，别拿它发正式版

本仓库**有意提交了 `signkey.p12`**（根目录 + `app/src/main/assets/` 两份），原因有两条：

1. **它本来就不是秘密**：每一份发布出去的 APK 里都打着 `assets/signkey.p12`，
   任何人 `unzip` 一下就能拿到。藏进 `.gitignore` 只会让仓库 clone 下来构建不了，
   挡不住任何有心人。
2. **构建需要它**：`app/build.gradle.kts` 的 release 签名读的是 `rootProject.file("signkey.p12")`，
   缺了它 `assembleRelease` 直接失败。

**但它绝不适合用来发正式版本**：任何人都能用这把钥匙签一个 APK，
冒充你的更新、覆盖安装到用户手机上（同签名可直接升级）。

真发版之前请：

```bash
# 1) 生成自己的密钥（工程自带 keygen，不需要 keytool / BouncyCastle）
./gradlew :cli:installDist
./cli/build/install/cli/bin/cli keygen my-release.p12 "你的名字" 10950

# 2) 换成自己的：改 app/build.gradle.kts 的 signingConfigs.avt
#    3) 把仓库里的 signkey.p12 删掉，并加进 .gitignore
```

密钥口令在 `app/build.gradle.kts` 里是明文（`avtSignKey2026`）。真要正经发版，
建议改成从 `local.properties` 或环境变量读，不要写死在版本库里。

---

## 7. 回归自检

```bash
./gradlew :cli:installDist
./cli/build/install/cli/bin/cli selftest <某个.apk>
```

> ⚠️ `selftest` 里 **2 项断言本身是脆弱的**（`[7] 全量替换`、`[12] 带 .so 的整包包名联动`），
> 对某些样本会 FAIL，**这不代表引擎有 bug**。判断引擎是否正常，用端到端命令更可靠：

```bash
./cli/build/install/cli/bin/cli patch <输入.apk> <输出.apk> \
    --name 9.9.9 --code 999 --package com.example.renamed \
    --add-perm android.permission.CAMERA --sign signkey.p12
```

---

## 8. 已知不一致 / 待清理

这些是**沿用源码快照的既有问题**，本次建立工程时未改动：

1. **minSdk 有两套值**：`version.py` 写 `MIN_SDK = 26`，`app/build.gradle.kts` 写 `minSdk = 33`。
   `version.py` 的 `MIN_SDK` / `TARGET_SDK` 只喂 `legacy/` 里的旧手搓链，Gradle 不读。
2. **版本号两处写死**：`version.py` 与 `app/build.gradle.kts` 各写一份，需要一起改。
   彻底收敛可以让 `app/build.gradle.kts` 调 `version.py`（本次未做）。
3. `proguard-rules.pro` 里还在 keep 已删除的 `PagedActivity` / `SwipePager`
   —— 因为 minify 关闭，无害，属残留。
4. `legacy/AndroidManifest.legacy.xml` 是旧手搓链生成的清单（minSdk 26），Gradle 不读它。

---

## 9. 本次建立工程时的验证记录

在 Android/aarch64 + Debian 13 容器内（JDK 21、Gradle 8.14.2、SDK android-37.0 + build-tools 37.0.0）：

| 项 | 结果 |
| :--- | :--- |
| `:app:assembleRelease` | ✅ BUILD SUCCESSFUL（4m 3s，48 tasks） |
| 产物 | `app-release.apk`，**8,747,597 字节** |
| 与参考包 `dist/ApkVerTool-v2.0.3-drag.apk` 对比 | 条目数量与逐条大小**完全一致** |
| `apksigner verify` | ✅ Verifies；V2 + V3 双方案；签名者证书 SHA-256 与出货包一致 |
| `cli info` 自读 | ✅ `[V1* V2 V3]`，包名/版本/SDK 全部正确 |
| 端到端 `cli patch`（改版本 + 改包名 + 加权限 + 重签） | ✅ 清单池改写、DEX 字符串表 7 条、重打包、签名、回读全通过 |
| `:cli:installDist` | ✅ BUILD SUCCESSFUL |

---

## 10. 开源引用

- `libs/apksig.jar` — Android apksig（Apache-2.0）
- `io.github.kyant0:backdrop` / `shapes` — AndroidLiquidGlass（液态玻璃底栏的背景采样与折射）
- `legacy/reference/miuix/` — compose-miuix-ui 的 NavigationBar 参考源码（历史底栏规格来源）

开发者：Seagull（海鸥）
