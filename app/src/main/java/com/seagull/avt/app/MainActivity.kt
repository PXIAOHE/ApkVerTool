// ApkVerTool — Compose 入口。
// 界面全量迁移到 miuix（与 ApkEditorMiuix 同一套视觉语言）：
// MiuixTheme + Scaffold + SmallTopAppBar + NavigationBar 底栏 + 四分页 HorizontalPager。
// core/ 引擎保持纯 Java 不动。
package com.seagull.avt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.load(this)
        UiConfigState.load(this)
        enableEdgeToEdge()
        setContent {
            AvtApp()
        }
    }
}

@Composable
fun AvtApp() {
    // uiStyle==1 → MD3 整套界面（material3 真组件 + AvtTheme）；
    // uiStyle==0 → MiuixX（miuix 组件 + 毛玻璃）。
    // 切换是纯热切：uiStyle 变化直接重组换套，不 recreate、不重置导航栈。
    if (UiConfigState.uiStyle == 1) {
        Md3App()
        return
    }
    val dark = when (ThemeState.mode) {
        ThemeState.MODE_LIGHT, ThemeState.MODE_MONET_LIGHT -> false
        ThemeState.MODE_DARK, ThemeState.MODE_MONET_DARK -> true
        else -> isSystemInDarkTheme()
    }
    // 莫奈（Material You 动态取色）：走 ThemeController 的 Monet 模式 + 种子色/风格/规范
    val monetColors = remember(ThemeState.mode, ThemeState.seedIndex, ThemeState.paletteStyle, ThemeState.colorSpec) {
        if (!ThemeState.isMonet) {
            null
        } else {
            val keyColor = keyColorFor(ThemeState.seedIndex)
            val spec = top.yukonga.miuix.kmp.theme.ThemeColorSpec.entries
                .getOrNull(ThemeState.colorSpec) ?: top.yukonga.miuix.kmp.theme.ThemeColorSpec.Spec2021
            val style = top.yukonga.miuix.kmp.theme.ThemePaletteStyle.entries
                .getOrNull(ThemeState.paletteStyle) ?: top.yukonga.miuix.kmp.theme.ThemePaletteStyle.TonalSpot
            val mode = when (ThemeState.mode) {
                ThemeState.MODE_MONET_LIGHT -> ColorSchemeMode.MonetLight
                ThemeState.MODE_MONET_DARK -> ColorSchemeMode.MonetDark
                else -> ColorSchemeMode.MonetSystem
            }
            ThemeController(
                colorSchemeMode = mode,
                keyColor = keyColor,
                colorSpec = spec,
                paletteStyle = style,
            )
        }
    }
    val controller = remember(dark, monetColors) {
        monetColors ?: when {
            dark -> ThemeController(
                colorSchemeMode = ColorSchemeMode.Dark,
                darkColors = darkColorScheme(
                    // Miuix 深色：旧版蓝调
                    background = Color(0xFF0B1220),
                    surface = Color(0xFF0B1220),
                    surfaceContainer = Color(0xFF161F2E),
                    surfaceContainerHigh = Color(0xFF161F2E),
                    surfaceVariant = Color(0xFF161F2E),
                    primary = Color(0xFF818CF8),
                    onSurface = Color(0xFFE5E7EB),
                    onBackground = Color(0xFFE5E7EB),
                    onSurfaceContainer = Color(0xFFE5E7EB),
                    onSurfaceSecondary = Color(0xFF94A3B8),
                    onSurfaceVariantSummary = Color(0xFF94A3B8),
                    dividerLine = Color(0xFF293546),
                    error = Color(0xFFEF4444),
                ),
            )
            else -> ThemeController(
                colorSchemeMode = ColorSchemeMode.Light,
                lightColors = lightColorScheme(
                    // Miuix 浅色
                    background = Color(0xFFF2F4F8),
                    surface = Color(0xFFF2F4F8),
                    surfaceContainer = Color(0xFFFFFFFF),
                    surfaceContainerHigh = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFFFFFFF),
                    primary = Color(0xFF4F46E5),
                    onSurface = Color(0xFF111827),
                    onBackground = Color(0xFF111827),
                    onSurfaceContainer = Color(0xFF111827),
                    onSurfaceSecondary = Color(0xFF6B7280),
                    onSurfaceVariantSummary = Color(0xFF6B7280),
                    dividerLine = Color(0xFFE5E7EB),
                    error = Color(0xFFDC2626),
                ),
            )
        }
    }
    // 不用 key(...)：主题变化只让 MiuixTheme 重组换色，
    // 不能重建子树 —— 否则 AvtNavigation 的 backStack（remember）被重置，页面弹回主页。
    MiuixTheme(controller = controller) {
        // 不做 imePadding：输入法弹出时整体平移会把底栏顶上去，直接让输入框浮在栏上
        Box(Modifier.fillMaxSize()) {
            AvtNavigation(AvtNav.backStack)
        }
    }
}
