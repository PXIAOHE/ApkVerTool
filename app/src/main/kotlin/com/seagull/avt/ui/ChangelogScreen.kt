package com.seagull.avt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seagull.avt.app.AvtChangelog
import com.seagull.avt.ui.components.AvtCard
import com.seagull.avt.ui.components.BulletLine
import com.seagull.avt.ui.components.Note
import com.seagull.avt.ui.components.PageColumn
import com.seagull.avt.ui.components.SectionLabel
import kotlinx.coroutines.launch

/**
 * 更新日志页：一个版本一页，左右滑动翻页，不建底栏 —— 页码写在顶栏标题上。
 * 内容来自 version.py 自动生成的 AvtChangelog。
 */
@Composable
fun ChangelogScreen(host: ChangelogHost) {
    val count = AvtChangelog.COUNT
    val pagerState = rememberPagerState(pageCount = { if (count <= 0) 1 else count })
    val scope = rememberCoroutineScope()

    AvtScaffold(
        tabs = emptyList(),
        pagerState = pagerState,
        title = { page ->
            if (count <= 0) "更新日志" else "更新日志  ${page + 1}/$count"
        },
        showBottomBar = false,
        onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
        topBarActions = {
            TextButton(onClick = { host.closeChangelog() }) { Text("完成") }
        },
        pageContent = { page, inner ->
            val row = AvtChangelog.ENTRIES.getOrNull(page)
            if (row == null) {
                PageColumn(inner) {
                    item { AvtCard { Note("暂无更新日志。") } }
                }
            } else {
                VersionPage(row = row, latest = page == 0, inner = inner)
            }
        },
    )
}

@Composable
private fun VersionPage(row: Array<String>, latest: Boolean, inner: PaddingValues) {
    PageColumn(inner) {
        item {
            AvtCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "v" + row[0],
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (latest) {
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = "当前版本",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                Text(
                    text = row[1],
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            AvtCard {
                SectionLabel("更新内容（${row.size - 2} 项）")
                for (i in 2 until row.size) {
                    BulletLine(row[i])
                }
            }
        }
    }
}
