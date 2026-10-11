package com.seagull.avt.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.seagull.avt.ui.bars.BottomBarStyle
import com.seagull.avt.ui.theme.AvtTheme

/*
 * Java ↔ Compose 的唯一桥。
 *
 * Activity 仍然是“逻辑宿主”：它实现下面这些回调接口、持有全部业务字段、自己开线程跑引擎；
 * Compose 只负责显示状态与把用户操作转成回调调用。
 * 三个 attach* 是 Java 唯一需要的入口（@JvmStatic，Java 直接 AvtUi.attachMain(...)）。
 */

/** 主页回调。方法体在 MainActivity 里，与重构前完全一致。 */
interface MainHost {
    fun openSettings()
    fun pickFiles()
    fun addReplaceEntry(name: String)
    fun clearReplaceEntries()
    fun clearLogs()
    fun startRun()
    fun install()
    /** 用户在「未签名APK需要核心破解」对话框里点了「尝试 root 安装」。 */
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
    /** 切换底栏样式（三选一）。 */
    fun setBarStyle(style: BottomBarStyle)
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
}

/** 更新日志页回调。 */
interface ChangelogHost {
    fun closeChangelog()
}

object AvtUi {

    @JvmStatic
    fun attachMain(activity: ComponentActivity, state: MainUiState, host: MainHost) {
        activity.setContent {
            AvtTheme {
                MainScreen(state = state, host = host)
            }
        }
    }

    @JvmStatic
    fun attachSettings(activity: ComponentActivity, state: SettingsUiState, host: SettingsHost) {
        activity.setContent {
            AvtTheme {
                SettingsScreen(state = state, host = host)
            }
        }
    }

    @JvmStatic
    fun attachChangelog(activity: ComponentActivity, host: ChangelogHost) {
        activity.setContent {
            AvtTheme {
                ChangelogScreen(host = host)
            }
        }
    }
}
