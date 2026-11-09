package com.op.aod.enhance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.op.aod.enhance.data.AodConfigContract
import com.op.aod.enhance.data.AodConfigStore
import com.op.aod.enhance.data.AodInputValidation
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

class FeaturesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AodConfigStore.read(this)
        setContent { MiuixTheme { FeaturesScreen() } }
    }
}

@Composable
private fun FeaturesScreen() {
    val config by AodConfigStore.config.collectAsState()
    val context = LocalContext.current
    Scaffold(
        topBar = { SmallTopAppBar(title = "AOD功能设置", color = MiuixTheme.colorScheme.secondaryContainer) },
        containerColor = MiuixTheme.colorScheme.secondaryContainer,
    ) { paddingValues: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxHeight().scrollEndHaptic().overScrollVertical().padding(horizontal = 12.dp),
            contentPadding = paddingValues,
            overscrollEffect = null,
        ) {
            item { ConfigSyncStatusCard(Modifier.padding(top = 12.dp, bottom = 12.dp)) }
            item {
                Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background)) {
                    SwitchPreference(
                        title = "系统界面-全天全景AOD支持",
                        summary = "让系统界面解锁全天全景 AOD 相关能力；关闭后建议重启系统界面恢复原始能力状态",
                        checked = config.enablePanoramic,
                        onCheckedChange = { enabled -> AodConfigStore.update(context) { it.copy(enablePanoramic = enabled) } },
                    )
                    SwitchPreference(
                        title = "息屏-全天全景AOD开关",
                        summary = "在息屏设置中显示全天全景 AOD 开关",
                        checked = config.enableSettingsSupport,
                        onCheckedChange = { enabled -> AodConfigStore.update(context) { it.copy(enableSettingsSupport = enabled) } },
                    )
                    SwitchPreference(
                        title = "AOD单击唤醒屏蔽",
                        summary = "避免 AOD 单击误触导致唤醒；双击仍正常，修改后近实时生效",
                        checked = config.blockSingleClick,
                        onCheckedChange = { enabled -> AodConfigStore.update(context) { it.copy(blockSingleClick = enabled) } },
                    )
                    SwitchPreference(
                        title = "低光/特殊规则保持AOD显示",
                        summary = "阻止极暗、夜间低光以及当前 ColorOS 的 4 小时特殊规则自动关闭 AOD",
                        checked = config.blockLowLightHide,
                        onCheckedChange = { enabled -> AodConfigStore.update(context) { it.copy(blockLowLightHide = enabled) } },
                    )
                }
            }
            item {
                Text(
                    text = "AOD 目标显示时长：${durationModeLabel(config.aodDurationMode, config.aodDurationCustomMinutes)}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background),
                ) {
                    for (mode in AodConfigContract.DURATION_MODE_SYSTEM..AodConfigContract.DURATION_MODE_CUSTOM) {
                        RadioButtonPreference(
                            title = if (mode == AodConfigContract.DURATION_MODE_CUSTOM) "自定义" else durationModeLabel(mode, config.aodDurationCustomMinutes),
                            selected = config.aodDurationMode == mode,
                            onClick = { AodConfigStore.update(context) { it.copy(aodDurationMode = mode) } },
                        )
                    }
                }
            }
            if (config.aodDurationMode == AodConfigContract.DURATION_MODE_CUSTOM) {
                item {
                    ValidatedNumberField(
                        value = config.aodDurationCustomMinutes.toString(),
                        label = "自定义分钟数（1–1440）",
                        errorHint = "请输入 1–1440 的整数",
                        isValid = { AodInputValidation.durationMinutes(it) != null },
                        onValidValueChange = { text ->
                            AodInputValidation.durationMinutes(text)?.let { minutes ->
                                AodConfigStore.update(context) { it.copy(aodDurationCustomMinutes = minutes) }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                    )
                }
            }
            item {
                Text(
                    text = "目标时长只接管全景 AOD 的普通自动超时；来电、解锁、指纹、主动唤醒等其他退出路径仍按系统原逻辑执行。“始终显示”也不会阻止这些退出路径。",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item { Spacer(Modifier.padding(bottom = 16.dp)) }
        }
    }
}

private fun durationModeLabel(mode: Int, customMinutes: Int): String = when (mode) {
    AodConfigContract.DURATION_MODE_30_SECONDS -> "30 秒"
    AodConfigContract.DURATION_MODE_1_MINUTE -> "1 分钟"
    AodConfigContract.DURATION_MODE_5_MINUTES -> "5 分钟"
    AodConfigContract.DURATION_MODE_10_MINUTES -> "10 分钟"
    AodConfigContract.DURATION_MODE_30_MINUTES -> "30 分钟"
    AodConfigContract.DURATION_MODE_60_MINUTES -> "60 分钟"
    AodConfigContract.DURATION_MODE_ALWAYS -> "始终显示"
    AodConfigContract.DURATION_MODE_CUSTOM -> "自定义 $customMinutes 分钟"
    else -> "使用系统默认"
}
