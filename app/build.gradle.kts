plugins {
    // AGP 9+ 内置 Kotlin 支持，不要再声明 org.jetbrains.kotlin.android
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.seagull.avt"
    // miuix 0.9.4-rc01 / Compose 1.12.0-rc01 的 AAR 元数据要求 compileSdk ≥ 37
    compileSdk = 37

    defaultConfig {
        applicationId = "com.seagull.apkvertool"
        // miuix-blur 0.9.4-rc01 的 manifest 硬性要求 minSdk ≥ 33（RuntimeShader 等 API），
        // 比 overrideLibrary 强行压低安全 —— 不会在老设备上运行时崩
        minSdk = 33
        targetSdk = 36
        // 与 version.py 单一真源保持一致（2.0.3 / 26）
        // 相对上游 v2.1.0 递增（上游是 27 / 2.1.0），避免版本号倒退
        versionCode = 28
        versionName = "2.2.0"
    }

    // 签名配置：照抄原版 ApkVerTool —— 直接用仓库内置的 signkey.p12
    // （该密钥本就是 App 的功能资产：给用户改过的 APK 做默认重签名，
    //   密码/别名公开在 core/SignUtil.java 里，不是需要保密的发布密钥）。
    signingConfigs {
        create("avt") {
            storeFile = rootProject.file("signkey.p12")
            storeType = "PKCS12"
            storePassword = "avtSignKey2026"
            keyAlias = "avt"
            keyPassword = "avtSignKey2026"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            // R8 代码压缩 + 资源压缩：引擎零反射（纯二进制解析），可安全全量裁剪
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 与原版一致：release 用内置密钥签名，产出可直接安装的包
            signingConfig = signingConfigs.getByName("avt")
        }
        debug {
            // 与原版一致：debug 也用同一把内置密钥（便于覆盖安装验证）
            signingConfig = signingConfigs.getByName("avt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "META-INF/*"
        }
    }
}

dependencies {
    // 引擎走上游既有的 :core 模块（纯 JDK，自带 apksig）
    implementation(project(":core"))

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Miuix - Xiaomi HyperOS style UI（与 ApkEditorMiuix 同版本）
    implementation("top.yukonga.miuix.kmp:miuix-core-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4-rc01")
    // vendored nav 库（miuix 官方二级页转场动画）依赖 squircle 做角裁剪
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:0.9.4-rc01")


    // vendored nav 库的依赖：backStack 持久化用 serialization，条目作用域用 lifecycle
    // （与模板 ApkEditorMiuix 同版本组合；JetBrains lifecycle 包名仍是 androidx.lifecycle）
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // ── MD3 第二套 UI：material3 真组件（切换到 MD3 风格时全量使用）──
    // material3 1.4.0：minCompileSdk 35 / minAGP 8.6，与图内 compose 1.12.0-rc01 同代兼容
    implementation("androidx.compose.material3:material3:1.4.0")
    // 图标 core（49 个基础图标）；MD3 版另有一份自抽 AvtIcons.kt，刻意不引 extended 避免 dex 膨胀
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    // 液态玻璃底栏（MD3 版 GlassBottomBar 用）：backdrop 2.0.0 依赖 compose 1.11.x，
    // 与本图 1.12.0-rc01 前向兼容；其 AAR minCompileSdk=37 与本工程一致
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.0")
}
