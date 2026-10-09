package com.op.aod.enhance.hook

import android.content.Context
import android.content.SharedPreferences
import com.op.aod.enhance.data.AodConfig
import com.op.aod.enhance.data.AodConfigCodec
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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
            val parsed=AodConfigCodec.decode(all)
            cachedRef.set(parsed);lastRefreshNs.set(now);lastFailureNs.set(Long.MIN_VALUE)
            AodLog.d("CONFIG_REFRESH","keys=${all.size} durationMode=${parsed.aodDurationMode} customMinutes=${parsed.aodDurationCustomMinutes} blockSingle=${parsed.blockSingleClick} blockLowLight=${parsed.blockLowLightHide}")
        }finally{refreshing.set(false)}
    }

    private const val CACHE_TTL_NS=500_000_000L
    private const val FAILURE_BACKOFF_NS=5_000_000_000L
}
