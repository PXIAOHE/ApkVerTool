package com.seagull.avt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/*
 * 主题入口。
 *
 * 深浅切换沿用旧机制：Activity 在 attachBaseContext 里按 theme_mode 改写 uiMode，
 * 所以这里的 isSystemInDarkTheme() 拿到的是“已经被人为决定过”的结果 —— 不用另开一套状态。
 */
@Composable
fun AvtTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AvtDarkColors else AvtLightColors,
        content = content,
    )
}
