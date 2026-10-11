package com.seagull.avt.md3.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 按钮强调度，对应旧代码的 bigButton(accent=true/false) 与 glassButton。 */
enum class AvtEmphasis { Primary, Tonal, Outline }

/**
 * 「紧凑排版」开关。
 *
 * 由 [PageColumn] 用 `compact = true` 往下发，[AvtCard] / [FoldGroup] 自己读。
 * 这样页面只要在 `PageColumn` 上标一次，里面的卡片不用逐个传参 ——
 * 也天然做到「只收紧某一页、别的页一行不动」（设置页不传就是原样）。
 */
private val LocalCompactLayout = staticCompositionLocalOf { false }

@Composable
fun AvtCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val compact = LocalCompactLayout.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 14.dp else 16.dp,
                vertical = if (compact) 11.dp else 14.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
            content = content,
        )
    }
}

/** 卡片小标题（旧 label()）。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
}

/** 辅助说明（旧 note()）。 */
@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 17.sp,
    )
}

@Composable
fun AvtField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    lines: Int = 1,
    enabled: Boolean = true,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        singleLine = lines <= 1,
        minLines = lines,
        maxLines = if (lines <= 1) 1 else lines,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.small,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
    )
}

@Composable
fun AvtSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (supporting != null) {
                Spacer(Modifier.height(2.dp))
                Note(supporting)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onCheckedChange(it)
            },
            enabled = enabled,
        )
    }
}

/** 键值信息行（旧 kv()）。 */
@Composable
fun InfoPair(key: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 20.sp,
        )
    }
}

/**
 * 折叠分组：标题整行可点，右侧「展开 / 收起」+ 箭头。
 * 语义与旧版完全一致 —— 收起的分组整组字段都不参与改写。
 */
@Composable
fun FoldGroup(
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val compact = LocalCompactLayout.current
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onToggle()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = if (compact) 10.dp else 12.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (open) "收起" else "展开",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        AnimatedVisibility(visible = open) {
            Column(
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                content = content,
            )
        }
    }
}

/**
 * 执行日志面板：等宽字体 + 追加即自动滚到底。
 *
 * 以 [com.seagull.avt.md3.LOG_SECTION] 开头的行是「小节标题」，用 M3 标题样式渲染
 * （取代旧版 `──── 体检报告 ────` 这类画线分隔）。
 */
@Composable
fun LogPane(logs: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.scrollToItem(logs.lastIndex)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(360.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        ) {
            itemsIndexed(logs) { _, line ->
                if (line.startsWith(com.seagull.avt.md3.LOG_SECTION)) {
                    Text(
                        text = line.substring(com.seagull.avt.md3.LOG_SECTION.length),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = line.ifEmpty { " " },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

/** 每页统一的滚动容器：把 Scaffold 的 innerPadding 作为 contentPadding 消费掉。 */
@Composable
fun PageColumn(
    inner: PaddingValues,
    compact: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    CompositionLocalProvider(LocalCompactLayout provides compact) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = if (compact) 14.dp else 16.dp,
                end = if (compact) 14.dp else 16.dp,
                top = inner.calculateTopPadding() + if (compact) 8.dp else 10.dp,
                bottom = inner.calculateBottomPadding() + if (compact) 14.dp else 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp),
            content = content,
        )
    }
}

/** 统一按钮：图标 + 文字，按强调度分三档。 */
@Composable
fun AvtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    emphasis: AvtEmphasis = AvtEmphasis.Primary,
) {
    val body: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
    }
    val haptic = LocalHapticFeedback.current
    val clicked: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        onClick()
    }
    val padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    when (emphasis) {
        AvtEmphasis.Primary -> Button(
            onClick = clicked, modifier = modifier, enabled = enabled,
            shape = MaterialTheme.shapes.small, contentPadding = padding, content = body,
        )
        AvtEmphasis.Tonal -> FilledTonalButton(
            onClick = clicked, modifier = modifier, enabled = enabled,
            shape = MaterialTheme.shapes.small, contentPadding = padding, content = body,
        )
        AvtEmphasis.Outline -> OutlinedButton(
            onClick = clicked, modifier = modifier, enabled = enabled,
            shape = MaterialTheme.shapes.small, contentPadding = padding, content = body,
        )
    }
}

/** 一个圆点 + 文本，取代旧版手写的「• 」列表行。 */
@Composable
fun BulletLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(5.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 21.sp,
        )
    }
}

/**
 * 下拉选择行（标题 + 当前值 + 展开面板）。
 *
 * 展开态的**位置与动画**对齐 MiuixX 那边用的 miuix `OverlayDropdownPreference`：
 *  - 位置：面板右边贴齐本行右边，顶部落在本行下方 8dp；下方放不下就翻到上方，再不行垂直居中
 *  - 动画：缩放从 0 → 1 用 spring(dampingRatio = 0.82, stiffness = 362.5)，透明度进场 tween(200)、
 *          退场 tween(150)，退出时缩放与淡出并行——参数与 miuix `ListPopupDefaults` 一致
 *  - 尺寸：最小宽度 200dp（= miuix `ListPopupDefaults.MinWidth`）
 * 缩放原点是面板右上角；视觉仍走 Material 3（只把展开行为对齐 MiuixX）。
 */
@Composable
fun AvtDropdownRow(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = items.getOrNull(selectedIndex) ?: items.firstOrNull().orEmpty()
    val haptic = LocalHapticFeedback.current

    // 锚点就是这一行：Popup 的坐标取自它所在的父布局（下面这个 Box）
    // 另外记下行在窗口里的位置，用来判断下方还剩多少空间（决定向下 / 向上展开）
    var anchorRect by remember { mutableStateOf(IntRect.Zero) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                val r = it.boundsInWindow()
                anchorRect = IntRect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = enabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        expanded = true
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (supporting != null) {
                        Text(
                            text = supporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = selectedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        AvtDropdownOverlay(
            expanded = expanded,
            anchorRect = anchorRect,
            onDismissRequest = { expanded = false },
            items = items,
            selectedIndex = selectedIndex,
            onSelect = { index ->
                expanded = false
                onSelectedIndexChange(index)
            },
            enabled = enabled,
        )
    }
}

/**
 * miuix 风格的悬浮下拉面板：右边对齐锚点、从右上角缩放展开。
 *
 * 参数全部照着 miuix-ui 0.9.4-rc01 反编译出来的规格写：
 *  - position: `ListPopupDefaults.dropdownPositionProvider(verticalMargin = 8.dp, horizontalMargin = 0.dp)`
 *  - enter:    scale 0 → 1（spring 0.82 / 362.5）；alpha 0 → 1（tween 200）
 *  - exit:     scale 1 → 0（同 spring）与 alpha 1 → 0（tween 150）并行，alpha 走完才真正移除
 *  - size:     minWidth 200.dp
 */
@Composable
private fun AvtDropdownOverlay(
    expanded: Boolean,
    anchorRect: IntRect,
    onDismissRequest: () -> Unit,
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean,
) {
    val scaleSpec = remember {
        spring<Float>(dampingRatio = 0.82f, stiffness = 362.5f, visibilityThreshold = 1e-4f)
    }
    val alphaIn = remember { tween<Float>(durationMillis = 200) }
    val alphaOut = remember { tween<Float>(durationMillis = 150) }

    val animScale = remember { Animatable(0f) }
    val animAlpha = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) {
            visible = true
            launch { animScale.animateTo(1f, scaleSpec) }
            animAlpha.animateTo(1f, alphaIn)
        } else if (visible) {
            launch { animScale.animateTo(0f, scaleSpec) }
            animAlpha.animateTo(0f, alphaOut)
            visible = false
        }
    }

    if (!visible || items.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.roundToPx() }
    val edgePx = with(density) { 8.dp.roundToPx() }
    val minPanelPx = with(density) { 200.dp.roundToPx() }
    val maxPanelPx = with(density) { 420.dp.roundToPx() }
    val winHeight = LocalWindowInfo.current.containerSize.height
    // 优先向下：只要下方还剩 >= 200dp 就向下展开，面板高度上限 = 下方剩余（超出内部滚动）。
    // 只有下方实在放不下，才考虑上方 / 垂直居中。
    val roomBelow = winHeight - anchorRect.bottom - gapPx - edgePx
    val openDown = roomBelow >= minPanelPx
    val panelMaxPx = if (openDown) minOf(maxPanelPx, maxOf(roomBelow, minPanelPx)) else maxPanelPx
    val provider = remember(gapPx, edgePx, openDown, anchorRect) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val w = popupContentSize.width
                val h = popupContentSize.height
                android.util.Log.i(
                    "AvtDropdown",
                    "anchor=[${anchorBounds.left},${anchorBounds.top},${anchorBounds.right},${anchorBounds.bottom}] " +
                        "win=${windowSize.width}x${windowSize.height} popup=${w}x${h} " +
                        "openDown=$openDown panelMaxPx=$panelMaxPx rowRect=$anchorRect",
                )
                val winLeft = 0
                val winTop = 0
                val winRight = windowSize.width
                val winBottom = windowSize.height
                // 锚点用「本行在窗口里的真实位置」(anchorRect)，不用框架给的 anchorBounds：
                // 在 LazyColumn 里后者不一定是本行（实测差了约 300px，就是「比 MiuixX 更靠下」的来源）。
                // 公式与 miuix ListPopupDefaults.dropdownPositionProvider 一致：
                //   X: anchor.right - popupWidth（Align.End）
                //   Y: anchor.bottom + 8dp（向下）；放不下翻到上方；再不行在锚点上垂直居中
                val aRight = if (anchorRect.width > 0) anchorRect.right else anchorBounds.right
                val aTop = if (anchorRect.height > 0) anchorRect.top else anchorBounds.top
                val aBottom = if (anchorRect.height > 0) anchorRect.bottom else anchorBounds.bottom
                val aHeight = if (anchorRect.height > 0) anchorRect.height else anchorBounds.height
                val x = (aRight - w).coerceIn(
                    winLeft,
                    maxOf(winRight - w - edgePx, winLeft),
                )
                val y = when {
                    openDown -> aBottom + gapPx
                    aTop - winTop > h -> aTop - h - gapPx
                    else -> aTop + aHeight / 2 - h / 2
                }.coerceIn(
                    minOf(winTop + edgePx, winBottom - h - edgePx),
                    winBottom - h - edgePx,
                )
                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = enabled),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                // 宽度由「最长的那条选项」决定（miuix 的 MinWidth 是下限，不是固定值）；
                // 只用 widthIn(min) 会被 Popup 的最大宽度约束撑成整屏宽，所以先取 IntrinsicSize。
                .width(IntrinsicSize.Max)
                .widthIn(min = 200.dp)
                .heightIn(max = with(density) { panelMaxPx.toDp() })
                .graphicsLayer {
                    scaleX = animScale.value
                    scaleY = animScale.value
                    this.alpha = animAlpha.value
                    transformOrigin = TransformOrigin(pivotFractionX = 1f, pivotFractionY = 0f)
                },
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onSelect(index)
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
