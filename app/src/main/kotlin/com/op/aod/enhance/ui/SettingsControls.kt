package com.op.aod.enhance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.op.aod.enhance.AodApplication
import com.op.aod.enhance.data.AodConfigStore
import com.op.aod.enhance.data.AodSyncIssue
import com.op.aod.enhance.data.AodSyncState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField

@Composable
internal fun ConfigSyncStatusCard(modifier: Modifier = Modifier) {
    val status by AodConfigStore.syncStatus.collectAsState()
    val connected by AodApplication.configServiceConnected
    val context = LocalContext.current
    val message = when (status.state) {
        AodSyncState.SYNCED -> "已同步：配置已写入框架，请在下一次息屏确认效果"
        AodSyncState.PENDING -> if (status.hasPendingChanges)
            "待同步：修改已暂存本机，配置服务连接后自动同步"
        else "待同步：等待配置服务连接"
        AodSyncState.FAILED -> when (status.issue) {
            AodSyncIssue.LOCAL_SAVE -> "保存失败：未能保存到本机，请重新修改设置"
            AodSyncIssue.LOCAL_BACKUP -> "同步失败：远端配置未能备份到本机，请重试"
            AodSyncIssue.CONFIRMATION -> "同步失败：同步确认未保存，请重试"
            else -> "同步失败：本机配置已保留，请重试"
        }
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "同步状态：$message",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (!connected) Text("请在 LSPosed 启用模块，并从主页重新打开应用以连接配置服务。")
            if (connected && status.state != AodSyncState.SYNCED && status.issue != AodSyncIssue.LOCAL_SAVE) {
                TextButton(text = "重试同步", onClick = { AodConfigStore.retry(context) })
            }
        }
    }
}

/** Keep editable text independent of numbers: empty and partial decimal drafts are allowed. */
@Composable
internal fun ValidatedNumberField(
    value: String,
    label: String,
    errorHint: String,
    isValid: (String) -> Boolean,
    onValidValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
) {
    var draft by rememberSaveable { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(value) { if (!focused) draft = value }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextField(
            value = draft,
            onValueChange = { text ->
                draft = text
                if (isValid(text)) onValidValueChange(text)
            },
            label = label,
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                if (focused && !it.isFocused && isValid(draft)) draft = value
                focused = it.isFocused
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (isValid(draft)) focusManager.clearFocus() }),
            singleLine = true,
        )
        if (!isValid(draft)) Text("$errorHint；无效输入不会保存，当前设置为 $value")
    }
}

@Composable
internal fun BrightnessNumberControl(
    value: Float,
    title: String,
    fieldLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    quantize: (Float) -> Float,
    parse: (String) -> Float?,
    errorHint: String,
    onChange: (Float) -> Boolean,
    keyboardType: KeyboardType = KeyboardType.Number,
) {
    var dragValue by remember { mutableFloatStateOf(value) }
    var dirty by remember { mutableStateOf(false) }
    val latestDirty by rememberUpdatedState(dirty)
    val latestDrag by rememberUpdatedState(dragValue)
    val latestOnChange by rememberUpdatedState(onChange)
    val focusManager = LocalFocusManager.current
    LaunchedEffect(value) { if (!dirty) dragValue = value }
    DisposableEffect(Unit) {
        onDispose { if (latestDirty) latestOnChange(latestDrag) }
    }
    Text("$title：${format(if (dirty) dragValue else value)}")
    Slider(
        value = dragValue,
        onValueChange = {
            focusManager.clearFocus()
            dragValue = quantize(it.coerceIn(range.start, range.endInclusive))
            dirty = true
        },
        onValueChangeFinished = {
            val target = dragValue
            dirty = false
            if (target != value && !onChange(target)) dragValue = value
        },
        valueRange = range,
        steps = steps,
        modifier = Modifier.fillMaxWidth(),
    )
    ValidatedNumberField(
        value = format(value),
        label = fieldLabel,
        errorHint = errorHint,
        isValid = { parse(it) != null },
        onValidValueChange = { text ->
            parse(text)?.let {
                dirty = false
                dragValue = it
                if (it != value) onChange(it)
            }
        },
        keyboardType = keyboardType,
    )
}
