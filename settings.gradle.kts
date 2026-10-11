pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ApkVerTool"
include(":core")   // APK 引擎（纯 JDK，桌面可测）
include(":cli")    // 桌面 CLI：info / verify / selftest（回归验证器）
include(":app")    // Android 应用（Compose UI）
