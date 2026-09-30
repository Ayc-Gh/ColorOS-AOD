package com.op.aod.enhance.data

import android.content.Context
import android.content.SharedPreferences
import com.op.aod.enhance.AodApplication
import io.github.libxposed.service.XposedService
import java.util.concurrent.atomic.AtomicReference

object AodConfigStore {
    const val PREFS_NAME = "aod_config"
    private val DEFAULT_CONFIG = AodUiConfig()
    private val cachedRef = AtomicReference<AodUiConfig?>()

    private fun local(context: Context)=context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE)
    private fun remote():SharedPreferences?=AodApplication.service()?.getRemotePreferences(PREFS_NAME)

    fun onXposedServiceBound(context:Context,service:XposedService){
        val local=local(context); val remote=service.getRemotePreferences(PREFS_NAME)
        if(remote.all.isEmpty()&&local.all.isNotEmpty()){
            copyAll(local,remote)
        }
    }

    fun read(context: Context): AodUiConfig {
        val source=remote()?:local(context)
        val fresh=runCatching{readMap(source.all)}.getOrNull()
        if(fresh!=null){cachedRef.set(fresh);return fresh}
        return cachedRef.get()?:DEFAULT_CONFIG
    }

    fun write(context: Context, cfg: AodUiConfig): Boolean {
        val safe = cfg.copy(
            initDark=AodValueSanitizer.sanitizeBrightness(cfg.initDark),
            initBright=AodValueSanitizer.sanitizeBrightness(cfg.initBright),
            runningMultiplier=AodValueSanitizer.sanitizeRunningMultiplier(cfg.runningMultiplier,AodConfigContract.DEFAULT_RUNNING_MULTIPLIER),
            aodDurationMode=cfg.aodDurationMode.coerceIn(AodConfigContract.DURATION_MODE_SYSTEM,AodConfigContract.DURATION_MODE_CUSTOM),
            aodDurationCustomMinutes=cfg.aodDurationCustomMinutes.coerceIn(AodConfigContract.MIN_AOD_DURATION_CUSTOM_MINUTES,AodConfigContract.MAX_AOD_DURATION_CUSTOM_MINUTES),
        )
        val localOk=writePrefs(local(context),safe)
        val remotePrefs=remote()
        val remoteOk=remotePrefs?.let{writePrefs(it,safe)}?:true
        if(localOk&&remoteOk)cachedRef.set(safe)
        return localOk&&remoteOk
    }

    private fun writePrefs(p:SharedPreferences,c:AodUiConfig):Boolean = runCatching {
        p.edit().apply {
            putInt(AodConfigContract.KEY_INIT_DARK,c.initDark);putInt(AodConfigContract.KEY_INIT_BRIGHT,c.initBright)
            putFloat(AodConfigContract.KEY_RUNNING_MULTIPLIER,c.runningMultiplier)
            putBoolean(AodConfigContract.KEY_USE_SYSTEM_INIT_DARK,c.useSystemInitDark);putBoolean(AodConfigContract.KEY_USE_SYSTEM_INIT_BRIGHT,c.useSystemInitBright)
            putBoolean(AodConfigContract.KEY_USE_SYSTEM_RUNNING_MULTIPLIER,c.useSystemRunningMultiplier)
            putBoolean(AodConfigContract.KEY_ENABLE_PANORAMIC,c.enablePanoramic);putBoolean(AodConfigContract.KEY_ENABLE_SETTINGS_SUPPORT,c.enableSettingsSupport)
            putBoolean(AodConfigContract.KEY_BLOCK_SINGLE_CLICK,c.blockSingleClick);putBoolean(AodConfigContract.KEY_BLOCK_LOW_LIGHT_HIDE,c.blockLowLightHide)
            putInt(AodConfigContract.KEY_AOD_DURATION_MODE,c.aodDurationMode);putInt(AodConfigContract.KEY_AOD_DURATION_CUSTOM_MINUTES,c.aodDurationCustomMinutes)
        }.commit()
    }.getOrDefault(false)

    private fun copyAll(from:SharedPreferences,to:SharedPreferences){
        val e=to.edit()
        for((k,v) in from.all) when(v){
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Float -> e.putFloat(k, v)
            is String -> e.putString(k, v)
            is Set<*> -> {
                @Suppress("UNCHECKED_CAST")
                e.putStringSet(k, v as Set<String>)
            }
        }
        e.commit()
    }

    private fun readMap(all: Map<String,*>)=AodUiConfig(
        initDark=AodValueSanitizer.sanitizeBrightness((all[AodConfigContract.KEY_INIT_DARK] as? Number)?.toInt()?:AodConfigContract.DEFAULT_INIT_DARK),
        initBright=AodValueSanitizer.sanitizeBrightness((all[AodConfigContract.KEY_INIT_BRIGHT] as? Number)?.toInt()?:AodConfigContract.DEFAULT_INIT_BRIGHT),
        runningMultiplier=AodValueSanitizer.sanitizeRunningMultiplier((all[AodConfigContract.KEY_RUNNING_MULTIPLIER] as? Number)?.toFloat()?:AodConfigContract.DEFAULT_RUNNING_MULTIPLIER,AodConfigContract.DEFAULT_RUNNING_MULTIPLIER),
        useSystemInitDark=all[AodConfigContract.KEY_USE_SYSTEM_INIT_DARK] as? Boolean?:AodConfigContract.DEFAULT_USE_SYSTEM_INIT_DARK,
        useSystemInitBright=all[AodConfigContract.KEY_USE_SYSTEM_INIT_BRIGHT] as? Boolean?:AodConfigContract.DEFAULT_USE_SYSTEM_INIT_BRIGHT,
        useSystemRunningMultiplier=all[AodConfigContract.KEY_USE_SYSTEM_RUNNING_MULTIPLIER] as? Boolean?:AodConfigContract.DEFAULT_USE_SYSTEM_RUNNING_MULTIPLIER,
        enablePanoramic=all[AodConfigContract.KEY_ENABLE_PANORAMIC] as? Boolean?:AodConfigContract.DEFAULT_ENABLE_PANORAMIC,
        enableSettingsSupport=all[AodConfigContract.KEY_ENABLE_SETTINGS_SUPPORT] as? Boolean?:AodConfigContract.DEFAULT_ENABLE_SETTINGS_SUPPORT,
        blockSingleClick=all[AodConfigContract.KEY_BLOCK_SINGLE_CLICK] as? Boolean?:AodConfigContract.DEFAULT_BLOCK_SINGLE_CLICK,
        blockLowLightHide=all[AodConfigContract.KEY_BLOCK_LOW_LIGHT_HIDE] as? Boolean?:AodConfigContract.DEFAULT_BLOCK_LOW_LIGHT_HIDE,
        aodDurationMode=((all[AodConfigContract.KEY_AOD_DURATION_MODE] as? Number)?.toInt()?:AodConfigContract.DEFAULT_AOD_DURATION_MODE).coerceIn(AodConfigContract.DURATION_MODE_SYSTEM,AodConfigContract.DURATION_MODE_CUSTOM),
        aodDurationCustomMinutes=((all[AodConfigContract.KEY_AOD_DURATION_CUSTOM_MINUTES] as? Number)?.toInt()?:AodConfigContract.DEFAULT_AOD_DURATION_CUSTOM_MINUTES).coerceIn(AodConfigContract.MIN_AOD_DURATION_CUSTOM_MINUTES,AodConfigContract.MAX_AOD_DURATION_CUSTOM_MINUTES),
    )
}
