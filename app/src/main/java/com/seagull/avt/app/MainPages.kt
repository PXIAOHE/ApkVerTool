// 主页四分页：改写 / 条目 / 体检 / 日志。
// 表单交互对齐旧 Java 版；视觉全量 miuix（Card / TextField / SwitchPreference / OverlayDialog）。
package com.seagull.avt.app

import android.content.Intent
import java.io.File
import java.io.FileOutputStream
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import com.seagull.avt.md3.icons.IcoArticle
import com.seagull.avt.md3.icons.IcoCleaningServices
import com.seagull.avt.md3.icons.IcoDeleteSweep
import com.seagull.avt.md3.icons.IcoDownload
import com.seagull.avt.md3.icons.IcoFileOpen
import com.seagull.avt.md3.icons.IcoFolderOpen
import com.seagull.avt.md3.icons.IcoInventory2
import com.seagull.avt.md3.icons.IcoSave
import com.seagull.avt.md3.icons.IcoStraighten
import com.seagull.avt.md3.icons.IcoVpnKey
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.seagull.avt.core.ApkInfo
import com.seagull.avt.core.SplitBundle
import com.seagull.avt.core.SignUtil
import com.seagull.avt.core.AxmlPatch
import com.seagull.avt.core.ZipAlignWriter
import com.seagull.avt.ui.MiuixFoldGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ───────────────────────── 第 1 页：改写 ─────────────────────────

@Composable
fun RewritePage(padding: PaddingValues, scrollBehavior: ScrollBehavior? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceText by remember { mutableStateOf("尚未选择文件") }
    var versionName by remember { mutableStateOf("") }
    var versionCode by remember { mutableStateOf("") }
    var versionCodeMajor by remember { mutableStateOf("") }
    var minSdk by remember { mutableStateOf("") }
    var targetSdk by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var newPackage by remember { mutableStateOf("") }
    // 图标：选中的原图字节 + 展示名（null = 不改图标）
    var iconBytes by remember { mutableStateOf<ByteArray?>(null) }
    var iconName by remember { mutableStateOf("") }
    var dropEntries by remember { mutableStateOf("") }
    var installEnabled by remember { mutableStateOf(false) }
    var shareEnabled by remember { mutableStateOf(false) }
    var installHint by remember { mutableStateOf("安装：已签名APK走系统安装器；未签名APK需要 root + 核心破解。") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { importUris(context, uris) }
            }.onSuccess { info ->
                sourceText = info.first
                info.second?.let { manifest ->
                    versionName = manifest.versionName ?: ""
                    versionCode = manifest.versionCode?.toString() ?: ""
                    minSdk = manifest.minSdk?.toString() ?: ""
                    targetSdk = manifest.targetSdk?.toString() ?: ""
                    newPackage = manifest.packageName ?: ""
                }
                AvtState.appendLog("已读入 ${AvtState.queue.size} 个文件")
            }.onFailure {
                AvtState.appendLog("读取失败: $it")
                Toast.makeText(context, "读取失败: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 图标选择：只收图片；读完当场试解码，坏图立刻拒（不让它在「开始修改」时才炸）
    val iconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    AvtIo.readAll(context.contentResolver.openInputStream(uri)!!)
                }
                if (!AvtIcon.readable(bytes)) throw IllegalStateException("这个文件不是能识别的图片")
                bytes to AvtIo.displayName(context, uri)
            }.onSuccess { picked ->
                iconBytes = picked.first
                iconName = picked.second
                AvtState.appendLog("已选图标: ${picked.second}（${AvtIo.human(picked.first.size.toLong())}）")
            }.onFailure {
                Toast.makeText(context, "读取图标失败: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
            ),
    ) {
        // 内容延伸到栏后（毛玻璃采样真实内容）；Spacer 占位保证首末项能拉出栏区
        Spacer(Modifier.height(padding.calculateTopPadding()))
        SmallTitle(text = "① 选择 APK / 多包容器")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Button(
                onClick = {
                    picker.launch(
                        arrayOf(
                            "application/vnd.android.package-archive",
                            "application/zip",
                            "application/octet-stream",
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { BtnLabel(IcoFileOpen, "选择文件…") }
            Spacer(Modifier.height(8.dp))
            Text(
                text = sourceText,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "支持 .apk 单包、多选批量，以及 .xapk / .apks / split 压缩包",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        SmallTitle(text = "② 清单改写（留空 = 不改）")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            // 折叠状态：与 MD3 版一致的默认（版本号开 / SDK 与应用标识收）；
            // 收起的分组整组不参与改写（提交时只读展开组）
            // Miuix 风格 = 平铺全展开；MD3 风格 = 折叠（版本号开 / 其余收）
            var foldVer by remember { mutableStateOf(true) }
            var foldSdk by remember { mutableStateOf(UiConfigState.uiStyle == 1) }
            var foldId by remember { mutableStateOf(UiConfigState.uiStyle == 1) }

            MiuixFoldGroup(
                title = "版本号",
                open = foldVer,
                onToggle = { foldVer = !foldVer },
            ) {
                com.seagull.avt.ui.TextField(
                    value = versionName,
                    onValueChange = { versionName = it },
                    label = "新 versionName",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.seagull.avt.ui.TextField(
                        value = versionCode,
                        onValueChange = { versionCode = it.filter { c -> c.isDigit() }.take(19) },
                        label = "新 versionCode",
                        modifier = Modifier.weight(1f),
                    )
                    com.seagull.avt.ui.TextField(
                        value = versionCodeMajor,
                        onValueChange = { versionCodeMajor = it.filter { c -> c.isDigit() }.take(10) },
                        label = "versionCodeMajor",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
            MiuixFoldGroup(
                title = "安卓 SDK 版本",
                open = foldSdk,
                onToggle = { foldSdk = !foldSdk },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.seagull.avt.ui.TextField(
                        value = minSdk,
                        onValueChange = { minSdk = it.filter { c -> c.isDigit() }.take(3) },
                        label = "最小SDK",
                        modifier = Modifier.weight(1f),
                    )
                    com.seagull.avt.ui.TextField(
                        value = targetSdk,
                        onValueChange = { targetSdk = it.filter { c -> c.isDigit() }.take(3) },
                        label = "目标SDK",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "收起时这两项不参与改写；展开后留空同样视为不改。",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
            MiuixFoldGroup(
                title = "应用标识（应用名 / 包名）",
                open = foldId,
                onToggle = { foldId = !foldId },
            ) {
                com.seagull.avt.ui.TextField(
                    value = label,
                    onValueChange = { label = it },
                    label = "新应用名（android:label）",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                com.seagull.avt.ui.TextField(
                    value = newPackage,
                    onValueChange = { newPackage = it },
                    label = "新包名（联动 dex / arsc / so）",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { iconPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f),
                    ) { BtnLabel(IcoFileOpen, if (iconBytes == null) "选择新图标…" else "换一个图标…") }
                    if (iconBytes != null) {
                        Button(
                            onClick = {
                                iconBytes = null
                                iconName = ""
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("清除图标") }
                    }
                }
                if (iconBytes != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "图标: $iconName · 每个密度条目按原尺寸重采样，自适应图标跟到 foreground / background",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "改包名会同步改写 AndroidManifest 字符串池、dex 字符串表、resources.arsc 全局池",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }

        SmallTitle(text = "③ 执行")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        runPatchJob(
                            context = context,
                            versionName = versionName,
                            versionCode = versionCode,
                            versionCodeMajor = versionCodeMajor,
                            minSdk = minSdk,
                            targetSdk = targetSdk,
                            label = label,
                            newPackage = newPackage,
                            dropEntries = dropEntries,
                            iconImage = iconBytes,
                        )
                    }
                },
                enabled = !AvtState.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { if (AvtState.busy) Text("任务进行中…") else BtnLabel(Icons.Filled.PlayArrow, "开始修改 →") }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { doInstall(context) },
                    enabled = installEnabled,
                    modifier = Modifier.weight(1f),
                ) { BtnLabel(IcoDownload, "安装") }
                Button(
                    onClick = { doShare(context) },
                    enabled = shareEnabled,
                    modifier = Modifier.weight(1f),
                ) { BtnLabel(Icons.Filled.Share, "分享") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                installHint,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        LaunchedEffect(AvtState.lastOutput, AvtState.lastSafUri, AvtState.lastOutputSigned) {
            val hasOut = AvtState.lastOutput != null || AvtState.lastSafUri != null
            installEnabled = hasOut
            shareEnabled = hasOut
            installHint = when {
                AvtState.lastOutput == null && AvtState.lastSafUri == null ->
                    "安装：已签名APK走系统安装器；未签名APK需要 root + 核心破解。"
                AvtState.lastOutputSigned ->
                    "安装：APK已签名，点「安装」交给系统安装器。"
                else ->
                    "安装：APK未签名，点「安装」将走 root 静默安装，需要核心破解禁用签名校验。"
            }
        }
        // 日志也同步显示到改写页（旧版行为）
        LaunchedEffect(AvtState.log.size) {
            val last = AvtState.log.lastOrNull()
            if (last != null) sourceText = last
        }

        // 对齐上游 v2.1.0：提示卡（4 条要点）
        SmallTitle(text = "提示")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            HintLine("输出位置在「设置」的「输出」页里指定。")
            HintLine("重签名开关与签名方案在「设置」的「签名」页里。")
            HintLine("改动过的 APK 想装上设备就必须重新签名。")
            HintLine("执行过程与结果都在「日志」页。")
        }
        Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
    }
}

/** 一行提示：圆点 + 文本（对齐上游 BulletLine）。 */
@Composable
private fun HintLine(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
        Text(
            "•",
            color = MiuixTheme.colorScheme.primary,
            style = MiuixTheme.textStyles.main,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(text, style = MiuixTheme.textStyles.main)
    }
}

/** 读入 SAF uri 到私有缓存并回填清单信息 */
internal suspend fun importUris(
    context: android.content.Context,
    uris: List<Uri>,
): Pair<String, AxmlPatch.Info?> {
    AvtState.reset()
    val cr = context.contentResolver
    for ((i, u) in uris.withIndex()) {
        val name = AvtIo.displayName(context, u)
        val f = File(context.cacheDir, "in_$i" + AvtIo.suffix(name))
        AvtIo.copy(cr.openInputStream(u) ?: continue, FileOutputStream(f))
        AvtState.queue.add(f)
        AvtState.queueNames.add(name)
    }
    val sb = StringBuilder()
    sb.append("已选择 ").append(AvtState.queue.size).append(" 个文件")
    for (n in AvtState.queueNames.take(6)) sb.append('\n').append("· ").append(n)
    if (AvtState.queueNames.size > 6) {
        sb.append('\n').append("· …还有 ").append(AvtState.queueNames.size - 6).append(" 个")
    }
    var info: AxmlPatch.Info? = null
    val first = AvtState.queue.firstOrNull()
    if (first != null) {
        runCatching {
            if (com.seagull.avt.core.SplitBundle.isBundle(first)) {
                sb.append('\n').append("类型: 多包容器")
            } else {
                val mi = AxmlPatch.read(ZipAlignWriter.extractEntry(first, "AndroidManifest.xml"))
                info = mi
                sb.append('\n').append("类型: 单 APK")
                sb.append('\n').append("包名 ").append(mi.packageName)
                sb.append('\n').append("版本 ").append(mi.versionName).append(" (").append(mi.versionCode).append(")")
            }
        }.onFailure {
            sb.append('\n').append("类型识别失败: ").append(it.message)
        }
    }
    AvtState.manifestInfo = sb.toString()
    return sb.toString() to info
}

/** 组 Job 并后台执行 */
internal suspend fun runPatchJob(
    context: android.content.Context,
    versionName: String, versionCode: String, versionCodeMajor: String,
    minSdk: String, targetSdk: String, label: String, newPackage: String,
    dropEntries: String,
    iconImage: ByteArray? = null,
) {
    if (AvtState.busy) {
        Toast.makeText(context, "任务进行中", Toast.LENGTH_SHORT).show()
        return
    }
    if (AvtState.queue.isEmpty()) {
        Toast.makeText(context, "先选择文件", Toast.LENGTH_SHORT).show()
        return
    }
    val job = AvtRunner.buildJob(
        versionName, versionCode, versionCodeMajor, minSdk, targetSdk, label, newPackage,
        dropEntries.split("\n"),
        iconImage = iconImage,
        resign = AvtPrefs.resign(context),
        v1 = AvtPrefs.scheme(context, "scheme_v1", true),
        v2 = AvtPrefs.scheme(context, "scheme_v2", true),
        v3 = AvtPrefs.scheme(context, "scheme_v3", true),
        v4 = AvtPrefs.scheme(context, "scheme_v4", false),
        p12 = readSignKey(context),
        pass = AvtPrefs.keyPass(context).toCharArray().let { if (it.isEmpty()) null else it },
    )
    if (!AvtRunner.hasAnyChange(job)) {
        Toast.makeText(context, "没有任何改动", Toast.LENGTH_SHORT).show()
        return
    }
    AvtState.busy = true
    AvtState.clearLog()
    AvtState.lastSafUri = null
    try {
        withContext(Dispatchers.IO) { AvtRunner.runQueue(context, job) }
    } finally {
        AvtState.busy = false
    }
}

/** 读签名密钥：自定义 > 内置 assets/signkey.p12 */
internal fun readSignKey(context: android.content.Context): ByteArray? {
    return try {
        if (AvtPrefs.useCustomKey(context)) {
            val f = File(context.filesDir, "custom_key.p12")
            if (f.exists()) AvtIo.readAll(f.inputStream())
            else AvtIo.readAll(context.assets.open("signkey.p12"))
        } else {
            AvtIo.readAll(context.assets.open("signkey.p12"))
        }
    } catch (_: Throwable) {
        null
    }
}

// ───────────────────────── 第 2 页：条目 ─────────────────────────

@Composable
fun EntriesPage(padding: PaddingValues, scrollBehavior: ScrollBehavior? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dropEntries by remember { mutableStateOf("") }
    var entriesText by remember { mutableStateOf("待替换: 0 个") }

    // 添加条目名对话框（声明在 filePicker 之前：Kotlin 局部变量先声明后引用）
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingEntryName by remember { mutableStateOf<String?>(null) }
    var entryInput by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val name = pendingEntryName
        if (uri == null || name == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    AvtIo.readAll(context.contentResolver.openInputStream(uri)!!)
                }
            }.onSuccess { bytes ->
                AvtState.addPut(name, bytes)
                AvtState.appendLog("待替换条目: $name  (${bytes.size} 字节)")
            }.onFailure {
                AvtState.appendLog("读取替换文件失败: $it")
            }
        }
    }

    LaunchedEffect(AvtState.putMapVersion) {
        val sb = StringBuilder("待替换: ${AvtState.putMap.size} 个")
        for ((k, v) in AvtState.putMap) {
            sb.append('\n').append("  ✎ ").append(k).append("  (").append(v.size).append(" 字节)")
        }
        entriesText = sb.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
            ),
    ) {
        Spacer(Modifier.height(padding.calculateTopPadding()))
        SmallTitle(text = "删除条目")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                "每行一个条目名，或以 / 结尾的目录前缀，例如 lib/armeabi-v7a/",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            com.seagull.avt.ui.TextField(
                value = dropEntries,
                onValueChange = { dropEntries = it },
                label = "要删除的条目（每行一个）",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SmallTitle(text = "替换 / 新增条目")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Button(
                onClick = { showAddDialog = true; entryInput = "" },
                modifier = Modifier.fillMaxWidth(),
            ) { BtnLabel(Icons.Filled.Add, "添加替换条目…") }
            Spacer(Modifier.height(8.dp))
            Text(
                entriesText,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    AvtState.clearPut()
                    AvtState.appendLog("已清空替换列表")
                },
                modifier = Modifier.fillMaxWidth(),
            ) { BtnLabel(IcoDeleteSweep, "清空替换列表") }
        }
        Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
    }

    if (showAddDialog) {
        top.yukonga.miuix.kmp.overlay.OverlayDialog(
            show = true,
            title = "要替换/新增的条目名",
            summary = "例如 assets/config.json 或 lib/armeabi-v7a/libfoo.so",
            largeScreen = true,
            onDismissRequest = { showAddDialog = false },
        ) {
            com.seagull.avt.ui.TextField(
                value = entryInput,
                onValueChange = { entryInput = it },
                label = "条目名",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { showAddDialog = false },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val n = entryInput.trim()
                        if (n.isEmpty()) {
                            Toast.makeText(context, "条目名不能为空", Toast.LENGTH_SHORT).show()
                        } else {
                            pendingEntryName = n
                            showAddDialog = false
                            filePicker.launch(arrayOf("*/*"))
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("选文件") }
            }
        }
    }
}

// ───────────────────────── 第 3 页：体检 ─────────────────────────

@Composable
fun InspectPage(padding: PaddingValues, scrollBehavior: ScrollBehavior? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = AvtState.manifestInfo ?: "尚未选择文件"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
            ),
    ) {
        Spacer(Modifier.height(padding.calculateTopPadding()))
        SmallTitle(text = "对第一个已选文件做检查")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { runInspect(context) { ApkInfo.analyze(it).describe() } },
                    enabled = !AvtState.busy,
                    modifier = Modifier.weight(1f),
                ) { BtnLabel(IcoArticle, "体检报告") }
                Button(
                    onClick = { runInspect(context) { ZipAlignWriter.alignReport(it).describe() } },
                    enabled = !AvtState.busy,
                    modifier = Modifier.weight(1f),
                ) { BtnLabel(IcoStraighten, "对齐体检") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        runInspect(context) { f ->
                            val minSdk = runCatching {
                                AxmlPatch.read(
                                    ZipAlignWriter.extractEntry(f, "AndroidManifest.xml"),
                                ).minSdk
                            }.getOrNull() ?: 0
                            com.seagull.avt.core.SignUtil.verifyDetail(f, minSdk).describe()
                        }
                    },
                    enabled = !AvtState.busy,
                    modifier = Modifier.weight(1f),
                ) { BtnLabel(IcoVpnKey, "验签详情") }
                Button(
                    onClick = {
                        runInspect(context) { f ->
                            val names = ZipAlignWriter.listNames(f)
                            buildString {
                                append("──── 条目列表 (${names.size}) ────")
                                names.take(200).forEach { append('\n').append(it) }
                                if (names.size > 200) append("\n…还有 ${names.size - 200} 个条目")
                            }
                        }
                    },
                    enabled = !AvtState.busy,
                    modifier = Modifier.weight(1f),
                ) { BtnLabel(IcoInventory2, "条目列表") }
            }
        }

        SmallTitle(text = "当前选中")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                info,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                "体检报告包含：包名 / 版本 / SDK / 条目数 / DEX 数 / ABI / 原生库数 / 权限统计 / " +
                        "加固壳识别 / 框架识别 / 签名者证书指纹与应用名（从 resources.arsc 解析）。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
    }
}

internal fun runInspect(context: android.content.Context, block: (File) -> String) {
    val f = AvtState.queue.firstOrNull()
    if (f == null) {
        Toast.makeText(context, "先选择文件", Toast.LENGTH_SHORT).show()
        return
    }
    Thread {
        runCatching { block(f) }
            .onSuccess { AvtState.appendLog(it) }
            .onFailure { AvtState.appendLog("检查失败: $it") }
    }.start()
}

// ───────────────────────── 第 4 页：日志 ─────────────────────────

@Composable
fun LogPage(padding: PaddingValues, scrollBehavior: ScrollBehavior? = null) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Spacer(Modifier.height(padding.calculateTopPadding()))
        SmallTitle(text = "执行日志")
        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .padding(horizontal = 16.dp),
            ) {
                if (AvtState.log.isEmpty()) {
                    item {
                        Text(
                            "等待任务…",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.subtitle,
                        )
                    }
                }
                items(AvtState.log.size) { i ->
                    Text(
                        AvtState.log[i],
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { AvtState.clearLog() },
                modifier = Modifier.fillMaxWidth(),
            ) { BtnLabel(IcoCleaningServices, "清空日志") }
        }
        Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
    }
}

// ───────────────────────── 安装 / 分享 ─────────────────────────

internal fun doInstall(context: android.content.Context) {
    if (AvtState.lastOutput == null && AvtState.lastSafUri == null) {
        Toast.makeText(context, "还没有输出文件", Toast.LENGTH_SHORT).show()
        return
    }
    if (AvtState.lastOutputSigned) {
        AvtState.appendLog("安装：APK已签名 → 交给系统安装器")
        try {
            val u = AvtState.lastOutput?.let { publishForExternal(context, it) } ?: AvtState.lastSafUri
            if (u == null) {
                Toast.makeText(context, "APK不在本地，请到输出目录手动安装", Toast.LENGTH_LONG).show()
                return
            }
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(u, "application/vnd.android.package-archive")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (t: Throwable) {
            AvtState.appendLog("安装失败: $t")
            Toast.makeText(context, "安装失败，可到设置页确认系统安装权限", Toast.LENGTH_LONG).show()
        }
    } else {
        AvtState.appendLog("安装：APK未重签 → 只能 root 静默安装（需要核心破解）")
        doRootInstall(context)
    }
}

internal fun publishForExternal(context: android.content.Context, apk: File): Uri {
    val dst = File(context.cacheDir, "share_" + AvtIo.sanitize(apk.name))
    AvtIo.copy(apk.inputStream(), FileOutputStream(dst))
    return Uri.parse("content://" + AvtVersion.PROVIDER_AUTHORITY + "/" + dst.name)
}

internal fun doRootInstall(context: android.content.Context) {
    Thread {
        val apk = localCopyForRoot(context)
        if (apk == null) {
            AvtState.appendLog("✗ 拿不到APK文件（APK只在指定目录里），请到输出目录手动安装")
            return@Thread
        }
        AvtState.appendLog("准备 root 安装: ${apk.name}")
        val out = execSu("pm install -r -d --user 0 '${apk.absolutePath.replace("'", "'\\''")}'", 120000)
        AvtState.appendLog(AvtIo.condense(out))
        val low = out.lowercase()
        val ok = low.contains("success") ||
                (out.contains("(exit=0)") && !low.contains("failure") && !low.contains("exception"))
        AvtState.appendLog(if (ok) "✓ root 安装成功" else "✗ root 安装失败，详见上方输出")
    }.start()
}

internal fun localCopyForRoot(context: android.content.Context): File? {
    AvtState.lastOutput?.let { if (it.exists()) return it }
    val uri = AvtState.lastSafUri ?: return null
    return try {
        val dst = File(context.cacheDir, "install_${System.currentTimeMillis()}.apk")
        AvtIo.copy(context.contentResolver.openInputStream(uri)!!, FileOutputStream(dst))
        dst
    } catch (_: Throwable) {
        null
    }
}

private fun execSu(cmd: String, timeoutMs: Int): String {
    return try {
        val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val deadline = System.currentTimeMillis() + timeoutMs
        val buf = java.io.ByteArrayOutputStream()
        val pump = Thread {
            runCatching { AvtIo.copy(proc.inputStream, buf) }
            runCatching { AvtIo.copy(proc.errorStream, buf) }
        }
        pump.start()
        while (System.currentTimeMillis() < deadline) {
            try {
                val code = proc.exitValue()
                pump.join(2000)
                return String(buf.toByteArray(), Charsets.UTF_8) + "\n(exit=$code)"
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(120)
            }
        }
        "超时（$timeoutMs ms）"
    } catch (t: Throwable) {
        "执行失败: $t"
    }
}

internal fun doShare(context: android.content.Context) {
    if (AvtState.lastOutput == null && AvtState.lastSafUri == null) {
        Toast.makeText(context, "还没有输出文件", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val u = AvtState.lastOutput?.let { publishForExternal(context, it) } ?: AvtState.lastSafUri
        if (u == null) {
            Toast.makeText(context, "APK不在本地，请到输出目录手动分享", Toast.LENGTH_LONG).show()
            return
        }
        val i = Intent(Intent.ACTION_SEND)
        i.type = "application/vnd.android.package-archive"
        i.putExtra(Intent.EXTRA_STREAM, u)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(i, "分享 APK"))
    } catch (t: Throwable) {
        AvtState.appendLog("分享失败: $t")
    }
}
