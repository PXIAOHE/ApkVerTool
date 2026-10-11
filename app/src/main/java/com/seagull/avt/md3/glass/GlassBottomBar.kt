package com.seagull.avt.md3.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.seagull.avt.md3.AvtTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

// ─────────────────────────────────────────────────────────────────────────────
// 布局常量：外部（内容底部留白、Snackbar 让位）也要用，所以放顶层。
// ─────────────────────────────────────────────────────────────────────────────

/** 悬浮玻璃底栏的高度（外层胶囊；按下时会再鼓出来一点）。 */
val GlassBarHeight = 64.dp

/** 底栏左右外边距（浮起来，不贴屏幕边）。 */
val GlassBarMarginHorizontal = 24.dp

/** 底栏距离屏幕底（导航栏之上）的间距。 */
val GlassBarMarginBottom = 12.dp

/** 内容需要为浮层额外预留的高度 = 底栏高度 + 上下两个外边距。 */
val GlassBarReservedHeight = GlassBarHeight + GlassBarMarginBottom * 2

/**
 * 玻璃胶囊内部围绕内容一圈的留白（官方值 4dp）。
 *
 * 它同时决定 [tabWidth] 的算法：可见的那排 Row 是「64dp 高 + 四周 4dp padding」，
 * 所以内容区宽度 = 外框宽度 - 2×4dp，每格 = 内容区宽度 / 格数。
 * （指示器/胶囊都活在这个内缩过的坐标系里，算错就会顶出胶囊边界。）
 */
internal val GlassBarInset = 4.dp

/** 「按压」时拖拽胶囊的相对高度：56dp → 78dp（官方值，会鼓出 64dp 的外层胶囊）。 */
private const val PressedScale = 78f / 56f

/** 「按压」时录制层里那份图标/文字整体放大到 1.2×。 */
private const val AccentLayerScale = 1.2f

/** 录制层（染色副本）的缩放进度，由按压进度驱动。 */
private val LocalGlassTabScale = staticCompositionLocalOf { { 1f } }

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

// ─────────────────────────────────────────────────────────────────────────────
// 底栏本体
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 液态玻璃「悬浮」底栏 —— **按压缩放 + 拖拽切换 Tab** 版。
 *
 * 结构照搬官方 demo 的 `LiquidBottomTabs`（`com.kyant.backdrop.catalog.components`）：
 *
 * ```
 * BoxWithConstraints
 *  ├─ Row ①  外层胶囊（64dp）。可见的图标/文字在这里，一律中性色。
 *  ├─ Row ②  同尺寸的"染色副本"：.alpha(0f) 看不见，但 layerBackdrop 把它录进专属图层，
 *  │         内容是 accent 色的图标/文字，按下时整体放大到 1.2×。
 *  └─ Box ③  拖拽胶囊（56dp × 1/格数）。它采样 rememberCombinedBackdrop(页面, 染色副本)，
 *            所以"只有胶囊里能看到被放大的蓝色图标"——按压缩放 + 拖拽跟手的观感都来自这里。
 * ```
 *
 * 三个联动：
 * ① 拖胶囊 → [DampedDragAnimation] 自己跟手（不 consume 事件，下层不受影响）；
 *    松手吸附到最近一格 → [onSelect] → 交给 pager 翻页；
 * ② 外部页号（内容横滑 / 点 tab）→ 反推胶囊位置，横滑时胶囊跟手；
 * ③ 底栏自己拖拽和"等 pager 追上来"期间，忽略 ②，避免两边互拽。
 *
 * @param backdrop 页面图层（LayerBackdrop）
 * @param tabs 底栏项（文案 + 图标）
 * @param selectedIndex 当前页号（读 pagerState.currentPage）
 * @param pagerPosition 当前页位置的小数表示（currentPage + offsetFraction），用于跟手
 * @param onSelect 选中某项（要负责让 pager 翻到这一页）
 * @param badges 每项的角标数字，0 表示不显示
 */
@Composable
fun GlassBottomBar(
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
    // 可读性表层（onDrawSurface）。官方原话：必须在"好看"和"看得清"之间权衡。
    val containerColor = if (isLightTheme) {
        Color.White.copy(alpha = 0.55f)
    } else {
        Color(0xFF151A1F).copy(alpha = 0.45f)
    }

    // 外部传的是 lambda，每次重组都会换实例。用 rememberUpdatedState 兜住，
    // 免得把 lambda 当成 effect 的 key —— 那会让「拖动中 effect 被重启 → 胶囊被拽回原页」。
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    val latestPagerPosition by rememberUpdatedState(pagerPosition)
    val latestOnSelect by rememberUpdatedState(onSelect)

    // 「染色副本」的专属图层。可见那排是中性色，只有胶囊采样时才会看到染色那排，
    // 于是"只有被选中的那个 tab 是蓝的"。
    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier = modifier.swallowDrags(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - (GlassBarInset * 2).toPx()) / tabsCount
        }

        // 拖拽时整条胶囊轻微横向偏移，松手弹回 —— "拎起来"的那点惯性。
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).coerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()

        var currentIndex by remember { mutableIntStateOf(latestSelectedIndex()) }
        /** 手指是否正按在胶囊上拖。 */
        var barDragging by remember { mutableStateOf(false) }
        /** >= 0 表示"正在等 pager 走到这一页"，期间不让 pager 的位置反向驱动胶囊。 */
        var pagerGate by remember { mutableIntStateOf(-1) }

        val dampedDragAnimation = remember(animationScope, tabWidth) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = latestSelectedIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = PressedScale,
                onDragStarted = {
                    barDragging = true
                    pagerGate = -1
                },
                onDragStopped = {
                    val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                    barDragging = false
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
            )
        }

        // 按下时跟着手指走的那团白光。
        val interactiveHighlight = remember(animationScope, tabWidth, isLtr) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f,
                    )
                },
            )
        }

        // ① 可见的玻璃胶囊 + 中性色 tab
        Row(
            modifier = Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()                      // 提饱和，玻璃才有"通透感"
                        blur(if (UiConfigState.enableBlur) 8.dp.toPx() else 0f)               // 背景雾化
                        lens(24.dp.toPx(), 24.dp.toPx()) // 边缘折射
                    },
                    layerBlock = {
                        // 按下时整条胶囊横向"鼓"出来一点（16dp 摊到两侧）
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(interactiveHighlight.modifier)
                .height(GlassBarHeight)
                .fillMaxWidth()
                .padding(GlassBarInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassTabs(tabs = tabs, badges = badges, accent = false, onSelect = latestOnSelect)
        }

        // ② 染色副本：看不见（alpha 0），但会被录进 tabsBackdrop 供胶囊采样
        //    lambda 要 remember 住：staticCompositionLocalOf 一换值就会重组整棵子树，
        //    每次重组新建 lambda 会把下面 4 个 tab 一起重组掉。
        val accentScale: () -> Float = remember(dampedDragAnimation) {
            { lerp(1f, AccentLayerScale, dampedDragAnimation.pressProgress) }
        }
        CompositionLocalProvider(LocalGlassTabScale provides accentScale) {
            Row(
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(if (UiConfigState.enableBlur) 8.dp.toPx() else 0f)
                            lens(24.dp.toPx() * progress, 24.dp.toPx() * progress)
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = GlassBarInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassTabs(tabs = tabs, badges = badges, accent = true, onSelect = latestOnSelect)
            }
        }

        // ③ 拖拽胶囊
        Box(
            modifier = Modifier
                .padding(horizontal = GlassBarInset)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        // 静止时透镜为 0（不放大），按下才开始"放大镜"效果
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true,     // 边缘一点点色散，更"真"
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                    },
                    shadow = {
                        Shadow(alpha = dampedDragAnimation.pressProgress)
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 8.dp * dampedDragAnimation.pressProgress,
                            alpha = dampedDragAnimation.pressProgress,
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        // 甩得快的时候横向拉伸、纵向压扁一点，做出"液体"的惯性
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        // 静止时一层 10% 的压暗，按下时让位给"放大镜"
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f) else Color.White.copy(0.1f),
                            alpha = 1f - progress,
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount),
        ) {
            // 胶囊自己不带内容：它只是"采样窗"，看到的画面来自 ①②两层
        }

        // ── 与 pager 的三条联动 ──────────────────────────────────────────────

        // 外部页号（内容横滑 / 点 tab）→ 内部 currentIndex
        LaunchedEffect(Unit) {
            snapshotFlow { latestSelectedIndex() }
                .collectLatest { index -> currentIndex = index }
        }

        // 内部 currentIndex 变化 → 驱动 pager。
        // 只在「内部值 ≠ 外部值」时动手：那一定是底栏自己拖出来的；
        // 内容横滑引起的翻页会走下面那条"跟随"路径，这里再叠动画就会打架。
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    if (index != latestSelectedIndex()) {
                        // 先把"跟随 pager"关掉，等 pager 真的走到目标页再放开，
                        // 否则 pager 的补间动画会把胶囊从目标页拽回出发页
                        pagerGate = if (abs(latestPagerPosition() - index) > 0.01f) index else -1
                        dampedDragAnimation.animateToValue(index.toFloat())
                        latestOnSelect(index)
                    }
                }
        }

        // 跟随 pager：内容横滑时胶囊跟手；底栏自己拖拽期间 / 等 pager 追上来期间不跟
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { latestPagerPosition() }
                .collectLatest { position ->
                    if (barDragging) return@collectLatest
                    val gate = pagerGate
                    if (gate >= 0) {
                        if (abs(position - gate) < 0.01f) pagerGate = -1
                    } else {
                        dampedDragAnimation.updateValue(position)
                    }
                }
        }

        // 门控兜底：万一那次 pager 补间被打断（用户又去抓内容，最后停在别的页），
        // 「跟随 pager」也要能自己恢复，不能永久卡住。
        LaunchedEffect(pagerGate) {
            if (pagerGate >= 0) {
                delay(1200)
                if (pagerGate >= 0) pagerGate = -1
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 底栏项
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RowScope.GlassTabs(
    tabs: List<AvtTab>,
    badges: List<Int>,
    accent: Boolean,
    onSelect: (Int) -> Unit,
) {
    tabs.forEachIndexed { index, tab ->
        GlassTabItem(onClick = { onSelect(index) }) {
            GlassTabLabel(
                tab = tab,
                count = badges.getOrElse(index) { 0 },
                accent = accent,
            )
        }
    }
}

/**
 * 一格 tab。[accent] 只影响图标/文字颜色，不影响角标 ——
 * 因为角标要一直可辨认，不能跟着被染成一片蓝。
 */
@Composable
private fun RowScope.GlassTabItem(
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalGlassTabScale.current
    Column(
        modifier = Modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val value = scale()
                scaleX = value
                scaleY = value
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun GlassTabLabel(
    tab: AvtTab,
    count: Int,
    accent: Boolean,
) {
    val tint = if (accent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val icon: @Composable () -> Unit = {
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
        ) { icon() }
    } else {
        icon()
    }
    Text(
        text = tab.label,
        fontSize = 11.sp,
        maxLines = 1,
        fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
        color = tint,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 手势：别让底栏上的拖动顺带把下面的 HorizontalPager 也滚了
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 把落在底栏上的按压/拖动"吃掉"。
 *
 * 为什么挂在**根节点**、为什么是 **Main pass**：
 * 底栏（浮层）和 Pager（页面内容）在底栏区域是重叠命中的，两个都会收到同一串指针事件。
 * Main pass 是**从叶子往根**派发的，所以顺序天然是
 *   胶囊的拖拽识别 → 各 Tab 的点击 → （本函数）→ z 序更低的 Pager
 * 等轮到这一层再 consume，自己的手势已经处理完了；Pager 看到"已被消费"就会放弃这次拖拽。
 * 放在 Initial pass 会反过来把胶囊的拖拽一起掐掉（`inspectDragGestures` 见到 isConsumed 就退出）。
 */
internal fun Modifier.swallowDrags(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { if (it.pressed) it.consume() }
            if (event.changes.none { it.pressed }) break
        }
    }
}
