package com.seagull.avt.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 内置更新检查。
 *
 * 清单优先走 jsDelivr（国内快），失败再退 GitHub Releases API（零维护）。
 * 比较 versionCode，不是版本名字符串。网络请求请放在 IO 线程调 [check]。
 */
object UpdateChecker {

    private const val MANIFEST_URL =
        "https://cdn.jsdelivr.net/gh/PXIAOHE/ApkVerTool@main/update/update.json"
    private const val API_URL =
        "https://api.github.com/repos/PXIAOHE/ApkVerTool/releases/latest"
    private const val PAGE = "https://github.com/PXIAOHE/ApkVerTool/releases/latest"
    private const val PREF = "update_check"
    private const val INTERVAL_MS = 12 * 3600 * 1000L

    data class Info(
        val code: Int,
        val name: String,
        val notes: String,
        val apkUrl: String?,
        val pageUrl: String,
        val sha256: String?,
        val minSupportedCode: Int,
    ) {
        /** 语义版本比较（x.y.z）：两个来源都拿得到版本名，比流水号 versionCode 可靠。 */
        val isNewer: Boolean get() = semverGreater(name, AvtVersion.NAME)

        /** 只有清单（version.py 那份）会给真实的 versionCode，API 兜底时为 0 = 不强制。 */
        val isMandatory: Boolean get() = minSupportedCode > 0 && minSupportedCode > AvtVersion.CODE
    }

    /** a 是否大于 b（都按 x.y.z 数字段比；非法格式一律当不更大）。 */
    private fun semverGreater(a: String, b: String): Boolean {
        val x = a.split('.').mapNotNull { it.toIntOrNull() }
        val y = b.split('.').mapNotNull { it.toIntOrNull() }
        if (x.isEmpty() || y.isEmpty()) return false
        for (i in 0 until maxOf(x.size, y.size)) {
            val u = x.getOrElse(i) { 0 }
            val v = y.getOrElse(i) { 0 }
            if (u != v) return u > v
        }
        return false
    }

    var latest by mutableStateOf<Info?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    fun shouldAutoCheck(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(PREF, 0)
        return System.currentTimeMillis() - sp.getLong("last_check", 0) > INTERVAL_MS
    }

    /** 「稍后」：只清掉界面上的提示，不改忽略记录。 */
    fun dismiss() {
        latest = null
    }

    fun ignore(ctx: Context, name: String) {
        ctx.getSharedPreferences(PREF, 0).edit().putString("ignored_name", name).apply()
        latest = null
    }

    fun markChecked(ctx: Context) {
        ctx.getSharedPreferences(PREF, 0).edit()
            .putLong("last_check", System.currentTimeMillis()).apply()
    }

    /** 阻塞式；调用方放 IO 线程。返回非 null 表示确实有更新（且没被忽略）。 */
    fun check(ctx: Context): Info? {
        checking = true
        lastError = null
        android.util.Log.i("AvtUpdate", "开始检查（本地 " + AvtVersion.NAME + "）")
        try {
            val info = fetchManifest() ?: fetchFromApi()
            markChecked(ctx)
            val ignored = ctx.getSharedPreferences(PREF, 0).getString("ignored_name", "")
            latest = info?.takeIf { it.isNewer && it.name != ignored }
            android.util.Log.i("AvtUpdate", "远端=" + (info?.name ?: "-") + " code=" + (info?.code ?: 0) +
                " 本地=" + AvtVersion.NAME + " 新版=" + (latest != null))
            return latest
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            android.util.Log.w("AvtUpdate", "检查失败: " + lastError)
            return null
        } finally {
            checking = false
        }
    }

    private fun fetchManifest(): Info? {
        val text = httpGet(MANIFEST_URL) ?: return null
        val o = JSONObject(text)
        return Info(
            code = o.optInt("version_code", 0),
            name = o.optString("version_name", ""),
            notes = o.optString("notes", ""),
            apkUrl = o.optString("apk_url").takeIf { it.isNotEmpty() },
            pageUrl = o.optString("page_url", PAGE),
            sha256 = o.optString("sha256").takeIf { it.isNotEmpty() },
            minSupportedCode = o.optInt("min_supported_code", 0),
        ).takeIf { it.code > 0 }
    }

    /**
     * 兜底：GitHub Releases API。它只给 tag（v2.2.1）不给 versionCode，
     * 所以按 major*10000 + minor*100 + patch 折算 —— 与本工程的 code 递增规则一致。
     */
    private fun fetchFromApi(): Info? {
        val text = httpGet(API_URL) ?: return null
        val o = JSONObject(text)
        val tag = o.optString("tag_name", "").trimStart('v')
        if (tag.split('.').size != 3) return null
        var apk: String? = null
        val assets = o.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name", "").endsWith(".apk")) {
                    apk = a.optString("browser_download_url").takeIf { it.isNotEmpty() }
                    break
                }
            }
        }
        return Info(
            code = 0,          // API 不给 versionCode，比较走版本名
            name = tag,
            notes = o.optString("body", ""),
            apkUrl = apk,
            pageUrl = o.optString("html_url", PAGE),
            sha256 = null,
            minSupportedCode = 0,
        )
    }

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ApkVerTool/" + AvtVersion.NAME)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
