package com.op.aod.enhance.hook

import android.content.Context
import android.content.SharedPreferences
import com.op.aod.enhance.data.AodConfigContract
import com.op.aod.enhance.data.AodConfigStore
import com.op.aod.enhance.data.AodValueSanitizer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal data class AodConfig(
    val initDark:Int=AodConfigContract.DEFAULT_INIT_DARK,
    val initBright:Int=AodConfigContract.DEFAULT_INIT_BRIGHT,
    val runningMultiplier:Float=AodConfigContract.DEFAULT_RUNNING_MULTIPLIER,
    val useSystemInitDark:Boolean=AodConfigContract.DEFAULT_USE_SYSTEM_INIT_DARK,
    val useSystemInitBright:Boolean=AodConfigContract.DEFAULT_USE_SYSTEM_INIT_BRIGHT,
    val useSystemRunningMultiplier:Boolean=AodConfigContract.DEFAULT_USE_SYSTEM_RUNNING_MULTIPLIER,
    val enablePanoramic:Boolean=AodConfigContract.DEFAULT_ENABLE_PANORAMIC,
    val enableSettingsSupport:Boolean=AodConfigContract.DEFAULT_ENABLE_SETTINGS_SUPPORT,
    val blockSingleClick:Boolean=AodConfigContract.DEFAULT_BLOCK_SINGLE_CLICK,
    val blockLowLightHide:Boolean=AodConfigContract.DEFAULT_BLOCK_LOW_LIGHT_HIDE,
    val aodDurationMode:Int=AodConfigContract.DEFAULT_AOD_DURATION_MODE,
    val aodDurationCustomMinutes:Int=AodConfigContract.DEFAULT_AOD_DURATION_CUSTOM_MINUTES,
)

internal object AodConfigReader {
    private val DEFAULT_CONFIG=AodConfig()
    private val prefsRef=AtomicReference<SharedPreferences?>(null)
    private val cachedRef=AtomicReference<AodConfig?>(null)
    private val refreshing=AtomicBoolean(false)
    private val lastRefreshNs=AtomicLong(0L)
    private val lastFailureNs=AtomicLong(Long.MIN_VALUE)

    fun bindPrefs(prefs:SharedPreferences,hostPackage:String){
        prefsRef.set(prefs)
        cachedRef.set(null);lastRefreshNs.set(0L);lastFailureNs.set(Long.MIN_VALUE)
        AodLog.i("CONFIG_BRIDGE","bound host=$hostPackage remote=true")
    }

    fun read(@Suppress("UNUSED_PARAMETER") context:Context?):AodConfig{
        val now=System.nanoTime(); val cached=cachedRef.get()
        val stale=cached==null||now-lastRefreshNs.get()>=CACHE_TTL_NS
        val failed=lastFailureNs.get(); val retryAllowed=failed==Long.MIN_VALUE||now-failed>=FAILURE_BACKOFF_NS
        if(stale&&retryAllowed) refresh(now)
        return cachedRef.get()?:DEFAULT_CONFIG
    }

    private fun refresh(now:Long){
        if(!refreshing.compareAndSet(false,true)) return
        try{
            val prefs=prefsRef.get() ?: run { lastFailureNs.set(now); return }
            val all=runCatching{prefs.all}.onFailure{AodLog.e("CONFIG_PREFS_READ","read failed",it)}.getOrNull()
            if(all==null){lastFailureNs.set(now);return}
            val parsed=fromMap(all)
            cachedRef.set(parsed);lastRefreshNs.set(now);lastFailureNs.set(Long.MIN_VALUE)
            AodLog.d("CONFIG_REFRESH","keys=${all.size} durationMode=${parsed.aodDurationMode} customMinutes=${parsed.aodDurationCustomMinutes} blockSingle=${parsed.blockSingleClick} blockLowLight=${parsed.blockLowLightHide}")
        }finally{refreshing.set(false)}
    }

    private fun fromMap(all:Map<String,*>):AodConfig{
        fun int(k:String,d:Int)=(all[k] as? Number)?.toInt()?:d
        fun float(k:String,d:Float)=(all[k] as? Number)?.toFloat()?:d
        fun bool(k:String,d:Boolean)=all[k] as? Boolean?:d
        return AodConfig(
            initDark=AodValueSanitizer.sanitizeBrightness(int(AodConfigContract.KEY_INIT_DARK,AodConfigContract.DEFAULT_INIT_DARK)),
            initBright=AodValueSanitizer.sanitizeBrightness(int(AodConfigContract.KEY_INIT_BRIGHT,AodConfigContract.DEFAULT_INIT_BRIGHT)),
            runningMultiplier=AodValueSanitizer.sanitizeRunningMultiplier(float(AodConfigContract.KEY_RUNNING_MULTIPLIER,AodConfigContract.DEFAULT_RUNNING_MULTIPLIER),AodConfigContract.DEFAULT_RUNNING_MULTIPLIER),
            useSystemInitDark=bool(AodConfigContract.KEY_USE_SYSTEM_INIT_DARK,AodConfigContract.DEFAULT_USE_SYSTEM_INIT_DARK),
            useSystemInitBright=bool(AodConfigContract.KEY_USE_SYSTEM_INIT_BRIGHT,AodConfigContract.DEFAULT_USE_SYSTEM_INIT_BRIGHT),
            useSystemRunningMultiplier=bool(AodConfigContract.KEY_USE_SYSTEM_RUNNING_MULTIPLIER,AodConfigContract.DEFAULT_USE_SYSTEM_RUNNING_MULTIPLIER),
            enablePanoramic=bool(AodConfigContract.KEY_ENABLE_PANORAMIC,AodConfigContract.DEFAULT_ENABLE_PANORAMIC),
            enableSettingsSupport=bool(AodConfigContract.KEY_ENABLE_SETTINGS_SUPPORT,AodConfigContract.DEFAULT_ENABLE_SETTINGS_SUPPORT),
            blockSingleClick=bool(AodConfigContract.KEY_BLOCK_SINGLE_CLICK,AodConfigContract.DEFAULT_BLOCK_SINGLE_CLICK),
            blockLowLightHide=bool(AodConfigContract.KEY_BLOCK_LOW_LIGHT_HIDE,AodConfigContract.DEFAULT_BLOCK_LOW_LIGHT_HIDE),
            aodDurationMode=int(AodConfigContract.KEY_AOD_DURATION_MODE,AodConfigContract.DEFAULT_AOD_DURATION_MODE).coerceIn(AodConfigContract.DURATION_MODE_SYSTEM,AodConfigContract.DURATION_MODE_CUSTOM),
            aodDurationCustomMinutes=int(AodConfigContract.KEY_AOD_DURATION_CUSTOM_MINUTES,AodConfigContract.DEFAULT_AOD_DURATION_CUSTOM_MINUTES).coerceIn(AodConfigContract.MIN_AOD_DURATION_CUSTOM_MINUTES,AodConfigContract.MAX_AOD_DURATION_CUSTOM_MINUTES),
        )
    }
    private const val CACHE_TTL_NS=500_000_000L
    private const val FAILURE_BACKOFF_NS=5_000_000_000L
}
