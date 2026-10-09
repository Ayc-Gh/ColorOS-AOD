package com.op.aod.enhance.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.op.aod.enhance.AodApplication
import io.github.libxposed.service.XposedService

object AodConfigStore {
    const val PREFS_NAME = "aod_config"
    private val lock = Any()
    private var synchronizer: AodConfigSync? = null

    private fun sync(context: Context): AodConfigSync {
        synchronizer?.let { return it }
        val app = context.applicationContext
        val marker = app.getSharedPreferences("aod_config_sync", Context.MODE_PRIVATE)
        return AodConfigSync(
            Preferences(app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)),
            object : AodConfigSync.Pending {
                override fun get() = marker.getBoolean("pending", false)
                override fun set(value: Boolean) = marker.edit().putBoolean("pending", value).commit()
            },
        ).also { synchronizer = it }
    }

    private fun remote() = AodApplication.service()?.getRemotePreferences(PREFS_NAME)?.let(::Preferences)

    fun onXposedServiceBound(context: Context, service: XposedService): Boolean = synchronized(lock) {
        runCatching {
            sync(context).bind(Preferences(service.getRemotePreferences(PREFS_NAME)))
        }.onFailure { Log.e("AOD_Enhance", "CONFIG_SYNC: binding failed; local edits retained", it) }
            .getOrDefault(false)
    }

    fun read(context: Context): AodConfig = synchronized(lock) { sync(context).read { remote() } }

    /** Success means the edit is durable locally; an unavailable bridge retries on bind. */
    fun write(context: Context, config: AodConfig): Boolean = synchronized(lock) {
        runCatching { sync(context).write(config) { remote() } }
            .onFailure { Log.e("AOD_Enhance", "CONFIG_SYNC: save failed", it) }.getOrDefault(false)
    }

    private class Preferences(private val preferences: SharedPreferences) : AodConfigSync.Storage {
        override fun read(): Map<String, *> = preferences.all
        override fun write(config: AodConfig): Boolean {
            val editor = preferences.edit()
            for ((key, value) in AodConfigCodec.encode(config)) {
                when (value) {
                    is Int -> editor.putInt(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                }
            }
            return editor.commit()
        }
    }
}
