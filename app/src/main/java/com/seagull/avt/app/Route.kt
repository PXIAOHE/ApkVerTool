// 顶层导航目的地（两套 UI 共用）。
// 转场动画来自 vendored 的 miuix 官方 nav 库（top.yukonga.miuix.kmp.nav），
// NavTransitions.MiuixDefault：二级页从右侧全宽滑入、被覆盖页 1/4 视差 + 透明度衰减，
// 手势（预测式返回 / 边缘滑动）与程序动画共用同一条驱动，零手写动画。
// 不用 @Serializable：backStack 用 navBackStackOf 手动栈，不走 rememberSaveable 持久化。
package com.seagull.avt.app

import top.yukonga.miuix.kmp.nav.core.NavKey

sealed interface Route : NavKey {
    data object Main : Route
    data object Settings : Route
    data object Changelog : Route
}
