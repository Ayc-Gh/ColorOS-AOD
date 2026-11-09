package com.op.aod.enhance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.op.aod.enhance.data.AodConfigStore
import com.op.aod.enhance.data.AodInputValidation
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

class BrightnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AodConfigStore.read(this)
        setContent { MiuixTheme { BrightnessScreen() } }
    }
}

@Composable
private fun BrightnessScreen() {
    val config by AodConfigStore.config.collectAsState()
    val context = LocalContext.current
    Scaffold(topBar = { SmallTopAppBar(title = "AOD亮度设置") }) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConfigSyncStatusCard()
            SwitchPreference(
                title = "暗光初始亮度使用系统默认",
                summary = "开启后保留 ColorOS 本次计算的暗光初始亮度",
                checked = config.useSystemInitDark,
                onCheckedChange = { enabled -> AodConfigStore.update(context) { it.copy(useSystemInitDark = enabled) } },
            )
            if (config.useSystemInitDark) {
                Text("当前：跟随系统；自定义值 ${config.initDark} 已保留")
            } else {
                BrightnessNumberControl(
                    value = config.initDark.toFloat(), title = "熄屏前暗光环境AOD亮度",
                    fieldLabel = "暗光初始亮度（0–255）", range = 0f..255f, steps = 254,
                    format = { it.roundToInt().toString() }, quantize = { it.roundToInt().toFloat() },
                    parse = { AodInputValidation.brightness(it)?.toFloat() }, errorHint = "请输入 0–255 的整数",
                    onChange = { value -> AodConfigStore.update(context) { it.copy(initDark = value.roundToInt()) } },
                )
            }
            SwitchPreference(
                title = "亮光初始亮度使用系统默认",
                summary = "开启后保留 ColorOS 本次计算的亮光初始亮度",
                checked = config.useSystemInitBright,
                onCheckedChange = { enabled -> AodConfigStore.update(context) { it.copy(useSystemInitBright = enabled) } },
            )
            if (config.useSystemInitBright) {
                Text("当前：跟随系统；自定义值 ${config.initBright} 已保留")
            } else {
                BrightnessNumberControl(
                    value = config.initBright.toFloat(), title = "熄屏前亮光环境AOD亮度",
                    fieldLabel = "亮光初始亮度（0–255）", range = 0f..255f, steps = 254,
                    format = { it.roundToInt().toString() }, quantize = { it.roundToInt().toFloat() },
                    parse = { AodInputValidation.brightness(it)?.toFloat() }, errorHint = "请输入 0–255 的整数",
                    onChange = { value -> AodConfigStore.update(context) { it.copy(initBright = value.roundToInt()) } },
                )
            }
            SwitchPreference(
                title = "自动亮度倍率使用系统默认",
                summary = "开启后完整保留系统原有 AOD 自动亮度结果",
                checked = config.useSystemRunningMultiplier,
                onCheckedChange = { enabled -> AodConfigStore.update(context) { it.copy(useSystemRunningMultiplier = enabled) } },
            )
            if (config.useSystemRunningMultiplier) {
                Text("当前：跟随系统；自定义倍率 ${config.runningMultiplier}× 已保留")
            } else {
                BrightnessNumberControl(
                    value = config.runningMultiplier, title = "熄屏时AOD自动亮度倍率",
                    fieldLabel = "自动亮度倍率（1.0–2.0）", range = 1f..2f, steps = 9,
                    format = { it.toString() }, quantize = { (it * 10).roundToInt() / 10f },
                    parse = AodInputValidation::multiplier, errorHint = "请输入 1.0–2.0 的数值",
                    onChange = { value -> AodConfigStore.update(context) { it.copy(runningMultiplier = value) } },
                    keyboardType = KeyboardType.Decimal,
                )
            }
        }
    }
}
