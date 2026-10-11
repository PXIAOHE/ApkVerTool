package com.seagull.avt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagull.avt.ui.components.AvtButton
import com.seagull.avt.ui.components.AvtCard
import com.seagull.avt.ui.components.AvtEmphasis
import com.seagull.avt.ui.components.AvtField
import com.seagull.avt.ui.components.BulletLine
import com.seagull.avt.ui.components.FoldGroup
import com.seagull.avt.ui.components.LogPane
import com.seagull.avt.ui.components.Note
import com.seagull.avt.ui.components.PageColumn
import com.seagull.avt.ui.components.SectionLabel
import com.seagull.avt.ui.icons.IcoArticle
import com.seagull.avt.ui.icons.IcoCleaningServices
import com.seagull.avt.ui.icons.IcoDeleteSweep
import com.seagull.avt.ui.icons.IcoDownload
import com.seagull.avt.ui.icons.IcoFileOpen
import com.seagull.avt.ui.icons.IcoInventory2
import com.seagull.avt.ui.icons.IcoMonitorHeart
import com.seagull.avt.ui.icons.IcoReceiptLong
import com.seagull.avt.ui.icons.IcoStraighten
import com.seagull.avt.ui.icons.IcoVerifiedUser
import kotlinx.coroutines.launch

private val MAIN_TABS = listOf(
    AvtTab("改写", Icons.Filled.Edit),
    AvtTab("条目", IcoInventory2),
    AvtTab("体检", IcoMonitorHeart),
    AvtTab("日志", IcoReceiptLong),
)

@Composable
fun MainScreen(state: MainUiState, host: MainHost) {
    val pagerState = rememberPagerState(pageCount = { MAIN_TABS.size })
    val scope = rememberCoroutineScope()

    // 业务侧请求跳页（例如「开始修改」后跳日志页），消费一次即清空
    LaunchedEffect(state.pageRequest) {
        val target = state.pageRequest ?: return@LaunchedEffect
        state.pageRequest = null
        if (target in MAIN_TABS.indices) pagerState.animateScrollToPage(target)
    }

    AvtScaffold(
        tabs = MAIN_TABS,
        pagerState = pagerState,
        title = { "APK 版本号修改器" },
        badges = listOf(state.fileCount, state.entryCount, 0, 0),
        snackbarHostState = state.snackbar,
        onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
        topBarActions = {
            IconButton(onClick = { host.openSettings() }) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        },
        pageContent = { page, inner ->
            when (page) {
                0 -> RewritePage(state, host, inner)
                1 -> EntryPage(state, host, inner)
                2 -> InspectPage(state, host, inner)
                else -> LogPage(state, host, inner)
            }
        },
    )

    // 重要结果提示（安装成功 / 被系统拒绝 / 失败）
    val alertTitle = state.alertTitle
    if (alertTitle != null) {
        AlertDialog(
            onDismissRequest = { state.dismissAlert() },
            title = { Text(alertTitle) },
            text = { Text(state.alertMessage) },
            confirmButton = {
                TextButton(onClick = { state.dismissAlert() }) { Text("知道了") }
            },
        )
    }

    // 「未签名APK需要核心破解」——root 静默安装确认（旧版 askRootInstall 的双按钮对话框）
    if (state.rootPrompt) {
        AlertDialog(
            onDismissRequest = { state.rootPrompt = false },
            title = { Text("未签名APK需要核心破解") },
            text = {
                Text(
                    "当前APK没有重新签名（MT 式输出），系统安装器会直接拒绝。\n\n" +
                        "用 root 静默安装的前提：\n" +
                        "1. 设备已 root，且本应用能拿到 su 授权；\n" +
                        "2. 系统已装核心破解（CorePatch / 核心破解类模块）并禁用了 APK 签名校验。\n\n" +
                        "缺第 2 条时，即使拿到 root 也会报 INSTALL_PARSE_FAILED_NO_CERTIFICATES 之类的错误。\n\n" +
                        "是否尝试用 root 安装？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.rootPrompt = false
                    host.confirmRootInstall()
                }) { Text("尝试 root 安装") }
            },
            dismissButton = {
                TextButton(onClick = { state.rootPrompt = false }) { Text("取消") }
            },
        )
    }

    // 「要替换/新增的条目名」输入
    if (state.askEntryName) {
        AlertDialog(
            onDismissRequest = { state.askEntryName = false },
            title = { Text("要替换/新增的条目名") },
            text = {
                AvtField(
                    label = "例如 assets/config.json",
                    value = state.entryNameDraft,
                    onValueChange = { state.entryNameDraft = it },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = state.entryNameDraft.isNotBlank(),
                    onClick = {
                        val name = state.entryNameDraft.trim()
                        state.askEntryName = false
                        state.entryNameDraft = ""
                        host.addReplaceEntry(name)
                    },
                ) { Text("选文件") }
            },
            dismissButton = {
                TextButton(onClick = { state.askEntryName = false }) { Text("取消") }
            },
        )
    }
}

/** 每页统一的滚动容器见 components/PageColumn。 */

// ---------------------------------------------------------------- 第 1 页：改写

@Composable
private fun RewritePage(state: MainUiState, host: MainHost, inner: PaddingValues) {
    PageColumn(inner, compact = true) {
        item {
            AvtCard {
                SectionLabel("选择 APK / 多包容器")
                AvtButton(
                    text = "选择文件…",
                    onClick = host::pickFiles,
                    icon = IcoFileOpen,
                    modifier = Modifier.fillMaxWidth(),
                )
                Note("支持 .apk 单包、多选批量，以及 .xapk / .apks / split 压缩包（容器内每个子包都会处理）。")
                Text(
                    text = state.sourceText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }
        }
        item {
            AvtCard {
                SectionLabel("清单改写（留空 = 不改）")
                FoldGroup(
                    title = "版本号",
                    open = state.foldOpen["ver"] == true,
                    onToggle = { state.foldOpen["ver"] = !(state.foldOpen["ver"] ?: true) },
                ) {
                    AvtField(
                        label = "新 versionName",
                        value = state.versionName,
                        onValueChange = { state.versionName = it },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AvtField(
                            label = "新 versionCode",
                            value = state.versionCode,
                            onValueChange = { state.versionCode = it },
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                        AvtField(
                            label = "versionCodeMajor",
                            value = state.versionCodeMajor,
                            onValueChange = { state.versionCodeMajor = it },
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                FoldGroup(
                    title = "安卓SDK版本",
                    open = state.foldOpen["sdk"] == true,
                    onToggle = { state.foldOpen["sdk"] = !(state.foldOpen["sdk"] ?: false) },
                ) {
                    AvtField(
                        label = "最小SDK",
                        value = state.minSdk,
                        onValueChange = { state.minSdk = it },
                        numeric = true,
                    )
                    AvtField(
                        label = "目标SDK",
                        value = state.targetSdk,
                        onValueChange = { state.targetSdk = it },
                        numeric = true,
                    )
                    Note("收起时这两项不参与改写；展开后留空同样视为不改。")
                }
                FoldGroup(
                    title = "应用标识（应用名 / 包名）",
                    open = state.foldOpen["id"] == true,
                    onToggle = { state.foldOpen["id"] = !(state.foldOpen["id"] ?: false) },
                ) {
                    AvtField(
                        label = "新应用名（android:label）",
                        value = state.appLabel,
                        onValueChange = { state.appLabel = it },
                    )
                    AvtField(
                        label = "新包名（联动 dex / arsc / so）",
                        value = state.packageName,
                        onValueChange = { state.packageName = it },
                    )
                    Note(
                        "改包名会同步改写 AndroidManifest 字符串池、dex 字符串表、resources.arsc 全局池；" +
                            "lib 下的 .so 仅在长度相等时原地替换。"
                    )
                }
            }
        }
        item {
            AvtCard {
                AvtButton(
                    text = "开始修改",
                    onClick = host::startRun,
                    icon = Icons.Filled.PlayArrow,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvtButton(
                        text = "安装",
                        onClick = host::install,
                        icon = IcoDownload,
                        enabled = state.canInstall,
                        modifier = Modifier.weight(1f),
                    )
                    AvtButton(
                        text = "分享",
                        onClick = host::share,
                        icon = Icons.Filled.Share,
                        emphasis = AvtEmphasis.Tonal,
                        enabled = state.canShare,
                        modifier = Modifier.weight(1f),
                    )
                }
                Note(state.installHint)
            }
        }
        item {
            AvtCard {
                SectionLabel("提示")
                BulletLine("输出位置在「设置」的「输出」页里指定。")
                BulletLine("重签名开关与签名方案在「设置」的「签名」页里。")
                BulletLine("改动过的 APK 想装上设备就必须重新签名。")
                BulletLine("执行过程与结果都在「日志」页。")
            }
        }
    }
}

// ---------------------------------------------------------------- 第 2 页：条目

@Composable
private fun EntryPage(state: MainUiState, host: MainHost, inner: PaddingValues) {
    PageColumn(inner, compact = true) {
        item {
            AvtCard {
                SectionLabel("删除条目")
                Note("每行一个条目名，或以 / 结尾的目录前缀，例如 lib/armeabi-v7a/")
                AvtField(
                    label = "要删除的条目（每行一个）",
                    value = state.dropText,
                    onValueChange = { state.dropText = it },
                    lines = 5,
                )
            }
        }
        item {
            AvtCard {
                SectionLabel("替换 / 新增条目")
                AvtButton(
                    text = "添加替换条目…",
                    onClick = { state.askEntryName = true },
                    icon = Icons.Filled.Add,
                    emphasis = AvtEmphasis.Tonal,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "待替换: ${state.replaceEntries.size} 个",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.replaceEntries.forEach { entry ->
                        Text(
                            text = "${entry.name}  (${entry.size} 字节)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                AvtButton(
                    text = "清空替换列表",
                    onClick = host::clearReplaceEntries,
                    icon = IcoDeleteSweep,
                    emphasis = AvtEmphasis.Outline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 第 3 页：体检

@Composable
private fun InspectPage(state: MainUiState, host: MainHost, inner: PaddingValues) {
    PageColumn(inner, compact = true) {
        item {
            AvtCard {
                SectionLabel("对第一个已选文件做检查")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvtButton(
                        text = "体检报告",
                        onClick = host::showInfo,
                        icon = IcoArticle,
                        modifier = Modifier.weight(1f),
                    )
                    AvtButton(
                        text = "对齐体检",
                        onClick = host::showAlign,
                        icon = IcoStraighten,
                        emphasis = AvtEmphasis.Tonal,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvtButton(
                        text = "验签详情",
                        onClick = host::showVerify,
                        icon = IcoVerifiedUser,
                        emphasis = AvtEmphasis.Tonal,
                        modifier = Modifier.weight(1f),
                    )
                    AvtButton(
                        text = "条目列表",
                        onClick = host::showEntries,
                        icon = IcoInventory2,
                        emphasis = AvtEmphasis.Tonal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            AvtCard {
                SectionLabel("当前选中")
                Text(
                    text = state.infoText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }
        }
        item {
            AvtCard {
                Note(
                    "体检报告包含：包名 / 版本 / SDK / 条目数 / DEX 数 / ABI / 原生库数 / 权限统计 / " +
                        "加固壳识别 / 框架识别 / 签名者证书指纹与应用名（从 resources.arsc 解析）。"
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 第 4 页：日志

@Composable
private fun LogPage(state: MainUiState, host: MainHost, inner: PaddingValues) {
    PageColumn(inner, compact = true) {
        item {
            AvtCard {
                SectionLabel("执行日志")
                LogPane(logs = if (state.logs.isEmpty()) listOf("等待任务…") else state.logs)
            }
        }
        item {
            AvtButton(
                text = "清空日志",
                onClick = host::clearLogs,
                icon = IcoCleaningServices,
                emphasis = AvtEmphasis.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
