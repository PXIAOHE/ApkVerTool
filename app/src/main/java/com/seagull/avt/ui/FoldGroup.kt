// 折叠分组（MD3 版 FoldGroup 的 miuix 对齐实现）：
// 标题行可点，右侧是旋转箭头指示（展开时朝上、收起时朝下），内容 AnimatedVisibility 展开。
// 视觉走 miuix Card 语言（surfaceContainer 底 + dividerLine 分隔）。
package com.seagull.avt.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixFoldGroup(
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // ChevronForward 默认朝右（收起）；展开时顺时针转 90° → 朝下
    val rotation by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "foldArrow",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // 标题行：整行可点
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                // 水平 16dp 与同 Card 内的 preference 行（BasicComponent InsideMargin）对齐
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MiuixTheme.textStyles.main,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = if (open) "收起" else "展开",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
        // 内容：动画展开收起
        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(content = content)
        }
        Spacer(Modifier.height(4.dp))
    }
}
