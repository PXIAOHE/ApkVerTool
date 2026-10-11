package com.seagull.avt.md3.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * 固定配色：白色为主 + 浅蓝强调的清新主题（不走 Material You 动态取色）。
 *
 * 深浅两套槽位一一对应，取值规则遵循 Material 3 的 tonal palette 关系：
 *   primary 40 / container 90（浅色）  ←→  primary 80 / container 30（深色）
 *   surface 白底 + surfaceContainer* 承担卡片分层（取代旧代码里的硬编码 cCard/cLogCard）
 */

val AvtLightColors = lightColorScheme(
    primary = Color(0xFF2E7CC4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E7FB),
    onPrimaryContainer = Color(0xFF0A2A45),
    inversePrimary = Color(0xFF9CCAF0),

    secondary = Color(0xFF51677C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E4F0),
    onSecondaryContainer = Color(0xFF0D1F2E),

    tertiary = Color(0xFF2C8C99),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCE9EC),
    onTertiaryContainer = Color(0xFF00363C),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF7FAFD),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDEE3EA),
    onSurfaceVariant = Color(0xFF42474E),
    surfaceTint = Color(0xFF2E7CC4),
    inverseSurface = Color(0xFF2E3133),
    inverseOnSurface = Color(0xFFF0F1F3),

    outline = Color(0xFF72787F),
    outlineVariant = Color(0xFFC2C7CE),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD8DBDF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F8FC),
    surfaceContainer = Color(0xFFEDF3F9),
    surfaceContainerHigh = Color(0xFFE7EDF4),
    surfaceContainerHighest = Color(0xFFE1E8EF),
)

val AvtDarkColors = darkColorScheme(
    primary = Color(0xFF9CCAF0),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF0F4C77),
    onPrimaryContainer = Color(0xFFD3E7FB),
    inversePrimary = Color(0xFF2E7CC4),

    secondary = Color(0xFFB9CBDC),
    onSecondary = Color(0xFF24384A),
    secondaryContainer = Color(0xFF3A4E60),
    onSecondaryContainer = Color(0xFFD6E4F0),

    tertiary = Color(0xFF9FD3D8),
    onTertiary = Color(0xFF00363C),
    tertiaryContainer = Color(0xFF1A4E55),
    onTertiaryContainer = Color(0xFFCCE9EC),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0F1418),
    onBackground = Color(0xFFE1E2E5),
    surface = Color(0xFF0F1418),
    onSurface = Color(0xFFE1E2E5),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CE),
    surfaceTint = Color(0xFF9CCAF0),
    inverseSurface = Color(0xFFE1E2E5),
    inverseOnSurface = Color(0xFF2E3133),

    outline = Color(0xFF8C9198),
    outlineVariant = Color(0xFF42474E),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFF353B3F),
    surfaceDim = Color(0xFF0F1418),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF171C20),
    surfaceContainer = Color(0xFF1B2024),
    surfaceContainerHigh = Color(0xFF262B2F),
    surfaceContainerHighest = Color(0xFF31363A),
)
