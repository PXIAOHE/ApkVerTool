// 应用级共享导航状态（两套 UI 共用）。
//
// 为什么放在 object 里而不是 remember：
//   1. 主题切换（换色）不能让页面弹回主页；
//   2. MiuixX ↔ MD3 切换会整棵子树换掉，remember 的状态会被重置。
// 放在应用级 object 中，生命周期与进程一致，两种切换都保持当前页面与当前分页。
// 进程重建（被系统杀掉后重进）回到初始状态，这是可接受的（未做持久化）。
package com.seagull.avt.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.navBackStackOf

object AvtNav {
    /** 唯一的导航栈：Route.Main 为根。 */
    val backStack: NavBackStack = navBackStackOf(Route.Main)

    /**
     * 主页四分页当前下标（改写 / 条目 / 体检 / 日志）。
     * 提为共享状态：切主题或切 UI 风格时停在同一分页。
     */
    var mainPage by mutableIntStateOf(0)

    /** 设置页四分页当前下标（外观 / 签名 / 输出 / 关于）。 */
    var settingsPage by mutableIntStateOf(0)

    fun push(route: Route) {
        backStack.add(route)
    }

    fun pop() {
        backStack.removeLastOrNull()
    }

    /** 当前栈顶目的地（两套 UI 互切时保持同一页）。 */
    val current: Route
        get() = backStack.lastOrNull() as? Route ?: Route.Main
}
