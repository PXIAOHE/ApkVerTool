// ApkVerTool —— Gradle 工程设置
// 仓库：阿里云镜像优先（本机到 repo1.maven.org 基本不可用），google()/mavenCentral() 兜底。
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
    }
}

rootProject.name = "ApkVerTool"

include(":core")   // APK 引擎（纯 JDK，桌面可测）
include(":cli")    // 桌面 CLI：info / verify / selftest（回归验证器）
include(":app")    // Android 应用（Compose Material 3 UI）
