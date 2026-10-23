// ApkVerTool — Compose 版全局状态仓。
// 所有跨页共享的状态（主题模式、文件队列、任务日志、设置项）都放这里。
// UI 全部用 miuix（与 ApkEditorMiuix 同一套视觉语言），引擎仍是 core/ 里的纯 Java 实现。
package com.seagull.avt.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.seagull.avt.core.ApkPatcher
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * 主题模式：0 跟随系统 / 1 浅色 / 2 深色 / 3 莫奈跟随系统 / 4 莫奈浅色 / 5 莫奈深色。
 * 莫奈（Monet）= miuix 的 Material You 动态取色：从种子色推导整套配色，
 * 可选配色风格（TonalSpot/Neutral/Vibrant…）与色彩规范（2021/2025）。
 */
object ThemeState {
    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2
    const val MODE_MONET_SYSTEM = 3
    const val MODE_MONET_LIGHT = 4
    const val MODE_MONET_DARK = 5

    var mode by mutableStateOf(MODE_SYSTEM)
        private set
    /** 种子色下标：0 = 默认（不带种子色），1..N = KeyColors[i-1]。 */
    var seedIndex by mutableStateOf(0)
        private set
    /** ThemePaletteStyle.entries 下标。 */
    var paletteStyle by mutableStateOf(0)
        private set
    /** ThemeColorSpec.entries 下标（Spec2021 / Spec2025）。 */
    var colorSpec by mutableStateOf(0)
        private set

    val isMonet: Boolean get() = mode >= MODE_MONET_SYSTEM

    fun load(ctx: Context) {
        val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        mode = when (sp.getString("theme_mode", "auto")) {
            "light" -> MODE_LIGHT
            "dark" -> MODE_DARK
            "monet_auto" -> MODE_MONET_SYSTEM
            "monet_light" -> MODE_MONET_LIGHT
            "monet_dark" -> MODE_MONET_DARK
            else -> MODE_SYSTEM
        }
        seedIndex = sp.getInt("theme_seed", 0)
        paletteStyle = sp.getInt("theme_palette", 0)
        colorSpec = sp.getInt("theme_spec", 0)
    }

    fun setMode(ctx: Context, m: Int) {
        mode = m
        persist(ctx)
    }

    /** 莫奈取色参数变化（种子色 / 配色风格 / 色彩规范）。 */
    fun setMonet(ctx: Context, seed: Int = seedIndex, palette: Int = paletteStyle, spec: Int = colorSpec) {
        seedIndex = seed
        paletteStyle = palette
        colorSpec = spec
        persist(ctx)
    }

    private fun persist(ctx: Context) {
        // 持久化用旧 Java 版的 key（theme_mode）：老用户升级配置不丢
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString(
                "theme_mode",
                when (mode) {
                    MODE_LIGHT -> "light"
                    MODE_DARK -> "dark"
                    MODE_MONET_SYSTEM -> "monet_auto"
                    MODE_MONET_LIGHT -> "monet_light"
                    MODE_MONET_DARK -> "monet_dark"
                    else -> "auto"
                },
            )
            .putInt("theme_seed", seedIndex)
            .putInt("theme_palette", paletteStyle)
            .putInt("theme_spec", colorSpec)
            .apply()
    }
}

/** 莫奈种子色表（对齐 miuix example 的 KeyColors）。index 0 = 默认无种子色。 */
val KeyColors: List<Pair<String, androidx.compose.ui.graphics.Color>> = listOf(
    "蓝" to androidx.compose.ui.graphics.Color(0xFF3482FF),
    "绿" to androidx.compose.ui.graphics.Color(0xFF36D167),
    "紫" to androidx.compose.ui.graphics.Color(0xFF7C4DFF),
    "黄" to androidx.compose.ui.graphics.Color(0xFFFFB21D),
    "橙" to androidx.compose.ui.graphics.Color(0xFFFF5722),
    "粉" to androidx.compose.ui.graphics.Color(0xFFE91E63),
    "青" to androidx.compose.ui.graphics.Color(0xFF00BCD4),
)

/** seedIndex 0 表示默认（返回 null，交给系统调色板）。 */
fun keyColorFor(index: Int): androidx.compose.ui.graphics.Color? =
    if (index <= 0) null else KeyColors.getOrNull(index - 1)?.second

/** 应用级 UI 状态：队列、日志、任务、APK */
object AvtState {
    // ── 文件队列 ──
    val queue = mutableStateListOf<File>()
    val queueNames = mutableStateListOf<String>()

    // ── 待替换条目 ──
    val putMap = LinkedHashMap<String, ByteArray>()
    var putMapVersion by mutableStateOf(0)
        private set

    fun addPut(name: String, bytes: ByteArray) {
        putMap[name] = bytes
        putMapVersion++
    }

    fun clearPut() {
        putMap.clear()
        putMapVersion++
    }

    // ── 清单信息（选中第一个文件后回填） ──
    var manifestInfo by mutableStateOf<String?>(null)

    // ── 任务 ──
    var busy by mutableStateOf(false)
    val log = mutableStateListOf<String>()
    var lastOutput by mutableStateOf<File?>(null)
    var lastSafUri by mutableStateOf<Uri?>(null)
    var lastOutputSigned by mutableStateOf(true)

    fun appendLog(s: String) {
        log.add(s)
        // 日志无限堆会把列表撑爆，保留最近 800 行
        while (log.size > 800) log.removeAt(0)
    }

    fun clearLog() = log.clear()

    fun reset() {
        queue.clear()
        queueNames.clear()
        manifestInfo = null
        lastOutput = null
        lastSafUri = null
        lastOutputSigned = true
    }
}

/** 设置项读写（与旧 Java 版 key 完全兼容，老用户升级不丢配置） */
object AvtPrefs {
    fun sp(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // 重签名
    fun resign(ctx: Context) = sp(ctx).getBoolean("resign", true)
    fun setResign(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("resign", v).apply()

    // 签名方案
    fun scheme(ctx: Context, key: String, def: Boolean) = sp(ctx).getBoolean(key, def)
    fun setScheme(ctx: Context, key: String, v: Boolean) = sp(ctx).edit().putBoolean(key, v).apply()

    // 密钥
    fun useCustomKey(ctx: Context) = sp(ctx).getBoolean("use_custom", false)
    fun keyPass(ctx: Context) = sp(ctx).getString("key_pass", "") ?: ""
    fun setKeyPass(ctx: Context, v: String) = sp(ctx).edit().putString("key_pass", v).apply()

    // 输出目录（SAF tree uri）
    fun outputUri(ctx: Context) = sp(ctx).getString("output_uri", "") ?: ""
    fun setOutputUri(ctx: Context, v: String?) {
        val e = sp(ctx).edit()
        if (v.isNullOrEmpty()) e.remove("output_uri") else e.putString("output_uri", v)
        e.apply()
    }
}

/** 共享 IO 工具 */
object AvtIo {
    fun copy(input: InputStream, out: OutputStream) {
        try {
            val buf = ByteArray(1 shl 16)
            var n: Int
            while (input.read(buf).also { n = it } > 0) out.write(buf, 0, n)
            out.flush()
        } finally {
            try { input.close() } catch (_: Throwable) {}
            try { out.close() } catch (_: Throwable) {}
        }
    }

    fun readAll(input: InputStream): ByteArray {
        val bos = java.io.ByteArrayOutputStream(1 shl 16)
        try {
            val buf = ByteArray(1 shl 15)
            var n: Int
            while (input.read(buf).also { n = it } > 0) bos.write(buf, 0, n)
        } finally {
            try { input.close() } catch (_: Throwable) {}
        }
        return bos.toByteArray()
    }

    fun sanitize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_') sb.append(c)
            else sb.append('_')
        }
        return sb.toString()
    }

    fun displayName(ctx: Context, u: Uri): String {
        try {
            ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) {
                    val s = c.getString(idx)
                    if (!s.isNullOrEmpty()) return s
                }
            }
        } catch (_: Throwable) {}
        return u.lastPathSegment ?: "unknown.apk"
    }

    fun suffix(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".xapk") -> ".xapk"
            lower.endsWith(".apks") -> ".apks"
            else -> ".apk"
        }
    }

    /** 应用私有 out 目录（中转） */
    fun outDir(ctx: Context): File {
        val d = ctx.getExternalFilesDir("out") ?: File(ctx.filesDir, "out")
        d.mkdirs()
        return d
    }

    /** APK导出到 SAF 目录；未设置目录返回 null */
    fun exportToSaf(ctx: Context, apk: File, base: String): Uri? {
        val oUri = AvtPrefs.outputUri(ctx)
        if (oUri.isEmpty()) return null
        return try {
            val tree = Uri.parse(oUri)
            val tdid = DocumentsContract.getTreeDocumentId(tree)
            val dir = DocumentsContract.buildDocumentUriUsingTree(tree, tdid)
            val cr = ctx.contentResolver
            // 同名文件先删掉：SAF 的 insert 不覆盖同名文档
            findChildByName(cr, tree, tdid, base)?.let { old ->
                try { DocumentsContract.deleteDocument(cr, old) } catch (_: Throwable) {}
            }
            var target: Uri? = null
            try {
                target = DocumentsContract.createDocument(cr, dir, "application/octet-stream", base)
            } catch (_: Throwable) {}
            if (target == null) {
                val cv = android.content.ContentValues()
                cv.put(DocumentsContract.Document.COLUMN_DISPLAY_NAME, base)
                cv.put(DocumentsContract.Document.COLUMN_MIME_TYPE, "application/octet-stream")
                target = try { cr.insert(dir, cv) } catch (_: Throwable) { null }
            }
            if (target == null) return null
            copy(apk.inputStream(), cr.openOutputStream(target) ?: return null)
            target
        } catch (_: Throwable) {
            null
        }
    }

    fun findChildByName(cr: android.content.ContentResolver, tree: Uri, treeDocId: String, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocId)
        return try {
            cr.query(children, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    if (name == c.getString(1)) {
                        return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                    }
                }
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** pm install 输出压几行：丢堆栈行，末尾标注省略数 */
    fun condense(s: String?): String {
        if (s == null) return ""
        val lines = s.split("\n")
        val sb = StringBuilder()
        var skipped = 0
        for (raw in lines) {
            val ln = raw.trim()
            if (ln.isEmpty()) continue
            if (ln.startsWith("at ") || ln.startsWith("... ") || ln.startsWith("Caused by:")) {
                if (ln.startsWith("Caused by:")) sb.append(ln).append('\n')
                skipped++
                continue
            }
            sb.append(ln).append('\n')
        }
        if (skipped > 0) sb.append("…（省略 ").append(skipped).append(" 行堆栈）\n")
        return sb.toString().trim()
    }

    fun human(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }
}

/** 任务引擎：把 UI 的表单值转成 ApkPatcher.Job 并在后台线程执行 */
object AvtRunner {
    /** 当前任务进度文本（写日志页的同时给改写页顶部摘要） */
    var runningSummary by mutableStateOf<String?>(null)

    fun buildJob(
        versionName: String, versionCode: String, versionCodeMajor: String,
        minSdk: String, targetSdk: String, label: String, newPackage: String,
        dropEntries: List<String>,
        resign: Boolean, v1: Boolean, v2: Boolean, v3: Boolean, v4: Boolean,
        p12: ByteArray?, pass: CharArray?,
        iconImage: ByteArray? = null,
    ): ApkPatcher.Job {
        val job = ApkPatcher.Job()
        if (versionName.isNotBlank()) job.manifest.versionName = versionName.trim()
        if (versionCode.isNotBlank()) job.manifest.versionCode = versionCode.trim().toLong()
        if (versionCodeMajor.isNotBlank()) job.manifest.versionCodeMajor = versionCodeMajor.trim().toLong()
        if (minSdk.isNotBlank()) job.manifest.minSdk = minSdk.trim().toInt()
        if (targetSdk.isNotBlank()) job.manifest.targetSdk = targetSdk.trim().toInt()
        if (label.isNotBlank()) job.manifest.label = label.trim()
        if (newPackage.isNotBlank()) job.newPackage = newPackage.trim()
        dropEntries.forEach { if (it.isNotBlank()) job.edit.delete.add(it.trim()) }
        if (iconImage != null && iconImage.isNotEmpty()) {
            job.iconImage = iconImage
            job.iconScaler = AvtIcon.scaler
        }
        for ((k, v) in AvtState.putMap) job.edit.replace[k] = v
        job.resign = resign
        job.opt.v1 = v1
        job.opt.v2 = v2
        job.opt.v3 = v3
        job.opt.v4 = v4
        if (resign && p12 != null) {
            job.p12 = p12
            if (pass != null) job.pass = pass
        }
        return job
    }

    fun hasAnyChange(job: ApkPatcher.Job): Boolean =
        !job.manifest.isEmpty() || job.newPackage != null ||
                job.edit.delete.isNotEmpty() || job.edit.replace.isNotEmpty() ||
                job.iconImage != null

    /**
     * 批量处理整个队列；返回 (成功数, 失败数)。阻塞调用，须在后台线程。
     * @param sink 日志出口；null 写 AvtState.log（Miuix 界面），MD3 界面传自己的 logs。
     */
    fun runQueue(ctx: Context, job: ApkPatcher.Job, sink: ((String) -> Unit)? = null): Pair<Int, Int> {
        var ok = 0
        var failed = 0
        val queue = AvtState.queue.toList()
        val names = AvtState.queueNames.toList()
        val say: (String) -> Unit = sink ?: { AvtState.appendLog(it) }
        say("签名: " + if (job.resign) {
            "开 ${job.opt.describe()}  密钥: ${if (job.p12 != null) "自定义密钥" else "内置密钥"}"
        } else "关（MT 式不重签）")
        for ((idx, src) in queue.withIndex()) {
            val srcName = names.getOrNull(idx) ?: src.name
            say("──────── [${idx + 1}/${queue.size}] $srcName ────────")
            try {
                if (processOne(ctx, src, srcName, job, say)) ok++ else failed++
            } catch (t: Throwable) {
                failed++
                say("失败: $t")
            }
        }
        say("════ 完成: 成功 $ok 个，失败 $failed 个 ════")
        return ok to failed
    }

    private fun processOne(
        ctx: Context,
        src: File,
        srcName: String,
        job: ApkPatcher.Job,
        say: (String) -> Unit = { AvtState.appendLog(it) },
    ): Boolean {
        var stem = srcName
        val dot = stem.lastIndexOf('.')
        if (dot > 0) stem = stem.substring(0, dot)
        val ext = AvtIo.suffix(srcName)
        val base = "patched_" + AvtIo.sanitize(stem) +
                (job.manifest.versionName?.let { "_" + AvtIo.sanitize(it) } ?: "") +
                (job.manifest.versionCode?.let { "_$it" } ?: "") + ext
        val out = File(AvtIo.outDir(ctx), base)

        if (com.seagull.avt.core.SplitBundle.isBundle(src)) {
            say("多包容器模式")
            val rep = com.seagull.avt.core.SplitBundle.patch(src, out, job)
            say(rep.describe())
            if (rep.failed > 0) return false
        } else {
            val oc = ApkPatcher.patch(src, out, job)
            say(oc.logText())
            if (job.resign && oc.verify != null && !oc.verify.verified) return false
        }
        say("输出: ${out.absolutePath}  (${out.length()} 字节)")
        val saf = AvtIo.exportToSaf(ctx, out, base)
        AvtState.lastOutput = out
        AvtState.lastSafUri = saf
        AvtState.lastOutputSigned = job.resign
        if (saf != null && out.delete()) {
            AvtState.lastOutput = null
            say("中转副本已清理（APK只留在指定目录里）")
        }
        return true
    }
}

/**
 * 图标缩放：和 CLI 的 ImageIO 版同一套行为——居中取最大内接正方形，缩放到目标尺寸，输出 PNG。
 * 解码/编码交给 Android 的 BitmapFactory，core 只收字节，保持纯 JDK。
 */
object AvtIcon {
    val scaler: com.seagull.avt.core.IconReplacer.Scaler =
        com.seagull.avt.core.IconReplacer.Scaler { image, w, h -> scale(image, w, h) }

    fun scale(image: ByteArray, w: Int, h: Int): ByteArray {
        val src = BitmapFactory.decodeByteArray(image, 0, image.size)
            ?: throw IllegalStateException("认不出这个图片格式")
        val side = minOf(src.width, src.height)
        if (side <= 0) throw IllegalStateException("图片尺寸为 0")
        val cropped =
            if (src.width == side && src.height == side) src
            else Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val dst = Bitmap.createScaledBitmap(cropped, w, h, true)
        val out = java.io.ByteArrayOutputStream(1 shl 15)
        dst.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    /** 选图时先试解码一次：坏图当场拒绝，别等点「开始修改」才失败。 */
    fun readable(image: ByteArray): Boolean {
        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(image, 0, image.size, opts)
        return opts.outWidth > 0 && opts.outHeight > 0
    }
}
