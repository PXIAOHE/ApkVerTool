<div align="center">

# ApkVerTool

**APK 版本号修改器 + 体检工具** —— 选 APK → 改清单字段 → 包名联动 → 增删条目 → 对齐重打包 → 重签名，输出可直接安装的新包。

UI 提供两套完整界面：**[Miuix](https://github.com/compose-miuix-ui/miuix)**（小米 HyperOS 风格）与 **Material 3**，设置里热切换。

![Android](https://img.shields.io/badge/Android-13%2B%20(API%2033)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Miuix%20%2F%20Material%203-4285F4)
![License](https://img.shields.io/badge/License-GPL--3.0-blue)

<br>

引擎是**纯自研**的：AXML / `resources.arsc` / DEX 字符串表 / ZIP 重打包四个格式全部手写读写，不依赖 apktool / aapt2 的资源链。

</div>

---

### 📌 基本信息

| 项目 | 说明 |
| :--- | :--- |
| **当前版本** | `2.2.0`（`versionCode 28`） |
| **包名** | `com.seagull.apkvertool` |
| **最低系统** | **Android 13（API 33）** 及以上 |
| **目标 SDK** | API 36（`compileSdk 37`） |
| **支持容器** | `.apk` 单包 / 多选批量 / `.xapk` / `.apks` / split 压缩包 |

---

## ✨ 功能

### 清单改写
- `versionName` / `versionCode` / **`versionCodeMajor`（64 位版本号）**
- `minSdk` / `targetSdk` / 包名 / 应用名（`android:label`）
- `debuggable` / `extractNativeLibs` / `allowBackup` / `uses-permission` 增删
- 属性缺失时**自动新增**（同步补 `RES_XML_RESOURCE_MAP` 框架属性 ID）
- 字符串池按原编码重建：UTF-16 池保持 UTF-16，不强制转 UTF-8

### 包名联动
一次改包名，四处一起改：AndroidManifest 字符串池 + dex 字符串表（支持任意长度）+ `resources.arsc` 全局池 + `.so` 等长原地替换。

### 条目增删
替换 / 新增 / 删除 APK 内任意条目，或以 `/` 结尾的目录前缀（例如 `lib/armeabi-v7a/`）。

### 体检
包名 / 版本 / SDK / 条目数 / DEX 数 / ABI / 原生库数 / 权限统计、**加固壳识别**、框架识别、签名者证书指纹与应用名（从 `resources.arsc` 解析）。

### 重打包与签名
- 完整 **Zip64**（条目数 > 65535、体积 > 4GB），保留原 extra field
- 对齐体检：4 字节 / 16KB 页对齐报告
- 签名方案 **V1 / V2 / V3 / V4 可分别开关**
- 「不重签」输出为 MT 式：原签名数据原样保留，仅供二次处理

### 界面
- **两套 UI 热切换**：MiuixX（毛玻璃 / 悬浮底栏 / 液态玻璃反射）与 Material 3。切换不重启 Activity、不丢当前页面
- **莫奈取色**（Material You）：6 种主题模式（跟随/浅/深 × 普通/莫奈）+ 种子色 / 配色风格 / 色彩规范，两套 UI 共用同一套取色算法
- **二级页转场**：右侧滑入 + 被覆盖页视差 + 角裁剪 + 预测式返回（用 miuix 官方 nav 库，零手写动画）
- 悬浮顶栏 + 底栏毛玻璃，模糊开关与半径可调
- 底栏三种样式：标准通栏 / 悬浮胶囊 / 液态玻璃·iOS

---

## 🛠️ 技术栈

| 用途 | 技术 |
| :--- | :--- |
| 语言 | Kotlin 2.4.20（AGP 9+ 内置 Kotlin 支持，不用单独声明 kotlin 插件） |
| UI | Jetpack Compose + Miuix 0.9.4-rc01 + Material 3 1.4.0 |
| 二级页导航 | miuix 官方 nav 库（vendor 进工程，见下方说明） |
| 液态玻璃 | kyant backdrop 2.0.0 + shapes 1.2.0（AGSL 着色器） |
| 签名 | apksig（`:core` 自带 `libs/apksig.jar`） |
| 构建 | AGP 9.4.0 · Gradle 9.6.0 · JDK 17 |

### 依赖说明：vendored nav 库

`app/src/main/java/top/yukonga/miuix/kmp/nav/`（27 个文件）来自 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) 的官方导航库，**直接 vendored 进源码**而非引依赖。

原因：它提供了二级页转场与预测式返回的实现（`NavDisplay` + `NavTransitions.MiuixDefault`），但当时该库没有可用的 Maven 发布坐标。vendored 后两套 UI 共用同一套转场，不用自己写动画。

它需要 `kotlinx-serialization`（backStack 持久化）与 JetBrains 版 `lifecycle-*`（条目作用域），这两个依赖已在 `app/build.gradle.kts` 里声明。

---

## 🗂️ 项目结构

```
core/   引擎：AXML / ARSC / DEX / zip 改写、包名联动、重签名（纯 JDK，桌面可跑）
cli/    桌面 CLI：info / verify / list / manifest / patch / selftest
app/    Android 应用：MiuixX + Material 3 双界面
```

app 内部：

```text
app/src/main/java/com/seagull/avt/
├── app/                  界面与状态
│   ├── MainActivity.kt   入口，按 uiStyle 分发到两套 UI
│   ├── Navigation.kt     MiuixX 顶层导航 + 主页四分页
│   ├── MainPages.kt      MiuixX 四个页面（改写 / 条目 / 体检 / 日志）
│   ├── SettingsPage.kt   MiuixX 设置四分页（外观 / 签名 / 输出 / 关于）
│   ├── Md3App.kt         Material 3 界面的宿主与回调实现
│   ├── AvtState.kt       全局状态 + 任务执行（日志有 sink，两套 UI 共用）
│   ├── AvtNav.kt         应用级共享导航栈（跨 UI 切换保页面）
│   └── Route.kt          类型安全路由
├── md3/                  Material 3 界面
│   ├── MainScreen.kt / SettingsScreen.kt / ChangelogScreen.kt
│   ├── components/       AvtCard / AvtButton / AvtField / FoldGroup …
│   ├── bars/ + glass/    底栏三种样式与液态玻璃实现
│   └── theme/            Material You 色板（固定色 + 莫奈取色）
├── ui/                   MiuixX 侧的自造组件
│   ├── FoldGroup.kt      折叠分组（ChevronForward 旋转箭头）
│   ├── TextField.kt      字符串版 TextField 包装
│   └── liquid/           iOS 液态玻璃底栏（AGSL 折射）
└── top/yukonga/…/nav/    vendored miuix 导航库
```

**版本号 / 更新日志的单一真源是根目录 `version.py`**：
`app/src/main/java/com/seagull/avt/app/{AvtVersion,AvtChangelog}.java` 由它生成，**不要手改**。
改版本时改 `version.py`，再执行：

```bash
python3 version.py genjava app/src/main/java/com/seagull/avt/app
```

---

## 🚀 构建

### Android Studio

1. 用 Android Studio 打开项目根目录
2. 等待 Gradle 同步完成（首次需下载依赖）
3. Run ▶ 安装到设备（Android 13+）

### 命令行

> 需要 JDK 17 与 Android SDK（platform 37 + build-tools 37.0.0）

```bash
export JAVA_HOME=<jdk17 路径>
export ANDROID_HOME=<sdk 路径>

./gradlew :app:assembleDebug        # 产物 app/build/outputs/apk/debug/
./gradlew :app:assembleRelease      # 产物 app/build/outputs/apk/release/
./gradlew :cli:installDist          # 桌面 CLI
```

---

## ☁️ 云编译

仓库内置两条 GitHub Actions 工作流：

| 工作流 | 触发 | 产物 |
| :--- | :--- | :--- |
| **Build Debug APK**（`build.yml`） | **仅手动触发** | debug APK（artifact `apk-debug`） |
| **Build Release APK**（`build_release.yml`） | push 到 `main`、打 `v*` 标签、手动触发 | release APK（R8 + 内置密钥签名） |

debug 工作流**刻意不挂 push 触发器**：日常提交走 release 流程就够了，debug 只在需要临时验证界面时手动跑一次，避免每次 push 都白耗一次 CI。

### Release 工作流做了什么

1. 从 `version.py` 读版本号（与产物一致，不会出现两处版本对不上）
2. `assembleRelease` —— R8 代码压缩 + 资源压缩；签名交给 Gradle 的 `signingConfig`
3. 用 `apksigner` 验签，断言「验签通过且至少一个签名者」
   （不要求具体方案：AGP 9 在 `minSdk >= 33` 时会自动跳过 V1/V2、只打 V3，V3 向下兼容 V2 校验）
4. 产物重命名为 `ApkVerTool-v<版本>.apk`
5. **发布 GitHub Release**：打 `v*` 标签或手动触发时，创建/更新 Release 并上传 APK；
   push 到 `main` 的日常构建只上传 artifact，不发版

### 怎么发版

```bash
git tag v2.2.0
git push origin v2.2.0
```

或者到 Actions 页手动跑一次 **Build Release APK**。

---

## 🔐 签名与升级

- 用**固定密钥**签名，因此后续版本可以直接覆盖安装升级
- 升级前提：`versionCode` 必须递增、`applicationId` 不可变更
- ⚠️ 更换签名密钥会导致已安装用户无法覆盖升级（需卸载重装），请勿随意更换

签名的密钥是仓库内的 `signkey.p12`（`storeType=PKCS12`，别名 `avt`），**这不是秘密** —— 它本来就是 App 的功能资产：给用户改过的 APK 做默认重签名，密码在 `core` 的 `SignUtil.java` 里公开可见。

> 也因此 CI 不需要配任何 Secrets，克隆下来就能构建出可直接安装的包。

关于 R8：release 开了 `minifyEnabled` + `shrinkResources`。引擎是纯二进制解析、零反射、零序列化库，可以放心全量裁剪；保留规则见 `app/proguard-rules.pro`。

---

## 📝 说明

- 仅支持**未加固**的 APK；仅适配 Android 13+
- 引擎与 CLI 是纯 JDK 代码，桌面可直接跑 `selftest` 做回归验证
- 输出位置在设置页「输出」里指定（SAF 目录）；指定后产物只留那一份，中转文件导出成功即删
- 打包是否签名由设置页开关决定；改动过的 APK 想装到设备上就必须重新签名
- 应用内修改他人 APK 时用的是内置测试密钥，正式分发请换成自己的密钥

---

## ⚠️ 免责声明

本工具仅供学习、研究与个人修改使用，请勿用于侵权或盗版。修改他人应用请遵守相关法律法规，尊重开发者权益。

---

## 📄 许可证

GNU General Public License v3.0

第三方库版权声明见各依赖自身许可证；vendored 的 miuix nav 库来自 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)。
