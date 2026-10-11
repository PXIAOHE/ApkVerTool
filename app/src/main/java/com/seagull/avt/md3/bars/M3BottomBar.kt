package com.seagull.avt.md3.bars

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.seagull.avt.md3.AvtTab

/**
 * M3 底栏需要给内容预留的高度。
 *
 * `NavigationBarDefaults.ContainerHeight` 是 80dp；系统导航栏那条 inset 由 `NavigationBar`
 * 自己用 `NavigationBarDefaults.windowInsets` 撑出来，不需要在这里加。
 */
val M3BarReservedHeight = 80.dp

/**
 * Material 3 底栏 **+ 液态玻璃同款模糊**（= `dist/ApkVerTool-v2.0.3-m3.apk` 的底栏形状，
 * 换上 `ui/glass/` 那套 backdrop 采样）。
 *
 * 形状与交互仍然是 M3 的：**通栏贴底**（没有左右外边距、不是胶囊），
 * 图标/文字/角标全部交给 `NavigationBarItem` 默认配色。
 * 只是把原来那块不透明的 `surfaceContainerLowest` 换成了
 * `drawBackdrop(vibrancy → blur)` —— 内容从底栏底下滚过时会被雾化。
 * （顶部那条细分割线已去掉：一条实心硬线压在模糊边上很割裂。）
 *
 * ⚠️ 因此它**不能**再放进 `Scaffold` 的 `bottomBar` 槽：那个槽会给底栏预留空间，
 * 内容不会从它底下经过 → 没东西可采 → 模糊出来是死的。
 * 它和另外两种玻璃样式一样，是 `Box` 上的浮层（见 `ui/AppShell.kt`）。
 *
 * 与两种玻璃胶囊的差别只在**形状**：这里用 `RectangleShape`（通栏），
 * 所以不做 `lens` 边缘折射（那是胶囊才有的观感），只留 `vibrancy` + `blur`。
 *
 * @param backdrop 页面图层（LayerBackdrop），由 `AvtScaffold` 把内容录进去
 */
@Composable
fun M3BottomBar(
    backdrop: Backdrop,
    tabs: List<AvtTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<Int> = emptyList(),
) {
    if (tabs.isEmpty()) return

    val isLightTheme = !isSystemInDarkTheme()
    // 可读性表层（onDrawSurface）。通栏面积大，本来怕糊所以压得比胶囊实；
    // 但实测玻璃感不够，按要求调透一档 —— 比两种玻璃胶囊（0.55 / 0.45）还透。
    // 再想更"水"就继续降这两个数；字开始看不清就加回去。
    val surfaceColor = if (isLightTheme) {
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.38f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                // 通栏：直角矩形，不是胶囊。
                shape = { RectangleShape },
                effects = {
                    vibrancy()              // 提饱和，玻璃才有「通透感」
                    blur(8.dp.toPx())       // 背景雾化（与玻璃胶囊同档）
                },
                // ⚠️ 必须显式关掉边缘高光。
                //
                // drawBackdrop 的 highlight 参数有默认值（Highlight.Default，45° 边缘高光）。
                // 玻璃胶囊是圆角胶囊，高光顺着弧线走、看不出来；
                // 但这里是**通栏直角矩形**，高光正好在顶边拉成一条 **1 像素亮线** ——
                // 之前一直被当成"分割线没删干净"，其实是这个。
                highlight = { Highlight.Default.copy(alpha = 0f) },
                onDrawSurface = {
                    // 顶边渐隐。
                    //
                    // 实心 rect 的顶边和上方页面之间会撞出一条**水平硬边** ——
                    // 那条边看起来跟分割线一模一样（之前有人就是这么误判的），
                    // 但它不是 Divider，是「表层突然出现」造成的明度台阶。
                    // 让 alpha 从 0 渐变到 surfaceColor，台阶就没了。
                    val fade = 18.dp.toPx().coerceAtMost(size.height * 0.5f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to surfaceColor.copy(alpha = 0f),
                            fade / size.height to surfaceColor,
                            startY = 0f,
                            endY = size.height,
                        ),
                    )
                },
            ),
    ) {
        NavigationBar(
            // 关键：底栏自己的底色必须是透明的，否则会把下面的 backdrop 采样盖掉。
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            tabs.forEachIndexed { index, tab ->
                val count = badges.getOrElse(index) { 0 }
                NavigationBarItem(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    icon = {
                        val drawIcon: @Composable () -> Unit = {
                            Icon(imageVector = tab.icon, contentDescription = tab.label)
                        }
                        if (count > 0) {
                            BadgedBox(
                                badge = {
                                    Badge { Text(if (count > 99) "99+" else count.toString()) }
                                },
                            ) { drawIcon() }
                        } else {
                            drawIcon()
                        }
                    },
                    label = {
                        Text(
                            text = tab.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}
