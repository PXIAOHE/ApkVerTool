package com.seagull.avt.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.seagull.avt.ui.bars.BottomBarStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/*
 * UI 状态持有者。
 *
 * 设计原则：**业务逻辑一行不动**。Activity 仍然持有 queue / putMap / lastOutput 这些业务字段，
 * 仍然自己开 Thread 跑重活；这里只镜像“界面需要显示的那部分”，并把用户操作转成宿主回调。
 *
 * Java 侧通过生成的 getX()/setX() 读写（Kotlin 的 `by mutableStateOf` 会生成普通存取器），
 * 集合类用 SnapshotStateList / SnapshotStateMap，Java 直接当 MutableList / MutableMap 用。
 */

/** 「替换 / 新增条目」列表项：条目名 + 字节数。 */
data class ReplaceEntry(val name: String, val size: Int)

const val HINT_INSTALL_IDLE =
    "安装：已签名APK走系统安装器；未签名APK需要 root + 核心破解。"
const val HINT_INSTALL_SIGNED = "安装：APK已签名，点「安装」交给系统安装器。"
const val HINT_INSTALL_UNSIGNED =
    "安装：APK未签名，点「安装」将走 root 静默安装，需要核心破解禁用签名校验。"

/**
 * 日志「小节标题」的哨兵前缀。
 *
 * 旧版用 `──── 体检报告 ────` / `════ 完成 ════` 这种画线分隔，属于界面装饰；
 * 按 Q8 决策改为「M3 标题样式」：业务侧写 [MainUiState.section]，日志面板按标题排版渲染。
 * 前缀用 NUL 字符，正常日志里不可能出现，也不会被 Text 显示出来。
 */
const val LOG_SECTION = "\u0000"

class MainUiState {

    // ── 第 1 页：改写 ──
    var versionName by mutableStateOf("")
    var versionCode by mutableStateOf("")
    var versionCodeMajor by mutableStateOf("")
    var minSdk by mutableStateOf("")
    var targetSdk by mutableStateOf("")
    var appLabel by mutableStateOf("")
    var packageName by mutableStateOf("")

    /** 折叠分组状态：key -> 是否展开。收起的分组整组字段都不参与改写。 */
    val foldOpen = mutableStateMapOf("ver" to true, "sdk" to false, "id" to false)

    // ── 第 2 页：条目 ──
    var dropText by mutableStateOf("")
    val replaceEntries = mutableStateListOf<ReplaceEntry>()

    // ── 状态文本 ──
    var sourceText by mutableStateOf("尚未选择文件")
    var infoText by mutableStateOf("尚未选择文件")
    var installHint by mutableStateOf(HINT_INSTALL_IDLE)
    var canInstall by mutableStateOf(false)
    var canShare by mutableStateOf(false)
    var busy by mutableStateOf(false)

    /** 执行日志：一行一条，日志页用 LazyColumn 渲染并自动滚到底。 */
    val logs = mutableStateListOf<String>()

    // ── 底栏角标 ──
    var fileCount by mutableStateOf(0)
    var entryCount by mutableStateOf(0)

    /**
     * 请求跳到某一页：业务侧（Java）只负责写目标页号，Compose 侧消费一次后清空。
     * 对应旧代码里「开始修改 → selectPage(日志页)」那一步。
     */
    var pageRequest by mutableStateOf<Int?>(null)

    // ── 对话框 ──
    var alertTitle by mutableStateOf<String?>(null)
    var alertMessage by mutableStateOf("")
    var askEntryName by mutableStateOf(false)
    var entryNameDraft by mutableStateOf("")

    /** 「未签名APK需要核心破解」确认框（对应旧版 askRootInstall 的双按钮 AlertDialog）。 */
    var rootPrompt by mutableStateOf(false)

    // ── 轻提示 ──
    val snackbar = SnackbarHostState()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 重要结果用 Snackbar（轻提示仍然走 Toast，见各 Activity）。 */
    fun snack(message: String) {
        scope.launch { snackbar.showSnackbar(message, duration = SnackbarDuration.Short) }
    }

    /** 重要结果再给一个必须点掉的对话框（安装成功 / 被系统拒绝 / 失败）。 */
    fun alert(title: String, message: String) {
        alertTitle = title
        alertMessage = message
    }

    fun log(line: String) {
        logs.add(line)
    }

    /** 写一条「小节标题」（取代旧的 `──── xxx ────` 画线分隔）。 */
    fun section(title: String) {
        logs.add(LOG_SECTION + title)
    }

    fun clearLogs() {
        logs.clear()
    }

    fun dismissAlert() {
        alertTitle = null
    }

    /** 同步「待替换条目」列表（业务侧 putMap 仍是真的存储，这里只做展示镜像）。 */
    fun setReplaceEntries(items: List<ReplaceEntry>) {
        replaceEntries.clear()
        replaceEntries.addAll(items)
        entryCount = items.size
    }
}

class SettingsUiState {

    var followSystem by mutableStateOf(true)
    var darkMode by mutableStateOf(false)

    /** 底栏样式（三选一）。落盘键 `bar_style`，见 [BottomBarStyle]。 */
    var barStyle by mutableStateOf(BottomBarStyle.default())

    var resign by mutableStateOf(true)
    var v1 by mutableStateOf(true)
    var v2 by mutableStateOf(true)
    var v3 by mutableStateOf(true)
    var v4 by mutableStateOf(false)

    var keyStatus by mutableStateOf("")
    var keyPass by mutableStateOf("")

    var outputPath by mutableStateOf("")

    val snackbar = SnackbarHostState()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun snack(message: String) {
        scope.launch { snackbar.showSnackbar(message, duration = SnackbarDuration.Short) }
    }
}
