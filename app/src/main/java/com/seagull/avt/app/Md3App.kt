// MD3 界面的入口与 Host 实现（单 Activity，与 MiuixX 界面共存）。
// 切换方式：UiConfigState.uiStyle 改变 → AvtApp 重组自动切套（热切换，无需重启）。
// 好处：两套 UI 完全独立，互不干扰；业务函数（AvtRunner / AvtIo / internal helpers）两边复用。
package com.seagull.avt.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.seagull.avt.core.ApkInfo
import com.seagull.avt.core.AxmlPatch
import com.seagull.avt.core.SignUtil
import com.seagull.avt.core.SplitBundle
import com.seagull.avt.core.ZipAlignWriter
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import com.seagull.avt.md3.ChangelogHost
import com.seagull.avt.md3.HINT_INSTALL_IDLE
import com.seagull.avt.md3.HINT_INSTALL_SIGNED
import com.seagull.avt.md3.HINT_INSTALL_UNSIGNED
import com.seagull.avt.md3.ChangelogScreen
import com.seagull.avt.md3.MainHost
import com.seagull.avt.md3.MainScreen
import com.seagull.avt.md3.MainUiState
import com.seagull.avt.md3.SettingsHost
import com.seagull.avt.md3.SettingsScreen
import com.seagull.avt.md3.SettingsUiState
import com.seagull.avt.md3.theme.AvtTheme
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** MD3 界面根：自己的导航栈 + 全套 Host。 */
@Composable
fun Md3App() {
    val context = LocalContext.current
    // 用应用级共享栈：切换 UI 风格/主题时保持当前页面（栈不随组合树重建而丢失）
    val backStack = AvtNav.backStack
    val mainState = remember { MainUiState() }
    val settingsState = remember { SettingsUiState() }
    val scope = rememberCoroutineScope()

    // ── 状态镜像：AvtState（业务真源）→ MD3 屏幕状态 ──
    LaunchedEffect(AvtState.queue.size, AvtState.putMapVersion) {
        mainState.fileCount = AvtState.queue.size
        mainState.entryCount = AvtState.putMap.size
        mainState.setReplaceEntries(
            AvtState.putMap.map { com.seagull.avt.md3.ReplaceEntry(it.key, it.value.size) },
        )
    }
    LaunchedEffect(AvtState.busy) { mainState.busy = AvtState.busy }
    LaunchedEffect(AvtState.log.size) {
        // 日志桥：AvtState.log 的增量搬进 MD3 的日志面板
        val want = AvtState.log.size
        while (mainState.logs.size < want) mainState.log(AvtState.log[mainState.logs.size])
    }
    LaunchedEffect(AvtState.lastOutput, AvtState.lastSafUri, AvtState.lastOutputSigned) {
        val hasOut = AvtState.lastOutput != null || AvtState.lastSafUri != null
        mainState.canInstall = hasOut
        mainState.canShare = hasOut
        mainState.installHint = when {
            !hasOut -> HINT_INSTALL_IDLE
            AvtState.lastOutputSigned -> HINT_INSTALL_SIGNED
            else -> HINT_INSTALL_UNSIGNED
        }
    }
    // 设置状态初始化
    LaunchedEffect(Unit) {
        settingsState.followSystem = ThemeState.mode == ThemeState.MODE_SYSTEM
        settingsState.darkMode = ThemeState.mode == ThemeState.MODE_DARK
        settingsState.monet = ThemeState.isMonet
        settingsState.seedIndex = ThemeState.seedIndex
        settingsState.paletteStyle = ThemeState.paletteStyle
        settingsState.colorSpec = ThemeState.colorSpec
        settingsState.barStyle = com.seagull.avt.md3.bars.BottomBarStyle.read(context)
        settingsState.resign = AvtPrefs.resign(context)
        settingsState.v1 = AvtPrefs.scheme(context, "scheme_v1", true)
        settingsState.v2 = AvtPrefs.scheme(context, "scheme_v2", true)
        settingsState.v3 = AvtPrefs.scheme(context, "scheme_v3", true)
        settingsState.v4 = AvtPrefs.scheme(context, "scheme_v4", false)
        settingsState.keyPass = AvtPrefs.keyPass(context)
        settingsState.keyStatus = keyStatusText(context)
        settingsState.outputPath = outputPathText(context)
    }

    // ── SAF 选择器 ──
    var pendingEntryName by remember { mutableStateOf<String?>(null) }
    val putFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val name = pendingEntryName ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    AvtIo.readAll(context.contentResolver.openInputStream(uri)!!)
                }
            }.onSuccess { bytes ->
                AvtState.addPut(name, bytes)
                mainState.log("待替换条目: $name  (${bytes.size} 字节)")
                mainState.snack("已添加 $name")
            }.onFailure { mainState.log("读取替换文件失败: $it") }
        }
    }
    val pickFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val (info, manifest) = withContext(Dispatchers.IO) { importUris(context, uris) }
                mainState.sourceText = info
                mainState.infoText = info
                manifest?.let { m ->
                    mainState.versionName = m.versionName ?: ""
                    mainState.versionCode = m.versionCode?.toString() ?: ""
                    mainState.minSdk = m.minSdk?.toString() ?: ""
                    mainState.targetSdk = m.targetSdk?.toString() ?: ""
                    mainState.packageName = m.packageName ?: ""
                }
                AvtState.appendLog("已读入 ${AvtState.queue.size} 个文件")
            }.onFailure {
                mainState.log("读取失败: $it")
                Toast.makeText(context, "读取失败: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    // 图标选择：读完当场试解码，坏图立刻拒
    val pickIconLauncher = rememberLauncherForActivityResult(
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
                mainState.iconBytes = picked.first
                mainState.iconName = picked.second
                AvtState.appendLog("已选图标: ${picked.second}（${AvtIo.human(picked.first.size.toLong())}）")
            }.onFailure {
                Toast.makeText(context, "读取图标失败: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val pickKeyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val p12 = withContext(Dispatchers.IO) {
                    AvtIo.readAll(context.contentResolver.openInputStream(uri)!!)
                }
                if (p12.size < 64) throw IllegalStateException("文件太小，不是有效的 PKCS#12")
                withContext(Dispatchers.IO) {
                    File(context.filesDir, "custom_key.p12").writeBytes(p12)
                }
                context.getSharedPreferences("settings", 0).edit()
                    .putBoolean("use_custom", true).apply()
            }.onSuccess {
                settingsState.keyStatus = keyStatusText(context)
                settingsState.snack("已导入自定义密钥（记得填密码并保存）")
            }.onFailure {
                Toast.makeText(context, "导入失败: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val pickOutputDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val take = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, take)
            AvtPrefs.setOutputUri(context, uri.toString())
            settingsState.outputPath = outputPathText(context)
            settingsState.snack("输出位置已设置")
        }.onFailure {
            Toast.makeText(context, "设置失败: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ── Host 实现 ──
    val mainHost = remember {
        object : MainHost {
            override fun openSettings() { backStack.add(Route.Settings) }
            override fun pickFiles() {
                pickFilesLauncher.launch(
                    arrayOf(
                        "application/vnd.android.package-archive",
                        "application/zip",
                        "application/octet-stream",
                    ),
                )
            }

            override fun pickIcon() { pickIconLauncher.launch(arrayOf("image/*")) }

            override fun clearIcon() {
                mainState.iconBytes = null
                mainState.iconName = ""
            }

            override fun addReplaceEntry(name: String) {
                pendingEntryName = name
                putFileLauncher.launch(arrayOf("*/*"))
            }

            override fun clearReplaceEntries() {
                AvtState.clearPut()
                mainState.log("已清空替换列表")
            }

            override fun clearLogs() { mainState.clearLogs() }

            override fun startRun() {
                if (AvtState.busy) { mainState.snack("任务进行中"); return }
                if (AvtState.queue.isEmpty()) { mainState.snack("先选择文件"); return }
                val job = AvtRunner.buildJob(
                    mainState.versionName, mainState.versionCode, mainState.versionCodeMajor,
                    mainState.minSdk, mainState.targetSdk, mainState.appLabel, mainState.packageName,
                    emptyList(),
                    resign = AvtPrefs.resign(context),
                    v1 = AvtPrefs.scheme(context, "scheme_v1", true),
                    v2 = AvtPrefs.scheme(context, "scheme_v2", true),
                    v3 = AvtPrefs.scheme(context, "scheme_v3", true),
                    v4 = AvtPrefs.scheme(context, "scheme_v4", false),
                    p12 = readSignKey(context),
                    pass = AvtPrefs.keyPass(context).toCharArray().let { if (it.isEmpty()) null else it },
                    iconImage = mainState.iconBytes,
                )
                // MD3 版：删除条目输入框的文本直接进 job
                mainState.dropText.split("\n").forEach { line ->
                    val s = line.trim()
                    if (s.isNotEmpty()) job.edit.delete.add(s)
                }
                if (!AvtRunner.hasAnyChange(job)) { mainState.snack("没有任何改动"); return }
                AvtState.busy = true
                mainState.pageRequest = 3
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            AvtRunner.runQueue(context, job) { line ->
                                // 日志走 AvtState.log，桥接器会搬进面板
                                AvtState.appendLog(line)
                            }
                        }
                    } finally {
                        AvtState.busy = false
                    }
                }
            }

            override fun install() { doInstall(context) }
            override fun confirmRootInstall() { doRootInstall(context) }
            override fun share() { doShare(context) }

            override fun showInfo() { inspect(context) { ApkInfo.analyze(it).describe() } }
            override fun showAlign() { inspect(context) { ZipAlignWriter.alignReport(it).describe() } }
            override fun showVerify() {
                inspect(context) { f ->
                    val minSdk = runCatching {
                        AxmlPatch.read(ZipAlignWriter.extractEntry(f, "AndroidManifest.xml")).minSdk
                    }.getOrNull() ?: 0
                    SignUtil.verifyDetail(f, minSdk).describe()
                }
            }

            override fun showEntries() {
                inspect(context) { f ->
                    if (SplitBundle.isBundle(f)) {
                        "──── 容器条目 ────\n" + ZipAlignWriter.listNames(f).joinToString("\n")
                    } else {
                        val names = ZipAlignWriter.listNames(f)
                        buildString {
                            append("──── 条目列表 (${names.size}) ────")
                            names.take(200).forEach { append('\n').append(it) }
                            if (names.size > 200) append("\n…还有 ${names.size - 200} 个条目")
                        }
                    }
                }
            }
        }
    }

    val settingsHost = remember {
        object : SettingsHost {
            override fun closeSettings() { backStack.removeLastOrNull() }

            override fun setThemeMode(mode: Int) {
                // 与 MiuixX 共用同一个真源：直接写 ThemeState.mode
                ThemeState.setMode(context, mode)
                settingsState.followSystem = mode == ThemeState.MODE_SYSTEM
                settingsState.darkMode = mode == ThemeState.MODE_DARK || mode == ThemeState.MODE_MONET_DARK
                settingsState.monet = ThemeState.isMonet
            }


            override fun setBarStyle(style: com.seagull.avt.md3.bars.BottomBarStyle) {
                com.seagull.avt.md3.bars.BottomBarStyle.write(
                    context.getSharedPreferences("settings", 0),
                    style,
                )
                // 同步给 MiuixX 那套：两套 UI 的「底栏样式」是同一件事，
                // 以前各存各的（MiuixX 用 ui_config 两个标志位），所以主页底栏不跟着换。
                when (style) {
                    com.seagull.avt.md3.bars.BottomBarStyle.M3 -> {
                        UiConfigState.useFloatingNavigationBar = false
                    }
                    com.seagull.avt.md3.bars.BottomBarStyle.GLASS_STATIC -> {
                        UiConfigState.useFloatingNavigationBar = true
                        UiConfigState.floatingNavStyle = 0
                    }
                    com.seagull.avt.md3.bars.BottomBarStyle.GLASS_DYNAMIC -> {
                        UiConfigState.useFloatingNavigationBar = true
                        UiConfigState.floatingNavStyle = 1
                    }
                }
                UiConfigState.barStyle = style
                UiConfigState.save(context)
                settingsState.barStyle = style
            }

            override fun setMonet(
                on: Boolean,
                seed: Int,
                palette: Int,
                spec: Int,
            ) {
                // 与 MiuixX 一致：只按「当前 ThemeState.mode」这一维推目标模式，不再看镜像字段
                val m = ThemeState.mode
                val target = if (on) {
                    when (m) {
                        ThemeState.MODE_SYSTEM -> ThemeState.MODE_MONET_SYSTEM
                        ThemeState.MODE_DARK, ThemeState.MODE_MONET_DARK -> ThemeState.MODE_MONET_DARK
                        else -> ThemeState.MODE_MONET_LIGHT
                    }
                } else {
                    when (m) {
                        ThemeState.MODE_MONET_SYSTEM -> ThemeState.MODE_SYSTEM
                        ThemeState.MODE_MONET_DARK, ThemeState.MODE_DARK -> ThemeState.MODE_DARK
                        else -> ThemeState.MODE_LIGHT
                    }
                }
                ThemeState.setMonet(context, seed = seed, palette = palette, spec = spec)
                ThemeState.setMode(context, target)
                settingsState.monet = on
                settingsState.seedIndex = seed
                settingsState.paletteStyle = palette
                settingsState.colorSpec = spec
            }

            override fun setResign(on: Boolean) {
                AvtPrefs.setResign(context, on)
                settingsState.resign = on
                settingsState.snack(if (on) "已开启重签名" else "已切换为 MT 式不重签输出")
            }

            override fun setScheme(index: Int, on: Boolean) {
                val key = listOf("scheme_v1", "scheme_v2", "scheme_v3", "scheme_v4")[index]
                AvtPrefs.setScheme(context, key, on)
                when (index) {
                    0 -> settingsState.v1 = on
                    1 -> settingsState.v2 = on
                    2 -> settingsState.v3 = on
                    else -> settingsState.v4 = on
                }
            }

            override fun pickKey() { pickKeyLauncher.launch(arrayOf("*/*")) }

            override fun exportKey() {
                scope.launch {
                    runCatching {
                        val data = withContext(Dispatchers.IO) {
                            if (AvtPrefs.useCustomKey(context)) {
                                File(context.filesDir, "custom_key.p12").readBytes()
                            } else {
                                AvtIo.readAll(context.assets.open("signkey.p12"))
                            }
                        }
                        val out = exportKeyFile()
                        withContext(Dispatchers.IO) { out.writeBytes(data) }
                        out.absolutePath
                    }.onSuccess {
                        settingsState.snack("已导出: $it")
                    }.onFailure {
                        Toast.makeText(context, "导出失败: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun generateKey() {
                settingsState.snack("正在生成密钥…")
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val cn = "ApkVerTool-" + java.text.SimpleDateFormat(
                                "yyyyMMdd-HHmm", java.util.Locale.US,
                            ).format(java.util.Date())
                            val out = File(context.filesDir, "custom_key.p12")
                            SignUtil.generateKey(out, SignUtil.PASS, cn, "Seagull", "Seagull", 10950, 2048)
                        }
                        context.getSharedPreferences("settings", 0).edit()
                            .putBoolean("use_custom", true).apply()
                    }.onSuccess {
                        settingsState.keyStatus = keyStatusText(context)
                        settingsState.snack("密钥已生成")
                    }.onFailure {
                        Toast.makeText(context, "生成失败: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun clearKey() {
                File(context.filesDir, "custom_key.p12").delete()
                context.getSharedPreferences("settings", 0).edit()
                    .putBoolean("use_custom", false).apply()
                settingsState.keyStatus = keyStatusText(context)
                settingsState.snack("已恢复内置密钥")
            }

            override fun saveKeyPass(pass: String) {
                AvtPrefs.setKeyPass(context, pass)
                settingsState.snack("已保存密钥密码")
            }

            override fun pickOutputDir() {
                pickOutputDirLauncher.launch(null)
            }

            override fun resetOutputDir() {
                AvtPrefs.setOutputUri(context, null)
                settingsState.outputPath = outputPathText(context)
                settingsState.snack("已恢复默认输出位置")
            }

            override fun openChangelog() { backStack.add(Route.Changelog) }

            override fun openUrl(url: String) {
                // 走 LinkOpener：酷安 intent:// → App 内打开，未装回落浏览器；其余走浏览器
                if (!LinkOpener.open(context, url)) {
                    Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                }
            }

            override fun setUiStyleIndex(index: Int) {
                // 与 MiuixX 的「全局风格」一致：改值 → 联动折叠组 → 落盘 → 重组换套
                if (index == UiConfigState.uiStyle) return
                UiConfigState.uiStyle = index
                UiConfigState.foldGroups = index == 1
                UiConfigState.save(context)
            }

            override fun setEnableBlur(on: Boolean) {
                UiConfigState.enableBlur = on
                UiConfigState.save(context)
            }

            override fun setBlurRadius(radius: Float) {
                UiConfigState.blurRadius = radius
                UiConfigState.save(context)
            }
        }
    }

    val changelogHost = remember {
        object : ChangelogHost {
            override fun closeChangelog() { backStack.removeLastOrNull() }
        }
    }

    // 返回键：退栈
    BackHandler(enabled = backStack.size > 1) {
        backStack.removeLastOrNull()
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        // 二级页转场用 vendored miuix nav 库（NavTransitions.MiuixDefault），
        // 与 MiuixX 套共用同一套动画，零手写
        NavDisplay(
            backStack = backStack,
            transition = NavTransitions.MiuixDefault,
            effects = NavDisplayEffects(
                enableCornerClip = true,
                cornerClipRadius = 32.dp,
                cornerClipMode = NavCornerClipMode.All,
                dimAmount = 0.5f,
                blockInputDuringTransition = false,
                backdropColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            entry<Route.Main> {
                AvtTheme { MainScreen(state = mainState, host = mainHost) }
            }
            entry<Route.Settings> {
                AvtTheme { SettingsScreen(state = settingsState, host = settingsHost) }
            }
            entry<Route.Changelog> {
                AvtTheme { ChangelogScreen(host = changelogHost) }
            }
        }
    }
}

/** 体检类操作：后台跑、结果进日志面板（经 AvtState.log 桥接）。 */
private fun inspect(context: Context, block: (File) -> String) {
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

private fun keyStatusText(context: Context): String {
    val useCustom = AvtPrefs.useCustomKey(context)
    val f = File(context.filesDir, "custom_key.p12")
    return if (useCustom && f.exists()) {
        "● 当前使用：自定义密钥（${f.length() / 1024} KB）"
    } else "● 当前使用：内置密钥"
}

private fun outputPathText(context: Context): String {
    val uriStr = AvtPrefs.outputUri(context)
    if (uriStr.isEmpty()) return "● 当前：默认（应用私有目录 + 手动导出）"
    return try {
        val path = Uri.parse(uriStr).lastPathSegment
        "● 当前：" + (path?.replace("primary:", "内部存储/") ?: "自定义目录")
    } catch (_: Exception) {
        "● 当前：默认（自动选择）"
    }
}

private fun exportKeyFile(): File {
    val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    dl?.mkdirs()
    return File(dl, "ApkVerTool-key.p12")
}
