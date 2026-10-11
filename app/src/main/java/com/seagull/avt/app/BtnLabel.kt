package com.seagull.avt.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 按钮/行的「图标 + 文字」标签：统一用 MD3 那套图标（以前是文字里的 emoji）。
 *
 * ⚠️ 这些 Ico* 矢量自带 fill = SolidColor(Black)，必须显式给 tint ——
 * miuix 的 Button 不提供 LocalContentColor，不给就渲染成纯黑，和旁边的文字颜色打架。
 */
@Composable
fun BtnLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = MiuixTheme.colorScheme.onSurfaceContainer)
    }
}
