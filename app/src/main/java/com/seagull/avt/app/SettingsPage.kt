// 设置页四分页：外观 / 签名 / 输出 / 关于（与旧 Java 版 key 完全兼容）。
// 视觉全量 miuix：SwitchPreference / OverlayDropdownPreference / ArrowPreference / Card。
package com.seagull.avt.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import com.seagull.avt.md3.icons.IcoArticle
import com.seagull.avt.md3.icons.IcoCleaningServices
import com.seagull.avt.md3.icons.IcoDeleteSweep
import com.seagull.avt.md3.icons.IcoDownload
import com.seagull.avt.md3.icons.IcoFileOpen
import com.seagull.avt.md3.icons.IcoSave
import com.seagull.avt.md3.icons.IcoStraighten
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.seagull.avt.core.SignUtil
import com.seagull.avt.ui.TextField
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.seagull.avt.ui.liquid.IosLiquidGlassNavigationBar

private val SETTING_TITLES = listOf("外观", "签名", "输出", "关于")

@Composable
fun SettingsPage(onBack: () -> Unit, onOpenChangelog: () -> Unit) {
    // 分页下标走应用级共享状态：切主题 / 切 UI 风格时停在当前分页
    val pagerState = rememberPagerState(initialPage = AvtNav.settingsPage, pageCount = { 4 })
    var selectedIndex by remember { mutableIntStateOf(AvtNav.settingsPage) }
    val scope = rememberCoroutineScope()

    // 悬浮顶栏 + 底栏毛玻璃（与主页同一套管线）
    val topBarScrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()

    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
            .collect {
                selectedIndex = it
                AvtNav.settingsPage = it
            }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        topBar = {
            // 二级页规格（对齐 ApkEditorMiuix）：Back 箭头返回 + 干净标题；底栏按需求保留
            // 顶栏也毛玻璃：与主页顶栏同一套 BlurredBar（以前这页只有底栏有玻璃）
            BlurredBar(
                backdrop = backdrop,
                blurEnabled = UiConfigState.enableBlur && UiConfigState.uiStyle == 0,
                gradient = ProgressiveBlur.Top,
                blurRadius = UiConfigState.blurRadius,
            ) {
            SmallTopAppBar(
                title = "设置",
                color = if (UiConfigState.enableBlur && backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                scrollBehavior = topBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
            }
        },
        bottomBar = {
            if (UiConfigState.uiStyle == 1) {
                // MD3 风格：标准实色底栏
                NavigationBar {
                    SETTING_TITLES.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = {
                                selectedIndex = index
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            icon = when (index) {
                                0 -> IcoPalette
                                1 -> IcoVpnKey
                                2 -> IcoFolderOpen
                                else -> Icons.Filled.Info
                            },
                            label = label,
                        )
                    }
                }
            } else if (UiConfigState.useFloatingNavigationBar && UiConfigState.floatingNavStyle == 1) {
                // iOS 风格 = 液态玻璃底栏（与主页同款组件）
                val navigationItems = remember(SETTING_TITLES) {
                    List(SETTING_TITLES.size) { i ->
                        NavigationItem(
                            SETTING_TITLES[i],
                            when (i) {
                                0 -> IcoPalette
                                1 -> IcoVpnKey
                                2 -> IcoFolderOpen
                                else -> Icons.Filled.Info
                            },
                        )
                    }
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
                    )
                }
            } else if (UiConfigState.useFloatingNavigationBar) {
                // 悬浮胶囊（默认风格）
                val blurOn = UiConfigState.enableBlur && backdrop != null
                val barColor = if (blurOn) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
                val barShape = RoundedCornerShape(28.dp)
                FloatingNavigationBar(
                    modifier = if (blurOn) {
                        Modifier.textureBlur(
                            backdrop = backdrop!!,
                            shape = barShape,
                            blurRadius = UiConfigState.blurRadius,
                            colors = BlurDefaults.blurColors(
                                blendColors = listOf(
                                    BlendColorEntry(color = MiuixTheme.colorScheme.surfaceContainer.copy(0.4f)),
                                ),
                            ),
                        )
                    } else {
                        Modifier
                    },
                    color = barColor,
                ) {
                    SETTING_TITLES.forEachIndexed { index, label ->
                        FloatingNavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = {
                                selectedIndex = index
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            icon = when (index) {
                                0 -> IcoPalette
                                1 -> IcoVpnKey
                                2 -> IcoFolderOpen
                                else -> Icons.Filled.Info
                            },
                            label = label,
                        )
                    }
                }
            } else {
                BlurredBar(
                    backdrop = backdrop,
                    blurEnabled = UiConfigState.enableBlur && UiConfigState.uiStyle == 0,
                    gradient = ProgressiveBlur.Bottom,
                    blurRadius = UiConfigState.blurRadius,
                ) {
                    NavigationBar {
                        SETTING_TITLES.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = selectedIndex == index,
                                onClick = {
                                    selectedIndex = index
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                icon = when (index) {
                                    0 -> IcoPalette
                                    1 -> IcoVpnKey
                                    2 -> IcoFolderOpen
                                    else -> Icons.Filled.Info
                                },
                                label = label,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
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
                    0 -> AppearanceTab(padding)
                    1 -> SignTab(padding)
                    2 -> OutputTab(padding)
                    3 -> AboutTab(padding, onOpenChangelog)
                }
            }
        }
    }
}

// ───────────────────────── 外观 ─────────────────────────

@Composable
private fun AppearanceTab(padding: androidx.compose.foundation.layout.PaddingValues) {
    val context = LocalContext.current
    val themeOptions = listOf("跟随系统", "浅色", "深色", "莫奈 · 跟随系统", "莫奈 · 浅色", "莫奈 · 深色")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
    ) {
        SmallTitle(text = "主题")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            OverlayDropdownPreference(
                items = themeOptions,
                selectedIndex = ThemeState.mode,
                title = "主题模式",
                summary = "「莫奈」= Material You 动态取色，可从种子色推导整套配色",
                onSelectedIndexChange = { m ->
                    ThemeState.setMode(context, m)
                },
            )
        }

        // 莫奈取色：仅在选择莫奈模式时出现（种子色 / 配色风格 / 色彩规范）
        if (ThemeState.isMonet) {
            SmallTitle(text = "莫奈取色")
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                OverlayDropdownPreference(
                    title = "种子色",
                    summary = "整套配色由它推导（默认 = 系统调色板）",
                    items = listOf("默认") + KeyColors.map { it.first },
                    selectedIndex = ThemeState.seedIndex,
                    onSelectedIndexChange = { i ->
                        ThemeState.setMonet(context, seed = i)
                    },
                )
                OverlayDropdownPreference(
                    title = "配色风格",
                    summary = "同一种子色的不同推导风格",
                    items = PALETTE_STYLE_OPTIONS,
                    selectedIndex = ThemeState.paletteStyle,
                    onSelectedIndexChange = { i ->
                        ThemeState.setMonet(context, palette = i)
                    },
                )
                OverlayDropdownPreference(
                    title = "色彩规范",
                    summary = "Material 3 新版 / 经典版取色算法",
                    items = COLOR_SPEC_OPTIONS,
                    selectedIndex = ThemeState.colorSpec,
                    onSelectedIndexChange = { i ->
                        ThemeState.setMonet(context, spec = i)
                    },
                )
            }
        }

        SmallTitle(text = "界面风格")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            OverlayDropdownPreference(
                items = listOf("MiuixX（毛玻璃）", "MD3（Material）"),
                selectedIndex = UiConfigState.uiStyle,
                title = "全局风格",
                summary = "切换全部页面的配色、栏体与改写页布局",
                onSelectedIndexChange = { i ->
                    if (i == UiConfigState.uiStyle) return@OverlayDropdownPreference
                    UiConfigState.uiStyle = i
                    UiConfigState.foldGroups = i == 1
                    UiConfigState.save(context)
                    // 热切换：uiStyle 是 mutableStateOf，AvtApp 读它做分支，改值即重组换套（无需提示）
                },
            )
        }

        SmallTitle(text = "顶栏与底栏")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            SwitchPreference(
                title = "栏体模糊",
                summary = "顶栏与底栏的毛玻璃效果",
                checked = UiConfigState.enableBlur,
                onCheckedChange = {
                    UiConfigState.enableBlur = it
                    UiConfigState.save(context)
                },
            )
            // 开模糊才显示半径滑条（对齐 ApkEditorMiuix 的交互）
            if (UiConfigState.enableBlur) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            "模糊半径",
                            style = MiuixTheme.textStyles.main,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%.1f".format(UiConfigState.blurRadius) + "px",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.subtitle,
                        )
                    }
                    Slider(
                        value = UiConfigState.blurRadius,
                        onValueChange = {
                            UiConfigState.blurRadius = it
                            UiConfigState.save(context)
                        },
                        valueRange = 0f..30f,
                    )
                }
            }
            // 对齐上游 v2.1.0：底栏样式三选一（原来拆成「开关 + 条件子菜单」两段）
            OverlayDropdownPreference(
                title = "底栏样式",
                items = BAR_STYLE_OPTIONS,
                selectedIndex = barStyleIndex(),
                onSelectedIndexChange = { i ->
                    when (i) {
                        0 -> { UiConfigState.useFloatingNavigationBar = false }
                        1 -> { UiConfigState.useFloatingNavigationBar = true; UiConfigState.floatingNavStyle = 0 }
                        else -> { UiConfigState.useFloatingNavigationBar = true; UiConfigState.floatingNavStyle = 1 }
                    }
                    UiConfigState.save(context)
                    // 反向同步：MiuixX 这边改底栏样式，MD3 那套的「底栏样式」也跟着走（同一件事）
                    com.seagull.avt.md3.bars.BottomBarStyle.write(
                        context.getSharedPreferences("settings", 0),
                        when (i) {
                            0 -> com.seagull.avt.md3.bars.BottomBarStyle.M3
                            1 -> com.seagull.avt.md3.bars.BottomBarStyle.GLASS_STATIC
                            else -> com.seagull.avt.md3.bars.BottomBarStyle.GLASS_DYNAMIC
                        },
                    )
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 底栏样式三选一（对齐上游 v2.1.0 的单一选择器）。下标 ↔ 旧的 useFloating + floatingNavStyle 组合。 */
private val BAR_STYLE_OPTIONS = listOf("标准通栏", "悬浮胶囊 · 默认", "液态玻璃 · iOS")

private fun barStyleIndex(): Int =
    if (!UiConfigState.useFloatingNavigationBar) 0
    else if (UiConfigState.floatingNavStyle == 1) 2 else 1

// ───────────────────────── 签名 ─────────────────────────

@Composable
private fun SignTab(padding: androidx.compose.foundation.layout.PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var resign by remember { mutableStateOf(AvtPrefs.resign(context)) }
    var v1 by remember { mutableStateOf(AvtPrefs.scheme(context, "scheme_v1", true)) }
    var v2 by remember { mutableStateOf(AvtPrefs.scheme(context, "scheme_v2", true)) }
    var v3 by remember { mutableStateOf(AvtPrefs.scheme(context, "scheme_v3", true)) }
    var v4 by remember { mutableStateOf(AvtPrefs.scheme(context, "scheme_v4", false)) }
    var keyPass by remember { mutableStateOf(AvtPrefs.keyPass(context)) }
    var keyStatus by remember { mutableStateOf("● 当前使用：内置密钥") }

    LaunchedEffect(Unit) {
        val useCustom = AvtPrefs.useCustomKey(context)
        val f = File(context.filesDir, "custom_key.p12")
        keyStatus = if (useCustom && f.exists()) {
            "● 当前使用：自定义密钥（${f.length() / 1024} KB）"
        } else "● 当前使用：内置密钥"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
    ) {
        SmallTitle(text = "重签名")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            SwitchPreference(
                checked = resign,
                onCheckedChange = {
                    resign = it
                    AvtPrefs.setResign(context, it)
                    Toast.makeText(
                        context,
                        if (it) "已开启重签名" else "已切换为 MT 式不重签输出",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                title = "修改后重新签名",
                summary = "关闭 = MT 式不重签输出：原签名数据原样保留，仅供二次处理，不能直接安装",
            )
        }

        SmallTitle(text = "签名方案")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            SwitchPreference(
                checked = v1,
                onCheckedChange = { v1 = it; AvtPrefs.setScheme(context, "scheme_v1", it) },
                title = "V1（JAR 签名，minSdk < 24 必需）",
            )
            SwitchPreference(
                checked = v2,
                onCheckedChange = { v2 = it; AvtPrefs.setScheme(context, "scheme_v2", it) },
                title = "V2（APK Signature Scheme v2）",
            )
            SwitchPreference(
                checked = v3,
                onCheckedChange = { v3 = it; AvtPrefs.setScheme(context, "scheme_v3", it) },
                title = "V3（密钥轮换支持）",
            )
            SwitchPreference(
                checked = v4,
                onCheckedChange = { v4 = it; AvtPrefs.setScheme(context, "scheme_v4", it) },
                title = "V4（增量安装，额外产出 .idsig）",
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "V1 文件始终写入；minSdk ≥ 24 时系统不校验 V1，属正常现象。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        SmallTitle(text = "签名密钥（PKCS#12）")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                keyStatus,
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.main,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = keyPass,
                onValueChange = { keyPass = it },
                label = "密钥密码",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = "保存密码",
                    onClick = {
                        AvtPrefs.setKeyPass(context, keyPass)
                        Toast.makeText(context, "已保存密钥密码", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    text = "生成新密钥",
                    onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    val cn = "ApkVerTool-" + java.text.SimpleDateFormat(
                                        "yyyyMMdd-HHmm", java.util.Locale.US,
                                    ).format(java.util.Date())
                                    val out = File(context.filesDir, "custom_key.p12")
                                    SignUtil.generateKey(
                                        out, SignUtil.PASS, cn, "Seagull", "Seagull", 10950, 2048,
                                    )
                                }
                            }.onSuccess {
                                context.getSharedPreferences("settings", 0)
                                    .edit().putBoolean("use_custom", true).apply()
                                keyStatus = "● 当前使用：自定义密钥"
                                Toast.makeText(context, "密钥已生成", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "生成失败: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                text = "恢复内置密钥",
                onClick = {
                    File(context.filesDir, "custom_key.p12").delete()
                    context.getSharedPreferences("settings", 0)
                        .edit().putBoolean("use_custom", false).apply()
                    keyStatus = "● 当前使用：内置密钥"
                    Toast.makeText(context, "已恢复内置密钥", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ───────────────────────── 输出 ─────────────────────────

@Composable
private fun OutputTab(padding: androidx.compose.foundation.layout.PaddingValues) {
    val context = LocalContext.current
    var outputPath by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val uriStr = AvtPrefs.outputUri(context)
        outputPath = if (uriStr.isNotEmpty()) {
            try {
                val path = Uri.parse(uriStr).lastPathSegment
                "● 当前：" + (path?.replace("primary:", "内部存储/") ?: "自定义目录")
            } catch (_: Exception) {
                "● 当前：默认（自动选择）"
            }
        } else "● 当前：默认（应用私有目录 + 手动导出）"
    }

    val dirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val take = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, take)
            AvtPrefs.setOutputUri(context, uri.toString())
            outputPath = "● 当前：" + (
                uri.lastPathSegment?.replace("primary:", "内部存储/") ?: "自定义目录"
                )
            Toast.makeText(context, "输出位置已设置", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "设置失败: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
    ) {
        SmallTitle(text = "输出位置")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                outputPath,
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.main,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = "选择文件夹",
                    onClick = { dirPicker.launch(null) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    text = "默认",
                    onClick = {
                        AvtPrefs.setOutputUri(context, null)
                        outputPath = "● 当前：默认（应用私有目录 + 手动导出）"
                        Toast.makeText(context, "已恢复默认输出位置", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "APK先写到应用私有目录，导出到指定目录成功后自动删掉那份中转文件，" +
                        "APK只留在指定目录里；「安装 / 分享」会自动用指定目录里的那份。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ───────────────────────── 关于 ─────────────────────────

@Composable
private fun AboutTab(
    padding: androidx.compose.foundation.layout.PaddingValues,
    onOpenChangelog: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 与 build.gradle.kts / PackageManager 保持一致的运行时读法（ApkEditorMiuix AboutPage 同款）
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: AvtVersion.NAME
        } catch (_: Exception) { AvtVersion.NAME }
    }
    val versionCode = remember(context) {
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toString()
            else pi.versionCode.toString()
        } catch (_: Exception) { AvtVersion.CODE.toString() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
    ) {
        SmallTitle(text = "关于")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            // 版本运行时读 PackageManager，与构建配置永远一致
            InfoRow(
                title = "应用版本",
                summary = "v" + versionName + " (" + versionCode + ")",
            )
            ArrowPreference(
                title = "更新日志",
                summary = "一个版本一页，左右滑动翻页",
                onClick = onOpenChangelog,
            )
            ArrowPreference(
                title = "检查更新",
                summary = if (UpdateChecker.checking) "正在检查…"
                else "当前 v" + versionName + "（code " + versionCode + "）",
                onClick = {
                    if (!UpdateChecker.checking) {
                        scope.launch {
                            val info = withContext(Dispatchers.IO) { UpdateChecker.check(context) }
                            val msg = when {
                                info != null -> "发现新版本 v" + info.name
                                UpdateChecker.lastError != null -> "检查失败：" + UpdateChecker.lastError
                                else -> "已是最新版本"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
            ArrowPreference(
                title = "开源地址",
                summary = "GitHub · PXIAOHE / ApkVerTool",
                onClick = {
                    runCatching {
                        uriHandler.openUri("https://github.com/PXIAOHE/ApkVerTool")
                    }.onFailure {
                        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            InfoRow(
                title = "开发者",
                summary = "Seagull（海鸥）· PXIAOHE",
            )
        }

        // 对齐上游 v2.1.0：UI设计者（圆形头像 + 名字，整行可点跳酷安主页）
        SmallTitle(text = "UI设计")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            DesignerRow(
                avatarRes = com.seagull.avt.R.drawable.avatar_qi,
                name = "祈",
                // 酷安 App 内打开（intent:// → coolmarket scheme），未装酷安回落浏览器
                url = "intent://www.coolapk.com/u/396214#Intent;scheme=coolmarket;package=com.coolapk.market;end",
                onOpen = { u ->
                    if (!LinkOpener.open(context, u)) {
                        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
            DesignerRow(
                avatarRes = com.seagull.avt.R.drawable.avatar_xkspeed,
                name = "XKspeed",
                url = "intent://www.coolapk.com/u/34717033#Intent;scheme=coolmarket;package=com.coolapk.market;end",
                onOpen = { u ->
                    if (!LinkOpener.open(context, u)) {
                        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        SmallTitle(text = "功能说明")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            // 纯展示行（无点击 → InfoRow 不画箭头）
            InfoRow(
                title = "清单改写",
                summary = "versionName / versionCode / minSdk / targetSdk / 包名 / 应用名",
            )
            InfoRow(
                title = "条目增删",
                summary = "替换 / 新增 / 删除 APK 内任意条目或目录前缀",
            )
            InfoRow(
                title = "多包容器",
                summary = "XAPK / APKS / split 逐包处理并回填元数据",
            )
            InfoRow(
                title = "体检",
                summary = "版本 / SDK / DEX / ABI / 权限 / 加固壳识别 / 签名者指纹",
            )
            InfoRow(
                title = "重签名",
                summary = "V1 / V2 / V3 / V4 可选，重打包保持 4 字节与 16KB 对齐",
            )
        }

        SmallTitle(text = "开源引用")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            ArrowPreference(
                title = "Miuix",
                summary = "compose-miuix-ui/miuix — 界面全量采用其 Compose 实现",
                onClick = {
                    runCatching {
                        uriHandler.openUri("https://github.com/compose-miuix-ui/miuix")
                    }.onFailure {
                        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            InfoRow(
                title = "apksig",
                summary = "Android Open Source Project — APK 签名与验签",
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 一位设计者：圆形头像 + 名字，整行可点跳酷安主页。
 * 头像是构建期打进 res/drawable-nodpi 的静态图（App 无 INTERNET 权限，运行时拉不了）。
 */
@Composable
private fun DesignerRow(
    avatarRes: Int,
    name: String,
    url: String,
    onOpen: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable { onOpen(url) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(avatarRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .border(1.dp, MiuixTheme.colorScheme.dividerLine, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            name,
            style = MiuixTheme.textStyles.main,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

/**
 * 纯信息行（无点击、无箭头）。用 miuix 的 BasicComponent —— 即 ArrowPreference 的底层组件，
 * 只是不传 endActions，因此不会画右侧箭头。视觉与其它 preference 行完全一致。
 */
@Composable
private fun InfoRow(title: String, summary: String? = null) {
    BasicComponent(
        title = title,
        summary = summary,
        onClick = null,
    )
}

/** miuix ThemePaletteStyle.entries 的显示名（顺序必须与枚举一致）。 */
private val PALETTE_STYLE_OPTIONS = listOf(
    "Tonal Spot", "Neutral", "Vibrant", "Expressive", "Rainbow",
    "Fruit Salad", "Monochrome", "Fidelity", "Content",
)

/** miuix ThemeColorSpec.entries 的显示名。 */
private val COLOR_SPEC_OPTIONS = listOf("Material 3 (2021)", "Material 3 Expressive (2025)")
