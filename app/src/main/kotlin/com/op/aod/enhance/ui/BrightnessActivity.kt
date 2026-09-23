package com.op.aod.enhance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.op.aod.enhance.data.AodConfigContract
import com.op.aod.enhance.data.AodConfigStore
import com.op.aod.enhance.data.AodUiConfig
import com.op.aod.enhance.data.AodValueSanitizer
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class BrightnessActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiuixTheme {
                BrightnessScreen(
                    initial = AodConfigStore.read(this),
                    onSave = { cfg -> AodConfigStore.write(this, cfg) }
                )
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun BrightnessScreen(
    initial: AodUiConfig,
    onSave: (AodUiConfig) -> Unit
) {
    var initDark by remember { mutableFloatStateOf(initial.initDark.toFloat()) }
    var initBright by remember { mutableFloatStateOf(initial.initBright.toFloat()) }
    var runningMultiplier by remember { mutableFloatStateOf(initial.runningMultiplier) }
    var useSystemInitDark by remember { mutableStateOf(initial.useSystemInitDark) }
    var useSystemInitBright by remember { mutableStateOf(initial.useSystemInitBright) }
    var useSystemRunningMultiplier by remember { mutableStateOf(initial.useSystemRunningMultiplier) }
    var dirty by remember { mutableStateOf(false) }

    val currentOnSave by rememberUpdatedState(onSave)
    val context = LocalContext.current
    val latestValues by rememberUpdatedState(Triple(initDark, initBright, runningMultiplier))
    val latestDirty by rememberUpdatedState(dirty)

    fun persist(dark: Float, bright: Float, multiplier: Float) {
        val base = AodConfigStore.read(context)
        currentOnSave(
            base.copy(
                initDark = AodValueSanitizer.sanitizeBrightness(dark.toInt()),
                initBright = AodValueSanitizer.sanitizeBrightness(bright.toInt()),
                runningMultiplier = AodValueSanitizer.sanitizeRunningMultiplier(
                    multiplier,
                    AodConfigContract.DEFAULT_RUNNING_MULTIPLIER,
                ),
            )
        )
    }

    fun persistFlag(transform: (AodUiConfig) -> AodUiConfig) {
        currentOnSave(transform(AodConfigStore.read(context)))
    }

    LaunchedEffect(Unit) {
        snapshotFlow { Triple(initDark, initBright, runningMultiplier) }
            .drop(1)
            .debounce(300)
            .distinctUntilChanged()
            .collect { (dark, bright, multiplier) ->
                persist(dark, bright, multiplier)
                dirty = false
            }
    }

    // 用户在 300ms debounce 窗口内返回时，补写最后一次自定义数值。
    DisposableEffect(Unit) {
        onDispose {
            if (latestDirty) {
                val (dark, bright, multiplier) = latestValues
                persist(dark, bright, multiplier)
            }
        }
    }

    Scaffold(
        topBar = { SmallTopAppBar(title = "AOD亮度设置") }
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SwitchPreference(
                title = "暗光初始亮度使用系统默认",
                summary = "开启后保留 ColorOS 本次计算的暗光初始亮度，不再替换为固定值",
                checked = useSystemInitDark,
                onCheckedChange = {
                    useSystemInitDark = it
                    persistFlag { base -> base.copy(useSystemInitDark = it) }
                },
            )
            if (useSystemInitDark) {
                Text("当前：跟随系统；自定义值 ${initDark.toInt()} 已保留，关闭此开关后继续使用")
            } else {
                Text("熄屏前暗光环境AOD亮度：${initDark.toInt()}")
                Slider(
                    value = initDark,
                    onValueChange = {
                        initDark = it.coerceIn(0f, 255f)
                        dirty = true
                    },
                    valueRange = 0f..255f,
                    steps = 254,
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = initDark.toInt().toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { v ->
                            initDark = AodValueSanitizer.sanitizeBrightness(v).toFloat()
                            dirty = true
                        }
                    },
                    label = "输入熄屏前暗光环境AOD亮度",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            SwitchPreference(
                title = "亮光初始亮度使用系统默认",
                summary = "开启后保留 ColorOS 本次计算的亮光初始亮度，不再替换为固定值",
                checked = useSystemInitBright,
                onCheckedChange = {
                    useSystemInitBright = it
                    persistFlag { base -> base.copy(useSystemInitBright = it) }
                },
            )
            if (useSystemInitBright) {
                Text("当前：跟随系统；自定义值 ${initBright.toInt()} 已保留，关闭此开关后继续使用")
            } else {
                Text("熄屏前亮光环境AOD亮度：${initBright.toInt()}")
                Slider(
                    value = initBright,
                    onValueChange = {
                        initBright = it.coerceIn(0f, 255f)
                        dirty = true
                    },
                    valueRange = 0f..255f,
                    steps = 254,
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = initBright.toInt().toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { v ->
                            initBright = AodValueSanitizer.sanitizeBrightness(v).toFloat()
                            dirty = true
                        }
                    },
                    label = "输入熄屏前亮光环境AOD亮度",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            SwitchPreference(
                title = "自动亮度倍率使用系统默认",
                summary = "开启后不再应用模块倍率补偿，完整保留系统原有 AOD 自动亮度结果",
                checked = useSystemRunningMultiplier,
                onCheckedChange = {
                    useSystemRunningMultiplier = it
                    persistFlag { base -> base.copy(useSystemRunningMultiplier = it) }
                },
            )
            if (useSystemRunningMultiplier) {
                Text("当前：跟随系统；自定义倍率 ${runningMultiplier}× 已保留，关闭此开关后继续使用")
            } else {
                Text("熄屏时AOD自动亮度倍率：${runningMultiplier}×")
                Slider(
                    value = runningMultiplier,
                    onValueChange = {
                        runningMultiplier = ((it * 10).toInt().coerceIn(10, 20) / 10f)
                        dirty = true
                    },
                    valueRange = 1.0f..2.0f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = runningMultiplier.toString(),
                    onValueChange = {
                        val parsed = it.toFloatOrNull()
                        if (parsed != null && parsed.isFinite()) {
                            runningMultiplier = AodValueSanitizer.sanitizeRunningMultiplier(
                                parsed,
                                AodConfigContract.DEFAULT_RUNNING_MULTIPLIER,
                            )
                            dirty = true
                        }
                    },
                    label = "输入熄屏时AOD自动亮度倍率",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}
