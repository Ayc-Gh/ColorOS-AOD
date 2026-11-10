package com.op.aod.enhance.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.op.aod.enhance.AodApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

internal class RuntimeStatusClient(context: Context) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val mutableReports = MutableStateFlow<Map<String, JSONObject>>(emptyMap())
    val reports = mutableReports.asStateFlow()
    private val mutableMessage = MutableStateFlow("尚未检查")
    val message = mutableMessage.asStateFlow()
    private var nonce: String? = null
    private var listening = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != RuntimeProtocol.REPLY || nonce == null || intent.getStringExtra(RuntimeProtocol.NONCE) != nonce) return
            val host = intent.getStringExtra("host") ?: return
            if (host !in RuntimeProtocol.hosts) return
            if (Build.VERSION.SDK_INT >= 34 && sentFromUid != runCatching { app.packageManager.getPackageUid(host, 0) }.getOrNull()) return
            val data = runCatching { JSONObject(intent.getStringExtra("payload") ?: "") }.getOrNull() ?: return
            if (data.optString("host") != host) return
            mutableReports.value = mutableReports.value + (host to data)
        }
    }
    fun start() {
        if (!listening) { app.registerReceiver(receiver, IntentFilter(RuntimeProtocol.REPLY), Context.RECEIVER_EXPORTED); listening = true }
        refresh()
    }
    fun stop() {
        nonce = null; handler.removeCallbacksAndMessages(null)
        if (listening) { app.unregisterReceiver(receiver); listening = false }
    }
    fun refresh() {
        if (!listening) return
        handler.removeCallbacksAndMessages(null); mutableReports.value = emptyMap(); nonce = null
        val service = AodApplication.service()
        if (service == null) { mutableMessage.value = "等待配置服务连接，请稍后点刷新"; return }
        val token = UUID.randomUUID().toString()
        val saved = runCatching { service.getRemotePreferences(RuntimeProtocol.PREFS).edit().putString(RuntimeProtocol.NONCE, token).commit() }.getOrDefault(false)
        if (!saved) { mutableMessage.value = "检查请求未能保存，请重新连接配置服务"; return }
        nonce = token; mutableMessage.value = "正在检查…"
        RuntimeProtocol.hosts.forEach { RuntimeProtocol.send(app, Intent(RuntimeProtocol.QUERY).setPackage(it).putExtra(RuntimeProtocol.NONCE, token)) }
        handler.postDelayed({ mutableMessage.value = "检查完成；未收到回复的进程保持未知状态" }, 4000)
    }
}
