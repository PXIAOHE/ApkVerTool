import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.seagull.apkvertool"
    // compileSdk 必须 ≥ 37：io.github.kyant0:backdrop 的 AAR 元数据写着 minCompileSdk=37
    // （实测 aar-metadata.properties，2.0.0 / 2.0.0-rc01 都是 37）。
    // AGP 8.13.2 只"官方测试到 36"，所以 gradle.properties 里加了
    // android.suppressUnsupportedCompileSdk=37 把那条 warning 摁掉 —— 只是提示，不是错误。
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.seagull.apkvertool"
        // minSdk 33（Android 13）：液态玻璃的"灵魂"边缘折射 lens 走的是 RuntimeShader，
        // 只有 API 33+ 才有；抬到 33 就不用再写 Build.VERSION.SDK_INT 降级分支了。
        minSdk = 33
        targetSdk = 36
        versionCode = 27
        versionName = "2.1.0"

        ndk {
            // 原工程只打 arm64-v8a（androidx.graphics.path 的 so），别的 abi 纯占体积
            abiFilters += "arm64-v8a"
        }
    }

    // 签名沿用工程自带的 signkey.p12（别名 avt / 口令 avtSignKey2026），
    // 与出货包同一把钥匙 —— 升级安装不会因为签名不同被拒。
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
            // 刻意不开 R8：本工程大量走“库只 D8 不 R8”的历史约定，
            // 且 Compose 与自研回调在 R8 下是历史崩溃重灾区（见 README/CHANGELOG 的 1.4.x 系列修复）。
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("avt")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("avt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        // ⚠️ minSdk ≥ 28 时 AGP 默认把 classes*.dex **原样存储**（Stored / 0% 压缩），
        //    好处是运行期能直接 mmap、不用安装时解压；代价是 APK 体积直接翻三倍
        //    （实测 8.7 MB → 24.2 MB）。本工具是「用户之间互传 APK」的形态，
        //    下载体积比首次启动那点收益重要，所以显式退回压缩打包。
        dex {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.version",
                "META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                // 注：AGP 还会在打包阶段直接注入 META-INF/version-control-info.textproto 与
                // META-INF/com/android/build/gradle/app-metadata.properties，它们不走这里的过滤；
                // 两个都是几十字节的构建元信息，运行期无用但也无害，就不再动它了。
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":core"))

    // ⚠️ 不用 compose-bom：见 gradle/libs.versions.toml 顶部说明
    //    （BOM 会把 ui/foundation 抬到 1.12.1，而那个版本要求 AGP 9.1 + compileSdk 37）
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // 液态玻璃：悬浮底栏的「背景采样 + 模糊 + 边缘折射」。
    // 版本 2.0.0（不是最新的 2.0.1）—— 理由见 gradle/libs.versions.toml 顶部。
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
}
