package com.op.aod.enhance.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import com.op.aod.enhance.BuildConfig
import com.op.aod.enhance.data.HookStatusRegistry
import com.op.aod.enhance.data.RuntimeProtocol
import org.json.JSONArray
import org.json.JSONObject

/** Reports only when the manager asks; no timer, file writes or background sampling. */
internal object HostDiagnostics {
    val registry = HookStatusRegistry()
    data class BrightnessSample(val original: Float, val target: Float, val normalized: Boolean, val initial: Boolean, val system: Boolean)
    @Volatile var brightness: BrightnessSample? = null
    private var prefs: SharedPreferences? = null
    private var host = ""
    private var registered = false
    fun bind(preferences: SharedPreferences, packageName: String) { prefs = preferences; host = packageName }
    @Synchronized fun attach(context: Context) {
        if (registered || prefs == null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != RuntimeProtocol.QUERY) return
                val nonce = intent.getStringExtra(RuntimeProtocol.NONCE) ?: return
                // This capability is held in the manager's private framework preferences.
                if (nonce.length !in 32..64 || nonce != runCatching { prefs?.getString(RuntimeProtocol.NONCE, null) }.getOrNull()) return
                if (Build.VERSION.SDK_INT >= 34 && sentFromUid != runCatching { ctx.packageManager.getPackageUid(RuntimeProtocol.APP, 0) }.getOrNull()) return
                val data = JSONObject().put("host", host).put("version", BuildConfig.VERSION_NAME).put("code", BuildConfig.VERSION_CODE)
                val features = JSONArray()
                registry.snapshot().filter { it.name != "Context" }.forEach { s ->
                    features.put(JSONObject().put("name", s.name).put("state", s.state).put("paths", JSONArray(s.paths.values.toList()))
                        .put("hits", s.hits.size).put("failures", JSONArray(s.failures.toList())).put("skipped", JSONArray(s.skipped.toList())))
                }
                data.put("features", features)
                brightness?.let { b ->
                    data.put("brightness", JSONObject().put("original", b.original.takeIf { it.isFinite() } ?: JSONObject.NULL)
                        .put("target", b.target.takeIf { it.isFinite() } ?: JSONObject.NULL).put("normalized", b.normalized)
                        .put("initial", b.initial).put("system", b.system))
                }
                RuntimeProtocol.send(ctx, Intent(RuntimeProtocol.REPLY).setPackage(RuntimeProtocol.APP)
                    .putExtra(RuntimeProtocol.NONCE, nonce).putExtra("host", host).putExtra("payload", data.toString()))
            }
        }
        runCatching { context.registerReceiver(receiver, IntentFilter(RuntimeProtocol.QUERY), Context.RECEIVER_EXPORTED) }
            .onSuccess { registered = true }.onFailure { AodLog.w("RUNTIME_STATUS", "Request receiver unavailable", it) }
    }
}
