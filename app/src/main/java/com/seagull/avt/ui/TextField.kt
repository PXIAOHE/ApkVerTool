package com.seagull.avt.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collect
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

/**
 * 字符串形式的 TextField 包装（内部使用 androidx TextFieldState）。
 * 适用于搜索框、单行/多行输入等场景。
 */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    val state = remember { TextFieldState() }

    // 外部值变化 → 同步到 state（避免循环：内容相同则不写）
    LaunchedEffect(value) {
        if (state.text.toString() != value) {
            state.edit { replace(0, length, value) }
        }
    }

    // state 变化 → 通知外部
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .collect { if (it != value) onValueChange(it) }
    }

    MiuixTextField(
        state = state,
        modifier = modifier,
        label = label,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        enabled = enabled,
        readOnly = readOnly,
    )
}
