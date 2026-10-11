// 顶层导航：主页（四分页）/ 设置（四分页）/ 更新日志 三个顶层目的地。
// 顶层用状态切换（返回键退栈），页面内部横滑用 HorizontalPager —— 与 ApkEditorMiuix 的交互一致。
// 悬浮顶栏 + 底栏毛玻璃：页面内容 layerBackdrop 录制 → BlurredBar 采样（同 ApkEditorMiuix 管线）。
package com.seagull.avt.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.seagull.avt.md3.icons.IcoPalette
import com.seagull.avt.md3.icons.IcoVpnKey
import com.seagull.avt.md3.icons.IcoFolderOpen
import com.seagull.avt.md3.icons.IcoInventory2
import com.seagull.avt.md3.icons.IcoMonitorHeart
import com.seagull.avt.md3.icons.IcoReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import com.seagull.avt.ui.liquid.IosLiquidGlassNavigationBar

/**
 * 顶层导航：用 vendored miuix nav 库（NavDisplay）驱动。
 * 二级页转场 = NavTransitions.MiuixDefault（库自带：右侧全宽滑入 + 被覆盖页视差/透明度），
 * 手势与程序动画共用同一条驱动，无需手写动画。
 */
@Composable
fun AvtNavigation(backStack: NavBackStack) {

    // 系统返回：退栈；到主页后交回系统
    BackHandler(enabled = backStack.size > 1) {
        backStack.removeLastOrNull()
    }

    NavDisplay(
        backStack = backStack,
        transition = NavTransitions.MiuixDefault,
        effects = NavDisplayEffects(
            enableCornerClip = true,
            cornerClipRadius = 32.dp,
            cornerClipMode = NavCornerClipMode.All,
            dimAmount = 0.5f,
            blockInputDuringTransition = false,
            backdropColor = MiuixTheme.colorScheme.surface,
        ),
    ) {
        entry<Route.Main> {
            MainPage(onOpenSettings = { backStack.add(Route.Settings) })
        }
        entry<Route.Settings> {
            SettingsPage(
                onBack = { backStack.removeLastOrNull() },
                onOpenChangelog = { backStack.add(Route.Changelog) },
            )
        }
        entry<Route.Changelog> {
            ChangelogPage(onBack = { backStack.removeLastOrNull() })
        }
    }
}

/** 主页：改写 / 条目 / 体检 / 日志 四分页 + Miuix NavigationBar 底栏（毛玻璃） */
@Composable
fun MainPage(onOpenSettings: () -> Unit) {
    // 分页下标走应用级共享状态：切主题 / 切 UI 风格时停在当前分页
    val pagerState = rememberPagerState(initialPage = AvtNav.mainPage, pageCount = { 4 })
    var selectedIndex by remember { mutableIntStateOf(AvtNav.mainPage) }
    val scope = rememberCoroutineScope()

    val titles = listOf("改写", "条目", "体检", "日志")
    val icons: List<ImageVector> = listOf(
        Icons.Filled.Edit,
        IcoInventory2,
        IcoMonitorHeart,
        IcoReceiptLong,
    )

    // 悬浮顶栏：滚动行为（各分页滚动时收拢）
    val topBarScrollBehavior = MiuixScrollBehavior()

    // 底栏毛玻璃：整个页面内容录进 backdrop；MD3 风格不用毛玻璃（实色 surface 栏）
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null && UiConfigState.uiStyle == 0

    // 底栏点击 → 翻页；横滑 → 同步底栏选中
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collectLatest { page ->
            selectedIndex = page
            AvtNav.mainPage = page
        }
    }

    Scaffold(
        // 内容延伸到栏后（栏悬浮），inset 只吃横向 —— 对齐 ApkEditorMiuix 的设计
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        topBar = {
            // 悬浮顶栏：透明起步，滚动后变实色（BlurUtils.topBarColor）
            BlurredBar(
                    backdrop = backdrop,
                    blurEnabled = UiConfigState.enableBlur && UiConfigState.uiStyle == 0,
                    gradient = ProgressiveBlur.Top,
                    blurRadius = UiConfigState.blurRadius,
                ) {
                SmallTopAppBar(
                    title = "ApkVerTool",
                    // 悬浮模糊态：透明让 progressive blur 透出来（模板 blurActive 分支同款）；
                    // 模糊层自带 30% surface 混色保证可读性
                    color = if (UiConfigState.enableBlur && backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = topBarScrollBehavior,
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "设置",
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (UiConfigState.uiStyle == 1) {
                // MD3 风格：标准 NavigationBar 实色（MD3 底栏本就贴底实色，无毛玻璃无悬浮）
                NavigationBar {
                    titles.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = {
                                selectedIndex = index
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            icon = icons[index],
                            label = label,
                            badge = when (index) {
                                0 ->
                                    if (AvtState.queue.size > 0) ({ BadgeText(AvtState.queue.size.toString()) }) else null
                                1 ->
                                    if (AvtState.putMap.size > 0) ({ BadgeText(AvtState.putMap.size.toString()) }) else null
                                else -> null
                            },
                        )
                    }
                }
            } else if (UiConfigState.useFloatingNavigationBar && UiConfigState.floatingNavStyle == 1) {
                // iOS 风格 = 液态玻璃底栏（模板 IosLiquidGlassNavigationBar 原样移植）
                val navigationItems = remember(titles, icons) {
                    List(titles.size) { i -> NavigationItem(titles[i], icons[i]) }
                }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    IosLiquidGlassNavigationBar(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        items = navigationItems,
                        selectedIndex = selectedIndex,
                        onItemClick = { index ->
                            selectedIndex = index
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        backdrop = backdrop,
                        isBlurActive = UiConfigState.enableBlur && backdrop != null,
                        badge = { index ->
                            when (index) {
                                0 -> if (AvtState.queue.size > 0) ({ BadgeText(AvtState.queue.size.toString()) }) else null
                                1 -> if (AvtState.putMap.size > 0) ({ BadgeText(AvtState.putMap.size.toString()) }) else null
                                else -> null
                            }
                        },
                    )
                }
            } else if (UiConfigState.useFloatingNavigationBar) {
                // 悬浮底栏：圆角胶囊 + 可选毛玻璃（样式走 UI 设置）
                val blurOn = UiConfigState.enableBlur && backdrop != null
                val barColor = if (blurOn) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
                val barShape = RoundedCornerShape(28.dp)
                // 风格差异：默认 = 浓一点的面色混合（偏实），iOS 风格 = 更透的玻璃
                val surfaceMix = if (UiConfigState.floatingNavStyle == 1) 0.25f else 0.4f
                FloatingNavigationBar(
                    modifier = if (blurOn) {
                        Modifier.textureBlur(
                            backdrop = backdrop!!,
                            shape = barShape,
                            blurRadius = UiConfigState.blurRadius,
                            colors = BlurDefaults.blurColors(
                                blendColors = listOf(
                                    BlendColorEntry(color = MiuixTheme.colorScheme.surfaceContainer.copy(surfaceMix)),
                                ),
                            ),
                        )
                    } else {
                        Modifier
                    },
                    color = barColor,
                ) {
                    titles.forEachIndexed { index, label ->
                        FloatingNavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = {
                                selectedIndex = index
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            icon = icons[index],
                            label = label,
                            badge = when (index) {
                                0 ->
                                    if (AvtState.queue.size > 0) ({ BadgeText(AvtState.queue.size.toString()) }) else null
                                1 ->
                                    if (AvtState.putMap.size > 0) ({ BadgeText(AvtState.putMap.size.toString()) }) else null
                                else -> null
                            },
                        )
                    }
                }
            } else {
                // 标准通栏底栏 + 毛玻璃（Bottom = 底边最强，向上渐清）
                BlurredBar(
                    backdrop = backdrop,
                    blurEnabled = UiConfigState.enableBlur && UiConfigState.uiStyle == 0,
                    gradient = ProgressiveBlur.Bottom,
                    blurRadius = UiConfigState.blurRadius,
                ) {
                    NavigationBar {
                        titles.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = selectedIndex == index,
                                onClick = {
                                    selectedIndex = index
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                icon = icons[index],
                                label = label,
                                badge = when (index) {
                                    0 ->
                                        if (AvtState.queue.size > 0) ({ BadgeText(AvtState.queue.size.toString()) }) else null
                                    1 ->
                                        if (AvtState.putMap.size > 0) ({ BadgeText(AvtState.putMap.size.toString()) }) else null
                                    else -> null
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        // 页面内容录进 backdrop：毛玻璃采样的就是它
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> RewritePage(padding = padding, scrollBehavior = topBarScrollBehavior)
                    1 -> EntriesPage(padding = padding, scrollBehavior = topBarScrollBehavior)
                    2 -> InspectPage(padding = padding, scrollBehavior = topBarScrollBehavior)
                    3 -> LogPage(padding = padding, scrollBehavior = topBarScrollBehavior)
                }
            }
        }
    }
}

/** 底栏数字角标（Miuix Badge：16dp 圆 + footnote2 字号） */
@Composable
private fun BadgeText(text: String) {
    Badge {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
        )
    }
}
