package com.seagull.avt.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 更新提示（两套 UI 共用）。
 *
 * v1：只提示 + 跳下载页/直链；不做应用内下载与安装（那是下一步）。
 * 「忽略此版本」写进 prefs，同一个版本不再打扰；强制更新版本不给忽略。
 */
@Composable
fun UpdatePrompt() {
    val info = UpdateChecker.latest ?: return
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { UpdateChecker.dismiss() },
        title = { Text("发现新版本 v" + info.name) },
        text = {
            Column {
                Text(
                    "当前 v" + AvtVersion.NAME + "（code " + AvtVersion.CODE + "）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (info.notes.isNotBlank()) {
                    Text(
                        info.notes.trim().take(800),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (info.isMandatory) {
                    Text(
                        "此版本为必须更新",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                LinkOpener.open(ctx, info.apkUrl ?: info.pageUrl)
                UpdateChecker.dismiss()
            }) { Text(if (info.apkUrl != null) "去下载" else "打开下载页") }
        },
        dismissButton = {
            Row {
                if (!info.isMandatory) {
                    TextButton(onClick = { UpdateChecker.ignore(ctx, info.name) }) { Text("忽略此版本") }
                }
                TextButton(onClick = { UpdateChecker.dismiss() }) { Text("稍后") }
            }
        },
    )
}
