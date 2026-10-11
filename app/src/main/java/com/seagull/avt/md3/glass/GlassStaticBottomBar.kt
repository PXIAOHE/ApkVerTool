package com.seagull.avt.md3.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.seagull.avt.app.UiConfigState
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.seagull.avt.md3.AvtTab

/**
 * 液态玻璃底栏 · **静态版**（= `dist/ApkVerTool-v2.0.3-glass.apk`）。
 *
 * 这是 §11 那一版，后来被 §13 的「按压缩放 + 拖拽」版（[GlassBottomBar]）取代。
 * 两者共用同一套外观常量（[GlassBarHeight] / [GlassBarMarginHorizontal] / [GlassBarMarginBottom] /
 * [GlassBarReservedHeight] / [GlassBarInset]）与同一个 `swallowDrags()`，
 * 差别只在**交互与选中态**：
 *
 * | 项 | 本版（静态） | [GlassBottomBar]（拖拽） |
 * | :--- | :--- | :--- |
 * | 选中态 | **常显**的浅蓝胶囊（`primaryContainer @0.92`）+ 边缘折射 | 静止时只有 10% 压暗 + 被放大的蓝图标 |
 * | 按压 | 图标 scale → 0.84（按下去变小） | 整条胶囊横向鼓 16dp，图标放大 1.2× |
 * | 拖拽 | 不支持；只能横滑页面换页 | 按住胶囊可拖到别的 tab，松手吸附 |
 * | 跟手 | 指示器跟 `pagerPosition` 跟手横滑 | 胶囊既跟手指也跟页面 |
 *
 * 所以本版**不需要** `DampedDragAnimation` / `InteractiveHighlight` / `DragGestureInspector` 三件套，
 * 位置直接用 `pagerPosition` 驱动一个 `graphicsLayer.translationX` 即可。
 *
 * 图层顺序（从下往上）：① 玻璃面 → ② 选中指示器 → ③ 图标/文字。
 * 指示器必须夹在中间，否则要么被玻璃面的 `onDrawSurface` 冲淡、要么盖住图标。
 */
@Composable
fun GlassStaticBottomBar(
    backdrop: Backdrop,
    tabs: List<AvtTab>,
    selectedIndex: () -> Int,
    pagerPosition: () -> Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<Int> = emptyList(),
) {
    if (tabs.isEmpty()) return
    val tabsCount = tabs.size

    // 深浅看主题（不是系统）：系统深色 + 主题选浅色时，原来会取暗色表层 → 糊成黑
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    // 可读性表层（onDrawSurface）。官方原话：必须在「好看」和「看得清」之间权衡。
    val containerColor = if (isLightTheme) {
        Color.White.copy(alpha = 0.55f)
    } else {
        Color(0xFF151A1F).copy(alpha = 0.45f)
    }
    // 选中胶囊：常显，浅蓝、接近不透明 —— 这是本版「一眼能看出选中谁」的全部来源。
    val indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)

    // lambda 每次重组都会换实例，用 rememberUpdatedState 兜住，避免被当成 effect 的 key。
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    val latestPagerPosition by rememberUpdatedState(pagerPosition)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    BoxWithConstraints(
        // 底栏和 Pager 在底栏区域重叠命中，不吃掉拖动的话拖底栏会顺带滚页面。
        modifier = modifier.swallowDrags(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        // ⚠️ 必须按**内容区**算宽度（外框减掉两侧 inset），按外框算会让末项顶到圆角上。
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - (GlassBarInset * 2).toPx()) / tabsCount
        }

        // ① 玻璃面：只画面，不放内容。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(GlassBarHeight)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()                      // 提饱和，玻璃才有「通透感」
                        // 跟设置里的开关/半径走
                        blur(if (UiConfigState.enableBlur) 10.dp.toPx() else 0f)
                        lens(20.dp.toPx(), 26.dp.toPx()) // 边缘折射
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow(alpha = 1f) },
                    onDrawSurface = { drawRect(containerColor) },
                ),
        ) {
            // 只画面，不放内容 —— 内容在 ③ 那层，保证指示器夹在中间。
        }

        // ② 选中指示器：浅蓝胶囊 + 一层「放大镜」折射，位置跟 pager 走。
        Box(
            modifier = Modifier
                .padding(horizontal = GlassBarInset)
                .graphicsLayer {
                    translationX = if (isLtr) {
                        latestPagerPosition() * tabWidth
                    } else {
                        size.width - (latestPagerPosition() + 1f) * tabWidth
                    }
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        lens(9.dp.toPx(), 11.dp.toPx(), chromaticAberration = true)
                    },
                    onDrawSurface = { drawRect(indicatorColor) },
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount),
        ) {
            // 纯绘制层，无内容。
        }

        // ③ 内容：图标 + 文字，画在最上层。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(GlassBarHeight)
                .padding(GlassBarInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                StaticTabItem(
                    tab = tab,
                    count = badges.getOrElse(index) { 0 },
                    selected = index == latestSelectedIndex(),
                    onClick = { latestOnSelect(index) },
                )
            }
        }
    }
}

/**
 * 一格 tab。按下时整体缩到 0.84（旧版观感），选中项用主题色 + 加粗。
 *
 * 角标不跟着染色 —— 被染成一坨蓝之后数字看不清（这一点与 [GlassBottomBar] 的处理一致）。
 */
@Composable
private fun RowScope.StaticTabItem(
    tab: AvtTab,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "staticTabScale",
    )

    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .clip(Capsule())
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val drawIcon: @Composable () -> Unit = {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        if (count > 0) {
            BadgedBox(
                badge = { Badge { Text(if (count > 99) "99+" else count.toString()) } },
            ) { drawIcon() }
        } else {
            drawIcon()
        }
        Text(
            text = tab.label,
            fontSize = 11.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
        )
    }
}
