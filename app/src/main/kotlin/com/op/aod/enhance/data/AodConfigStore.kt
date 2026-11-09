package com.op.aod.enhance.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.op.aod.enhance.AodApplication
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AodConfigStore {
    const val PREFS_NAME = "aod_config"
    private val lock = Any()
    private var synchronizer: AodConfigSync? = null
    private val mutableConfig = MutableStateFlow(AodConfig())
    val config = mutableConfig.asStateFlow()
    private val mutableStatus = MutableStateFlow(AodSyncStatus())
    val syncStatus = mutableStatus.asStateFlow()

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

    private fun publish(sync: AodConfigSync, config: AodConfig? = null) {
        config?.let { mutableConfig.value = it }
        mutableStatus.value = sync.status
    }

    fun onXposedServiceBound(context: Context, service: XposedService): Boolean = synchronized(lock) {
        val sync = sync(context)
        runCatching {
            val storage = Preferences(service.getRemotePreferences(PREFS_NAME))
            val success = sync.bind(storage)
            // Refresh open child pages after migration or reconnection.
            publish(sync, sync.read { storage })
            success
        }.onFailure { reportFailure(AodSyncIssue.REMOTE, it) }.getOrDefault(false)
    }

    fun onXposedServiceDied() = synchronized(lock) {
        synchronizer?.let { it.disconnected(); publish(it) }
    }

    fun read(context: Context): AodConfig = synchronized(lock) {
        val sync = sync(context)
        sync.read { remote() }.also { publish(sync, it) }
    }

    /** Merge a single control's edit against the latest settings under the same lock. */
    fun update(context: Context, transform: (AodConfig) -> AodConfig): Boolean = synchronized(lock) {
        write(context, transform(read(context)))
    }

    fun write(context: Context, config: AodConfig): Boolean = synchronized(lock) {
        val sync = sync(context)
        runCatching {
            val saved = sync.write(config) { remote() }
            // A failed local save must not appear as an applied setting.
            publish(sync, sync.read { remote() })
            saved
        }.onFailure { reportFailure(AodSyncIssue.LOCAL_SAVE, it) }.getOrDefault(false)
    }

    fun retry(context: Context): Boolean = synchronized(lock) {
        val service = AodApplication.service()
        if (service == null) {
            sync(context).let { it.disconnected(); publish(it) }
            false
        } else onXposedServiceBound(context, service)
    }

    private fun reportFailure(issue: AodSyncIssue, error: Throwable) {
        mutableStatus.value = AodSyncStatus(AodSyncState.FAILED, issue, hasPendingChanges = true)
        Log.e("AOD_Enhance", "CONFIG_SYNC: operation failed", error)
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
