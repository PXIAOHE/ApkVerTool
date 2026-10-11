plugins {
    id("com.android.application") version "9.4.0" apply false
    // AGP 9+ 内置 Kotlin，无需 kotlin.android 插件
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
}
