package com.seagull.avt.md3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.seagull.avt.app.ThemeState
import com.seagull.avt.app.UiConfigState
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagull.avt.R
import com.seagull.avt.app.AvtVersion
import com.seagull.avt.app.UpdateChecker
import com.seagull.avt.md3.bars.BottomBarStyle
import com.seagull.avt.md3.components.AvtButton
import com.seagull.avt.md3.components.AvtCard
import com.seagull.avt.md3.components.AvtDropdownRow
import com.seagull.avt.md3.components.AvtEmphasis
import com.seagull.avt.md3.components.AvtField
import com.seagull.avt.md3.components.AvtSwitchRow
import com.seagull.avt.md3.components.InfoPair
import com.seagull.avt.md3.components.Note
import com.seagull.avt.md3.components.PageColumn
import com.seagull.avt.md3.components.SectionLabel
import com.seagull.avt.md3.icons.IcoFolderOpen
import com.seagull.avt.md3.icons.IcoPalette
import com.seagull.avt.md3.icons.IcoReceiptLong
import com.seagull.avt.md3.icons.IcoSave
import com.seagull.avt.md3.icons.IcoUpload
import com.seagull.avt.md3.icons.IcoVpnKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SETTINGS_TABS = listOf(
    AvtTab("外观", IcoPalette),
    AvtTab("签名", IcoVpnKey),
    AvtTab("输出", IcoFolderOpen),
    AvtTab("关于", Icons.Filled.Info),
)

@Composable
fun SettingsScreen(state: SettingsUiState, host: SettingsHost) {
    // 分页下标走应用级共享状态：切主题 / 切 UI 风格时停在当前分页
    val pagerState = rememberPagerState(initialPage = com.seagull.avt.app.AvtNav.settingsPage, pageCount = { SETTINGS_TABS.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
            .collect { com.seagull.avt.app.AvtNav.settingsPage = it }
    }

    AvtScaffold(
        tabs = SETTINGS_TABS,
        pagerState = pagerState,
        title = { "设置" },
        snackbarHostState = state.snackbar,
        // 显式传当前样式：点选时设置页自己的底栏立刻跟着换，不用等 recreate。
        barStyle = state.barStyle,
        onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
        topBarActions = {
            TextButton(onClick = { host.closeSettings() }) { Text("完成") }
        },
        pageContent = { page, inner ->
            when (page) {
                0 -> AppearancePage(state, host, inner)
                1 -> SignPage(state, host, inner)
                2 -> OutputPage(state, host, inner)
                else -> AboutPage(host, inner)
            }
        },
    )
}

// ---------------------------------------------------------------- 外观

@Composable
private fun AppearancePage(state: SettingsUiState, host: SettingsHost, inner: PaddingValues) {
    PageColumn(inner) {
        item {
            AvtCard {
                SectionLabel("主题")
                // 与 MiuixX 完全同一套：单一真源是 ThemeState.mode 这六档，
                // 两套 UI 不再各存一份「跟随系统 / 深色」开关（之前会互相打架）。
                AvtDropdownRow(
                    title = "主题模式",
                    supporting = "「莫奈」= Material You 动态取色，可从种子色推导整套配色",
                    items = MD3_THEME_MODES,
                    selectedIndex = ThemeState.mode,
                    onSelectedIndexChange = { host.setThemeMode(it) },
                )
            }
        }
        item {
            AvtCard {
                SectionLabel("莫奈取色")
                AvtSwitchRow(
                    title = "Material You 动态取色",
                    supporting = "从种子色推导整套配色（与 MiuixX 套同一算法）",
                    checked = ThemeState.isMonet,
                    onCheckedChange = {
                        host.setMonet(it, ThemeState.seedIndex, ThemeState.paletteStyle, ThemeState.colorSpec)
                    },
                )
                Note("开启后可用下面的「种子色 / 配色风格 / 色彩规范」微调。")
            }
        }
        if (ThemeState.isMonet) {
            item {
                AvtCard {
                    SectionLabel("取色参数")
                    AvtDropdownRow(
                        title = "种子色",
                        items = listOf("默认") + com.seagull.avt.app.KeyColors.map { it.first },
                        selectedIndex = ThemeState.seedIndex,
                        onSelectedIndexChange = {
                            host.setMonet(true, it, ThemeState.paletteStyle, ThemeState.colorSpec)
                        },
                    )
                    AvtDropdownRow(
                        title = "配色风格",
                        items = MD3_PALETTE_STYLES,
                        selectedIndex = ThemeState.paletteStyle,
                        onSelectedIndexChange = {
                            host.setMonet(true, ThemeState.seedIndex, it, ThemeState.colorSpec)
                        },
                    )
                    AvtDropdownRow(
                        title = "色彩规范",
                        items = MD3_COLOR_SPECS,
                        selectedIndex = ThemeState.colorSpec,
                        onSelectedIndexChange = {
                            host.setMonet(true, ThemeState.seedIndex, ThemeState.paletteStyle, it)
                        },
                    )
                }
            }
        }
        item {
            AvtCard {
                SectionLabel("界面风格")
                // 与 MiuixX 那边完全同款：同一组两项下拉、同样热切换、同样带折叠组联动
                AvtDropdownRow(
                    title = "全局风格",
                    supporting = "切换全部页面的配色、栏体与改写页布局",
                    items = MD3_UI_STYLES,
                    selectedIndex = UiConfigState.uiStyle,
                    onSelectedIndexChange = { host.setUiStyleIndex(it) },
                )
            }
        }
        item {
            AvtCard {
                SectionLabel("顶栏与底栏")
                AvtSwitchRow(
                    title = "栏体模糊",
                    supporting = "顶栏与底栏的毛玻璃效果",
                    checked = UiConfigState.enableBlur,
                    onCheckedChange = { host.setEnableBlur(it) },
                )
                // 与 MiuixX 一致的交互：开模糊才显示半径滑条
                if (UiConfigState.enableBlur) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "模糊半径",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "%.1f".format(UiConfigState.blurRadius) + "px",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = UiConfigState.blurRadius,
                            onValueChange = { host.setBlurRadius(it) },
                            valueRange = 0f..30f,
                        )
                    }
                }
                // 与 MiuixX 一致：底栏样式也放在这张「顶栏与底栏」卡里（选项文案保持本套）
                AvtDropdownRow(
                    title = "底栏样式",
                    items = BottomBarStyle.entries.map { it.label },
                    selectedIndex = BottomBarStyle.entries.indexOf(state.barStyle),
                    onSelectedIndexChange = { i -> host.setBarStyle(BottomBarStyle.entries[i]) },
                )
            }
        }
    }
}

/** 一个底栏样式选项：单选圆点 + 名称。 */
@Composable
private fun BarStyleRow(
    style: BottomBarStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(6.dp))
            Text(
                text = style.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------- 签名

@Composable
private fun SignPage(state: SettingsUiState, host: SettingsHost, inner: PaddingValues) {
    PageColumn(inner) {
        item {
            AvtCard {
                SectionLabel("重签名")
                AvtSwitchRow(
                    title = "修改后重新签名",
                    checked = state.resign,
                    onCheckedChange = { host.setResign(it) },
                )
                Note("关闭 = MT 式不重签输出：原签名数据原样保留，仅供二次处理，不能直接安装。")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SectionLabel("签名方案")
                AvtSwitchRow("V1（JAR 签名，minSdk < 24 必需）", state.v1, { host.setScheme(0, it) })
                AvtSwitchRow("V2（APK Signature Scheme v2）", state.v2, { host.setScheme(1, it) })
                AvtSwitchRow("V3（密钥轮换支持）", state.v3, { host.setScheme(2, it) })
                AvtSwitchRow("V4（增量安装，额外产出 .idsig）", state.v4, { host.setScheme(3, it) })
                Note("V1 文件始终写入；minSdk ≥ 24 时系统不校验 V1，属正常现象。")
            }
        }
        item {
            AvtCard {
                SectionLabel("签名密钥（PKCS#12）")
                Text(
                    text = state.keyStatus,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                AvtField(
                    label = "密钥密码",
                    value = state.keyPass,
                    onValueChange = { state.keyPass = it },
                    password = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvtButton(
                        text = "导入密钥",
                        onClick = host::pickKey,
                        icon = IcoFolderOpen,
                        modifier = Modifier.weight(1f),
                    )
                    AvtButton(
                        text = "导出",
                        onClick = host::exportKey,
                        icon = IcoUpload,
                        emphasis = AvtEmphasis.Tonal,
                        modifier = Modifier.weight(1f),
                    )
                }
                AvtButton(
                    text = "一键生成新密钥（RSA-2048 自签）",
                    onClick = host::generateKey,
                    icon = IcoVpnKey,
                    emphasis = AvtEmphasis.Tonal,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvtButton(
                        text = "恢复内置",
                        onClick = host::clearKey,
                        icon = Icons.Filled.Refresh,
                        emphasis = AvtEmphasis.Outline,
                        modifier = Modifier.weight(1f),
                    )
                    AvtButton(
                        text = "保存密码",
                        onClick = { host.saveKeyPass(state.keyPass) },
                        icon = IcoSave,
                        emphasis = AvtEmphasis.Outline,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 输出

@Composable
private fun OutputPage(state: SettingsUiState, host: SettingsHost, inner: PaddingValues) {
    PageColumn(inner) {
        item {
            AvtCard {
                SectionLabel("输出位置")
                Text(
                    text = state.outputPath,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 22.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvtButton(
                        text = "选择文件夹",
                        onClick = host::pickOutputDir,
                        icon = IcoFolderOpen,
                        modifier = Modifier.weight(1f),
                    )
                    AvtButton(
                        text = "默认",
                        onClick = host::resetOutputDir,
                        icon = Icons.Filled.Refresh,
                        emphasis = AvtEmphasis.Tonal,
                        modifier = Modifier.weight(1f),
                    )
                }
                Note(
                    "APK先写到应用私有目录，导出到指定目录成功后自动删掉那份中转文件，" +
                        "APK只留在指定目录里；「安装 / 分享」会自动用指定目录里的那份。"
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 关于

@Composable
private fun AboutPage(host: SettingsHost, inner: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    PageColumn(inner) {
        item {
            AvtCard {
                SectionLabel("关于本软件")
                InfoPair(
                    "软件作用",
                    "查看并修改安卓 APK 的 versionName / versionCode / versionCodeMajor / minSdk / targetSdk / " +
                        "包名 / 应用名；可增删 APK 内任意条目；重打包时保持 4 字节与 16KB 对齐；" +
                        "支持 V1/V2/V3/V4 重签名，输出可直接安装的新 APK。",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoPair("多包支持", "XAPK / APKS / split 压缩包，容器内每个子包分别处理并回填元数据。")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoPair(
                    "体检能力",
                    "包名 / 版本 / SDK / DEX / ABI / 权限统计、加固壳识别、框架识别、" +
                        "签名者证书指纹与有效期、应用名（从 resources.arsc 解析）。",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoPair(
                    "版本号",
                    "v" + AvtVersion.NAME + "（code " + AvtVersion.CODE + "）",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoPair("开发者", "Seagull（海鸥）· PXIAOHE")
            }
        }
        item {
            AvtCard {
                SectionLabel("UI设计")
                DesignerRow(
                    avatarRes = R.drawable.avatar_qi,
                    name = "祈",
                    url = "intent://www.coolapk.com/u/396214#Intent;scheme=coolmarket;package=com.coolapk.market;end",
                    onOpen = host::openUrl,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DesignerRow(
                    avatarRes = R.drawable.avatar_xkspeed,
                    name = "XKspeed",
                    url = "intent://www.coolapk.com/u/34717033#Intent;scheme=coolmarket;package=com.coolapk.market;end",
                    onOpen = host::openUrl,
                )
            }
        }
        item {
            AvtCard {
                SectionLabel("更新日志")
                AvtButton(
                    text = "打开更新日志",
                    onClick = host::openChangelog,
                    icon = IcoReceiptLong,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AvtButton(
                    text = if (UpdateChecker.checking) "正在检查…" else "检查更新",
                    onClick = {
                        if (!UpdateChecker.checking) {
                            scope.launch {
                                val info = withContext(Dispatchers.IO) { UpdateChecker.check(ctx) }
                                val msg = when {
                                    info != null -> "发现新版本 v" + info.name
                                    UpdateChecker.lastError != null -> "检查失败：" + UpdateChecker.lastError
                                    else -> "已是最新版本"
                                }
                                android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    icon = Icons.Filled.Refresh,
                    modifier = Modifier.fillMaxWidth(),
                    emphasis = AvtEmphasis.Outline,
                )
            }
        }
        item {
            AvtCard {
                SectionLabel("开源引用")
                Note("apksig（Android Open Source Project）— APK 签名与验签")
                Note("D8（Android Open Source Project）— dex 生成")
                Note("AndroidX（activity / core）+ Jetpack Compose + Material 3 — 界面与边缘到边布局")
                Note("文件 ContentProvider 仍是手写实现，不依赖 androidx")
                LinkRow("Material 3 设计规范：m3.material.io") {
                    host.openUrl("https://m3.material.io/")
                }
                LinkRow("官网：pxiaohe.github.io/ApkVerTool") {
                    host.openUrl(AvtVersion.SITE)
                }
            }
        }
        item {
            AvtCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { host.openUrl("https://github.com/PXIAOHE/ApkVerTool") },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "开源地址：GitHub / PXIAOHE / ApkVerTool",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * 一位设计者：**圆形头像 + 名字**，一人一行，整行可点、跳同一个链接。
 *
 * 头像是构建期从酷安主页抓下来打进 `res/drawable-nodpi/` 的静态图 ——
 * 本应用没有 INTERNET 权限，运行时拉头像走不通。
 * 想换头像就替换那两个 jpg/png，代码不用动。
 *
 * @param avatarRes `R.drawable.avatar_*`
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
            .clip(MaterialTheme.shapes.small)
            .clickable { onOpen(url) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(avatarRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** miuix ThemePaletteStyle.entries 显示名（顺序与枚举一致）。 */
/** 主题模式六档：与 MiuixX 那边的主题模式完全同序（单一真源 = ThemeState.mode）。 */
/** 全局风格两项：顺序与 MiuixX 的「全局风格」一致（0 = MiuixX、1 = MD3）。 */
private val MD3_UI_STYLES = listOf("MiuixX（毛玻璃）", "MD3（Material）")

private val MD3_THEME_MODES = listOf(
    "跟随系统", "浅色", "深色", "莫奈 · 跟随系统", "莫奈 · 浅色", "莫奈 · 深色",
)

private val MD3_PALETTE_STYLES = listOf(
    "Tonal Spot", "Neutral", "Vibrant", "Expressive", "Rainbow",
    "Fruit Salad", "Monochrome", "Fidelity", "Content",
)

/** miuix ThemeColorSpec.entries 显示名。 */
private val MD3_COLOR_SPECS = listOf("Material 3 (2021)", "Material 3 Expressive (2025)")
