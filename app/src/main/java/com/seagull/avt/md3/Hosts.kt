// MD3 界面的 Host 回调接口（从 MD3 版 AvtUi.kt 抽出）。
// 屏幕只显示状态 + 把操作转成回调；实现层在 app 包的 Md3App.kt（单 Activity，recreate 热切换）。
package com.seagull.avt.md3

/** 主页回调。 */
interface MainHost {
    fun openSettings()
    fun pickFiles()
    fun addReplaceEntry(name: String)
    fun clearReplaceEntries()
    fun clearLogs()
    fun startRun()
    fun install()
    /** 用户在「未签名产物需要核心破解」对话框里点了「尝试 root 安装」。 */
    fun confirmRootInstall()
    fun share()
    fun showInfo()
    fun showAlign()
    fun showVerify()
    fun showEntries()
}

/** 设置页回调。 */
interface SettingsHost {
    fun closeSettings()
    /** 主题模式（0 跟随 / 1 浅 / 2 深 / 3 莫奈·跟随 / 4 莫奈·浅 / 5 莫奈·深），与 MiuixX 同一套。 */
    fun setThemeMode(mode: Int)
    /** 莫奈取色开关 + 参数（种子色 / 配色风格 / 色彩规范）。 */
    fun setMonet(on: Boolean, seed: Int, palette: Int, spec: Int)
    /** 切换底栏样式（三选一）。 */
    fun setBarStyle(style: com.seagull.avt.md3.bars.BottomBarStyle)
    fun setResign(on: Boolean)
    /** index：0=V1、1=V2、2=V3、3=V4。 */
    fun setScheme(index: Int, on: Boolean)
    fun pickKey()
    fun exportKey()
    fun generateKey()
    fun clearKey()
    fun saveKeyPass(pass: String)
    fun pickOutputDir()
    fun resetOutputDir()
    fun openChangelog()
    fun openUrl(url: String)
    /** 界面风格下标：0 = MiuixX（毛玻璃）、1 = MD3（Material），与 MiuixX 的「全局风格」同序。 */
    fun setUiStyleIndex(index: Int)
    /** 栏体模糊开关（顶栏与底栏的毛玻璃效果）。 */
    fun setEnableBlur(on: Boolean)
    /** 模糊半径（0..30，与 MiuixX 同一个区间）。 */
    fun setBlurRadius(radius: Float)
}

/** 更新日志页回调。 */
interface ChangelogHost {
    fun closeChangelog()
}
