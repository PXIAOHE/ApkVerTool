// 共用的外链跳转助手。
// 酷安链接走 coolmarket:// App 内打开（intent:// 格式，未装酷安则回落浏览器）；
// 其余链接（GitHub 等）走普通浏览器。
package com.seagull.avt.app

import android.content.Context
import android.content.Intent
import android.net.Uri

object LinkOpener {

    /**
     * 打开一个链接。
     * - `intent://…#Intent;scheme=coolmarket;package=com.coolapk.market;end` → 尝试唤起酷安 App，
     *   未安装时回落到 scheme 对应的浏览器打开。
     * - 其它 http(s) 链接 → 浏览器。
     * 失败返回 false（调用方给个 toast）。
     */
    fun open(context: Context, url: String): Boolean = try {
        if (url.startsWith("intent://")) {
            // 解析 intent:// 语法；未装目标包时用 scheme 兜底
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: android.content.ActivityNotFoundException) {
                // 目标 App（酷安）未安装 → 回落浏览器打开 coolmarket scheme 的 http 形式
                val fallback = url
                    .removePrefix("intent://")
                    .substringBefore("#Intent")
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://$fallback"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        } else {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        true
    } catch (_: Throwable) {
        false
    }
}
