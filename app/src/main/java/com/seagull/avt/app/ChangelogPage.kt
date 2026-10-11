// 更新日志页：一个版本一页，HorizontalPager 左右滑动翻页，页码写进标题栏。
// 内容来自 version.py 单一真源生成的 AvtChangelog（老数据源不动）。
package com.seagull.avt.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop

@Composable
fun ChangelogPage(onBack: () -> Unit) {
    val count = AvtChangelog.COUNT
    val pagerState = rememberPagerState(pageCount = { count })
    var page by remember { mutableIntStateOf(0) }

    val backdrop = rememberBlurBackdrop()

    androidx.compose.runtime.LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
            .collect { page = it }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        topBar = {
            // 顶栏模糊只用于一级界面（主页）；二级界面用实色顶栏
            SmallTopAppBar(
                title = "更新日志  ${page + 1}/$count",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { idx ->
                val row = AvtChangelog.ENTRIES[idx]
                ChangelogEntry(
                    version = row[0],
                    date = row[1],
                    items = row.drop(2),
                    latest = idx == 0,
                    pagePadding = padding,
                )
            }
        }
    }
}

@Composable
private fun ChangelogEntry(
    version: String,
    date: String,
    items: List<String>,
    latest: Boolean,
    pagePadding: androidx.compose.foundation.layout.PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = pagePadding.calculateTopPadding(),
                bottom = pagePadding.calculateBottomPadding(),
            ),
    ) {
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "v$version",
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                if (latest) {
                    Text(
                        "当前版本",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .background(
                                color = MiuixTheme.colorScheme.primary,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                date,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
        }

        SmallTitle(text = "更新内容（${items.size} 项）")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items.forEach { item ->
                    Row {
                        Text(
                            "•",
                            color = MiuixTheme.colorScheme.primary,
                            style = MiuixTheme.textStyles.main,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(item, style = MiuixTheme.textStyles.main)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
