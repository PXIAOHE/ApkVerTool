package com.seagull.avt.md3.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.seagull.avt.app.ThemeState
import com.seagull.avt.app.keyColorFor
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/*
 * 主题入口。
 *
 * 两套色源：
 *  1. 固定色板（AvtLightColors / AvtDarkColors）—— 默认，清新白底浅蓝。
 *  2. 莫奈取色 —— 当 ThemeState 选中「莫奈 · *」时，用 miuix 的 ThemeController
 *     按 种子色/配色风格/色彩规范 推导出一整套 miuix Colors，再映射到 material3 的
 *     ColorScheme。这样 MD3 界面也能跟随莫奈取色（与 MiuixX 套同一套算法与种子色）。
 */

@Composable
fun AvtTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (ThemeState.isMonet) {
        monetScheme() ?: fallback(darkTheme)
    } else {
        fallback(darkTheme)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

private fun fallback(dark: Boolean): ColorScheme =
    if (dark) AvtDarkColors else AvtLightColors

/**
 * 用 miuix 的取色算法算一套莫奈配色，再映射为 material3 ColorScheme。
 * 复用 ThemeController.currentColors()（@Composable），与 MiuixX 套的莫奈结果一致。
 */
@Composable
private fun monetScheme(): ColorScheme? {
    val mode = when (ThemeState.mode) {
        ThemeState.MODE_MONET_LIGHT -> ColorSchemeMode.MonetLight
        ThemeState.MODE_MONET_DARK -> ColorSchemeMode.MonetDark
        else -> ColorSchemeMode.MonetSystem
    }
    val keyColor = keyColorFor(ThemeState.seedIndex)
    val spec = ThemeColorSpec.entries.getOrNull(ThemeState.colorSpec) ?: ThemeColorSpec.Spec2021
    val style = ThemePaletteStyle.entries.getOrNull(ThemeState.paletteStyle)
        ?: ThemePaletteStyle.TonalSpot

    val controller = remember(mode, keyColor, spec, style) {
        ThemeController(
            colorSchemeMode = mode,
            keyColor = keyColor,
            colorSpec = spec,
            paletteStyle = style,
        )
    }
    val c: Colors = controller.currentColors()

    return remember(c) {
        val base = if (isDark(c)) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiaryContainer,
            onTertiary = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onBackground,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceVariant,
            onSurfaceVariant = c.onSurfaceSecondary,
            outline = c.outline,
            outlineVariant = c.dividerLine,
            error = c.error,
            surfaceContainer = c.surfaceContainer,
            surfaceContainerHigh = c.surfaceContainerHigh,
            surfaceContainerHighest = c.surfaceContainerHighest,
            surfaceContainerLow = c.surfaceContainer,
            surfaceContainerLowest = c.surface,
        )
    }
}

/** miuix Colors 无直接 isDark，按背景亮度判断。 */
private fun isDark(c: Colors): Boolean {
    val lum = 0.2126f * c.background.red + 0.7152f * c.background.green + 0.0722f * c.background.blue
    return lum < 0.5f
}
