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
    val style = barStyle ?: remember(context) { BottomBarStyle.read(context) }

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
        val backdrop = rememberLayerBackdrop {
            drawRect(backgroundColor)
            drawContent()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hasBar) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = backgroundColor,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = title(pagerState.currentPage),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        actions = topBarActions,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
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
                    top = innerPadding.calculateTopPadding(),
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

        // 底栏浮层。放在 Box 里当兄弟节点，绝不会被录进上面的图层
        // （录进去会形成「A 画进 B、B 又画 A」的循环采样，直接 SIGSEGV）。
        if (hasBar) {
            when (style) {
                BottomBarStyle.M3 -> M3BottomBar(
                    backdrop = backdrop,
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
