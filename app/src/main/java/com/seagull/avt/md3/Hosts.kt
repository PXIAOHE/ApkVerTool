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
    fun setFollowSystem(on: Boolean)
    fun setDarkMode(on: Boolean)
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
    /** 界面风格：true = 切回 MiuixX（recreate 热重启）。 */
    fun setUiStyle(useMiuix: Boolean)
}

/** 更新日志页回调。 */
interface ChangelogHost {
    fun closeChangelog()
}
