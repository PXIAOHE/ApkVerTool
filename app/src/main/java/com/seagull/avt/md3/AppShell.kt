package com.seagull.avt.md3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import com.seagull.avt.app.BlurredBar
import com.seagull.avt.app.UiConfigState
import com.seagull.avt.app.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as miuixRememberLayerBackdrop
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.seagull.avt.md3.bars.BottomBarStyle
import com.seagull.avt.md3.bars.M3BarReservedHeight
import com.seagull.avt.md3.bars.M3BottomBar
import com.seagull.avt.md3.glass.GlassBarMarginBottom
import com.seagull.avt.md3.glass.GlassBarMarginHorizontal
import com.seagull.avt.md3.glass.GlassBarReservedHeight
import com.seagull.avt.md3.glass.GlassBottomBar
import com.seagull.avt.md3.glass.GlassStaticBottomBar

/** 底栏一项：文案 + 图标。 */
data class AvtTab(val label: String, val icon: ImageVector)

/**
 * 通用外壳：顶栏 + 底栏 + HorizontalPager。
 *
 * **底栏有三种样式**，由 [barStyle] 选（设置页「外观」里切换，见 [BottomBarStyle]）。
 *
 * 三种样式**共用同一条布局路径**：内容录进 backdrop 图层，底栏是 `Box` 上的浮层。
 *
 * ```
 * Box(整屏)                                    ← 浮层的容器
 *  ├─ Box(Modifier.layerBackdrop)              ← 图层宿主，必须是整屏
 *  │   └─ Scaffold(bottomBar = {})             ← 页面内容；底栏槽刻意留空
 *  └─ 底栏浮层(align BottomCenter)              ← 盖在内容之上
 * ```
 *
 * 为什么三种都得这样 —— 因为三种底栏**都要采背景**：
 *
 * | 样式 | 采背景的用途 |
 * | :--- | :--- |
 * | [BottomBarStyle.M3] | `vibrancy + blur`（毛玻璃，无 lens） |
 * | [BottomBarStyle.GLASS_STATIC] | `vibrancy + blur + lens`（胶囊边缘折射） |
 * | [BottomBarStyle.GLASS_DYNAMIC] | 同上 + 拖拽胶囊的二次采样 |
 *
 * 底栏**不能**放进 `Scaffold` 的 `bottomBar` 槽：那个槽会给底栏预留空间，
 * 内容不会从底栏底下经过 → 玻璃采不到东西 → 模糊/折射/悬浮全部失效。
 *
 * ⚠️ 图层宿主必须是**整屏 Box**，不能直接挂在 `Scaffold` 上 ——
 * `Scaffold` 的实测高度比屏幕矮一条底栏，图层跟着它就只有那么高，
 * 而底栏浮层正好骑在图层下边界上，采样越界会在底栏顶边渗出一条 1 像素亮线。
 *
 * 三种样式的差异只剩**形状与交互**：
 * - M3：通栏直角，`NavigationBarItem` 默认配色；
 * - 玻璃两种：24dp 外边距的悬浮胶囊，选中态不同（见各自文件）。
 *
 * @param barStyle 传 `null` 表示「从 SharedPreferences 读当前设置」。
 *                 设置页会显式传 `state.barStyle`，这样点选时它自己的底栏立刻跟着换。
 */
/** M3 顶栏本体高度（不含状态栏），内容顶部按它预留。 */
private val TopBarHeight = 64.dp

/**
 * MD3 顶栏（浮层版）。
 *
 * 与底栏同样的思路：顶栏不放进 `Scaffold` 的槽，而是当浮层盖在内容之上，
 * 页面内容从它底下滚过 —— 开「栏体模糊」时用 `drawBackdrop` 采图层做毛玻璃，
 * 关掉就是原来的实色 M3 顶栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarOverlay(
    titleText: String,
    actions: @Composable RowScope.() -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    val blurOn = UiConfigState.enableBlur && backdrop != null
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    Box(modifier = modifier) {
        // 与 MiuixX 同一套：progressiveTextureBlur（贴边最强、向内渐清）+ 30% surface 混色
        Md3BlurredBar(
            backdrop = backdrop,
            blurEnabled = UiConfigState.enableBlur,
            gradient = ProgressiveBlur.Top,
            blurRadius = UiConfigState.blurRadius,
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = titleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (blurOn) Color.Transparent else surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}


/** MD3 版图层：底色用 MaterialTheme 的 surface（借 miuix 那版会用到它自己的主题色，在 MD3 下会发灰）。 */
@Composable
fun rememberMd3BlurBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported() || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return null
    val surfaceColor = MaterialTheme.colorScheme.surface
    return miuixRememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/** MD3 版渐进模糊栏：与 MiuixX 的 BlurredBar 同一套 progressiveTextureBlur，但混色用 MD3 的 surface。 */
@Composable
fun Md3BlurredBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    gradient: ProgressiveBlur,
    blurRadius: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val active = blurEnabled && backdrop != null
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier = modifier) {
        if (active) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .progressiveTextureBlur(
                        backdrop = backdrop!!,
                        shape = RectangleShape,
                        gradient = gradient.copy(curve = 2.2f),
                        blurRadius = blurRadius,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(BlendColorEntry(color = surface.copy(alpha = 0.3f))),
                        ),
                    ),
            )
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvtScaffold(
    tabs: List<AvtTab>,
    pagerState: PagerState,
    title: (Int) -> String,
    onSelect: (Int) -> Unit,
    pageContent: @Composable (Int, PaddingValues) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<Int> = emptyList(),
    showBottomBar: Boolean = true,
    barStyle: BottomBarStyle? = null,
    snackbarHostState: SnackbarHostState? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
) {
    val context = LocalContext.current
    // 读可观察状态：设置里改底栏样式，主页立刻跟着换（原来是 remember 只读一次，得重启）
    val style = barStyle ?: UiConfigState.barStyle

    val hasBar = showBottomBar && tabs.isNotEmpty()

    /** 内容底部要为浮层让出的额外高度（系统导航栏那条 inset 由 Scaffold 自己算）。 */
    val reservedHeight: Dp = when {
        !hasBar -> 0.dp
        style == BottomBarStyle.M3 -> M3BarReservedHeight
        else -> GlassBarReservedHeight
    }

    val backgroundColor = MaterialTheme.colorScheme.background

    Box(modifier = modifier.fillMaxSize()) {

        // 造图层。必须先 drawRect 补底色再 drawContent —— 只录内容的话，
        // 玻璃边缘会采到「空」，出现透明像素（官方教程第 2 步专门讲这个坑）。
        // kyant 图层：玻璃胶囊（drawBackdrop / lens）用
        val backdrop = rememberLayerBackdrop {
            drawRect(backgroundColor)
            drawContent()
        }
        // miuix 图层：顶栏 / M3 通栏的「渐进模糊」用（两套 backdrop 不通用）
        val miuixBackdrop = rememberMd3BlurBackdrop()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hasBar) Modifier.layerBackdrop(backdrop) else Modifier)
                .then(if (miuixBackdrop != null) Modifier.miuixLayerBackdrop(miuixBackdrop) else Modifier),
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = backgroundColor,
                // 顶栏槽留空：顶栏改成下面的浮层（和底栏同一个套路），
                // 否则它会被录进图层自己、开模糊时会形成循环采样。
                topBar = {},
                // 三种样式都留空：底栏一律是浮层，内容才会从它底下经过。
                bottomBar = {},
                snackbarHost = {
                    if (snackbarHostState != null) {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.padding(bottom = reservedHeight),
                        )
                    }
                },
            ) { innerPadding ->
                val pagePadding = PaddingValues(
                    // innerPadding.top 已经含状态栏；再补顶栏本体高度
                    top = innerPadding.calculateTopPadding() + TopBarHeight,
                    bottom = innerPadding.calculateBottomPadding() + reservedHeight,
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    pageContent(page, pagePadding)
                }
            }
        }

        // 顶栏浮层：与底栏同样当兄弟节点，开「栏体模糊」时采 backdrop 做毛玻璃。
        TopBarOverlay(
            titleText = title(pagerState.currentPage),
            actions = topBarActions,
            backdrop = miuixBackdrop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )

        // 底栏浮层。放在 Box 里当兄弟节点，绝不会被录进上面的图层
        // （录进去会形成「A 画进 B、B 又画 A」的循环采样，直接 SIGSEGV）。
        if (hasBar) {
            when (style) {
                BottomBarStyle.M3 -> M3BottomBar(
                    backdrop = backdrop,
                    miuixBackdrop = miuixBackdrop,
                    tabs = tabs,
                    selectedIndex = pagerState.currentPage,
                    onSelect = onSelect,
                    badges = badges,
                    // 通栏：不加左右外边距、不加 navigationBarsPadding
                    // （系统导航栏那条 inset 由 NavigationBar 自己撑）。
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )

                else -> {
                    // 玻璃两种是悬浮胶囊，要跟手 + 防互拽，走 guardedSelect。
                    val guardedSelect: (Int) -> Unit = { index ->
                        if (index != pagerState.currentPage) onSelect(index)
                    }
                    val selectedIndex = { pagerState.currentPage }
                    // 小数页位置：内容横滑时让指示器/胶囊跟手（翻页结束才跳会很生硬）。
                    val pagerPosition = {
                        pagerState.currentPage + pagerState.currentPageOffsetFraction
                    }
                    val barModifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            start = GlassBarMarginHorizontal,
                            end = GlassBarMarginHorizontal,
                            bottom = GlassBarMarginBottom,
                        )

                    if (style == BottomBarStyle.GLASS_STATIC) {
                        GlassStaticBottomBar(
                            backdrop = backdrop,
                            tabs = tabs,
                            selectedIndex = selectedIndex,
                            pagerPosition = pagerPosition,
                            onSelect = guardedSelect,
                            badges = badges,
                            modifier = barModifier,
                        )
                    } else {
                        GlassBottomBar(
                            backdrop = backdrop,
                            tabs = tabs,
                            selectedIndex = selectedIndex,
                            pagerPosition = pagerPosition,
                            onSelect = guardedSelect,
                            badges = badges,
                            modifier = barModifier,
                        )
                    }
                }
            }
        }
    }
}
