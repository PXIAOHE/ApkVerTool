// 全局 UI 配置（对齐 ApkEditorMiuix 的 UiConfigState）：模糊开关 / 悬浮底栏 / 模糊半径 / 悬浮风格。
package com.seagull.avt.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object UiConfigState {
    var enableBlur by mutableStateOf(true)
    var useFloatingNavigationBar by mutableStateOf(false)
    var blurRadius by mutableStateOf(15f)
    var floatingNavStyle by mutableStateOf(1) // 0=默认, 1=iOS 风格
    // 全局 UI 风格：0 = MiuixX（毛玻璃/大圆角/平铺），1 = MD3（实色栏/小圆角/折叠分组）
    var uiStyle by mutableStateOf(0)
    /** 底栏样式（与 BottomBarStyle 同一份语义）：两套 UI 共用，改动即时生效。 */
    var barStyle by mutableStateOf(com.seagull.avt.md3.bars.BottomBarStyle.default())
    // 改写页布局：MD3 风格下折叠分组；Miuix 风格下平铺
    var foldGroups by mutableStateOf(true)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("ui_config", Context.MODE_PRIVATE)
        enableBlur = prefs.getBoolean("enable_blur", true)
        useFloatingNavigationBar = prefs.getBoolean("use_floating_navbar", false)
        blurRadius = prefs.getFloat("blur_radius", 15f)
        floatingNavStyle = prefs.getInt("floating_nav_style", 1)
        uiStyle = prefs.getInt("ui_style", 0)
        foldGroups = prefs.getBoolean("fold_groups", true)
        barStyle = com.seagull.avt.md3.bars.BottomBarStyle.read(context)
    }

    fun save(context: Context) {
        context.getSharedPreferences("ui_config", Context.MODE_PRIVATE).edit().apply {
            putBoolean("enable_blur", enableBlur)
            putBoolean("use_floating_navbar", useFloatingNavigationBar)
            putFloat("blur_radius", blurRadius)
            putInt("floating_nav_style", floatingNavStyle)
            putInt("ui_style", uiStyle)
            putBoolean("fold_groups", foldGroups)
        }.apply()
    }
}
