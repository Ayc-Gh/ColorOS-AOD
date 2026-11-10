package com.op.aod.enhance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.op.aod.enhance.BuildConfig
import com.op.aod.enhance.data.RuntimeProtocol
import com.op.aod.enhance.data.RuntimeStatusClient
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

class RuntimeStatusActivity : ComponentActivity() {
    private lateinit var client: RuntimeStatusClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = RuntimeStatusClient(this)
        setContent {
            val reports by client.reports.collectAsState()
            val message by client.message.collectAsState()
            MiuixTheme {
                Scaffold(topBar = { SmallTopAppBar(title = "功能运行状态") }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ConfigSyncStatusCard()
                        Text("配置同步与功能运行分别检查。已加载表示入口可用；已触发表示系统调用过该入口，仍需确认实际效果。")
                        Text(message)
                        TextButton(text = "刷新功能状态", onClick = { client.refresh() })
                        RuntimeProtocol.hosts.forEach { host ->
                            Card {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(if (host == "com.android.systemui") "系统界面" else "系统息屏设置")
                                    val report = reports[host]
                                    if (report == null) {
                                        Text("未知：尚未收到回复。更新后请重启对应进程；系统息屏设置需打开后再检查。")
                                    } else if (report.optInt("code") != BuildConfig.VERSION_CODE) {
                                        Text("仍在运行旧版 ${report.optString("version")}，请重启对应进程。")
                                    } else {
                                        Text("已加载 ${report.optString("version")}")
                                        val features = report.optJSONArray("features")
                                        if (features != null) for (i in 0 until features.length()) {
                                            val f = features.getJSONObject(i)
                                            val title = TITLES[f.optString("name")] ?: continue
                                            val state = when (f.optString("state")) { "loaded" -> "已加载"; "partial" -> "部分可用"; else -> "不可用" }
                                            Text("$title：$state，${if (f.optInt("hits") > 0) "已触发" else "尚未触发"}")
                                            if (f.optString("state") != "loaded") Text("当前系统接口不完整，请查看框架日志或更新适配。")
                                        }
                                        report.optJSONObject("brightness")?.let { b ->
                                            val text = when {
                                                b.optBoolean("initial") -> "最近亮度处理：保持初始亮度，避免重复放大"
                                                b.optBoolean("system") -> "最近亮度处理：跟随系统"
                                                b.optDouble("original", 0.0) > 0.0 -> "最近亮度处理：实际约 ${String.format(Locale.ROOT, "%.2f", b.optDouble("target") / b.optDouble("original"))} 倍"
                                                else -> "最近亮度处理：保留系统特殊值"
                                            }
                                            Text(text)
                                        }
                                    }
                                }
                            }
                        }
                        Text("面板实际刷新率：尚未可靠测得。系统显示模式、内容更新帧率与面板扫描频率不能互相替代。")
                        Text("仅在打开本页或点击刷新时检查，不持续采样。单击、双击及夜间低光场景仍需实测。")
                    }
                }
            }
        }
    }
    override fun onStart() { super.onStart(); client.start() }
    override fun onStop() { client.stop(); super.onStop() }
    private companion object {
        val TITLES = mapOf("Brightness.Init" to "初始亮度", "Brightness.Running" to "自动亮度倍率", "Duration.NativeTimeout" to "显示时长",
            "Panoramic.AllDay" to "全景息屏支持", "SingleClick.Block" to "单击屏蔽", "LowLight.Block" to "低光保持", "AodSettings.AllDay" to "全天选项")
    }
}
