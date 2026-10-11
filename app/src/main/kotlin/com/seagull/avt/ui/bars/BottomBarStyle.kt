package com.seagull.avt.ui.bars

import android.content.Context
import android.content.SharedPreferences

/**
 * 底栏样式。
 *
 * 三种形态与 `dist/` 里的三个APK一一对应 —— 它们原本是三次迭代的APK，
 * 现在合成一套代码，由设置页「外观」里切换：
 *
 * | 本枚举 | 对应APK | 实现 |
 * | :--- | :--- | :--- |
 * | [GLASS_DYNAMIC] | `ApkVerTool-v2.0.3-drag.apk` | `ui/glass/GlassBottomBar.kt`（按压缩放 + 拖拽切页） |
 * | [GLASS_STATIC] | `ApkVerTool-v2.0.3-glass.apk` | `ui/glass/GlassStaticBottomBar.kt`（静态浅蓝胶囊 + 跟手折射） |
 * | [M3] | `ApkVerTool-v2.0.3-m3.apk` | `ui/bars/M3BottomBar.kt`（M3 形状 + 同款模糊） |
 *
 * 落盘键是 `bar_style`（SharedPreferences 名 `settings`）。
 * 这个键在本工程里一直存在，只是 UI 重构时把读取逻辑删了、键没删 —— 这里接回来，
 * 所以老用户升级不会因为缺键而崩，只是回到默认值。
 */
enum class BottomBarStyle(
    /** 落盘用的稳定 id，**不要改**（改了等于丢用户设置）。 */
    val id: String,
    /** 设置页显示名。 */
    val label: String,
) {
    /**
     * 悬浮玻璃胶囊：按住整条会横向鼓出来，选中的胶囊会放大并跟手；
     * 可以直接按住胶囊拖到别的分页，松手吸附。观感最重。
     */
    GLASS_DYNAMIC(
        // ⚠️ id 保持 "glass_drag" 不动 —— 这是落盘键，改了等于把老用户的设置丢掉。
        // 只有显示名从「拖拽」改成了「动态」。
        id = "glass_drag",
        label = "液态玻璃 · 动态",
    ),

    /**
     * 悬浮玻璃胶囊：选中项常显一块浅蓝胶囊并带边缘折射，横滑页面时胶囊跟手；
     * 只能横滑换页，不能拖底栏。观感适中。
     */
    GLASS_STATIC(
        id = "glass_static",
        label = "液态玻璃 · 静态",
    ),

    /**
     * Material 3 底栏 + 液态玻璃同款模糊：通栏贴底，形状与配色是 M3 的，
     * 但底栏本身是浮层、会采背景做雾化（内容从它底下滚过时会被模糊）。
     */
    M3(
        id = "m3",
        label = "Material 3",
    ),
    ;

    companion object {

        /** SharedPreferences 键名。 */
        const val PREF_KEY = "bar_style"

        /** 默认样式 = 原「拖拽版」的行为，保证升级后观感不变。 */
        @JvmStatic
        fun default(): BottomBarStyle = GLASS_DYNAMIC

        /** 按 id 反查；不认识的值一律回落到默认（不抛异常）。 */
        @JvmStatic
        fun fromId(id: String?): BottomBarStyle =
            entries.firstOrNull { it.id == id } ?: default()

        @JvmStatic
        fun read(prefs: SharedPreferences): BottomBarStyle =
            fromId(prefs.getString(PREF_KEY, default().id))

        /** Java 侧（Activity）用这个。 */
        @JvmStatic
        fun read(context: Context): BottomBarStyle =
            read(context.getSharedPreferences("settings", Context.MODE_PRIVATE))

        @JvmStatic
        fun write(prefs: SharedPreferences, style: BottomBarStyle) {
            prefs.edit().putString(PREF_KEY, style.id).apply()
        }
    }
}
