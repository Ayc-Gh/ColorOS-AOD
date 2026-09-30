package com.op.aod.enhance.hook

import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.op.aod.enhance.data.AodConfigContract
import java.util.concurrent.atomic.AtomicLong

internal object AodDurationHook {
    private const val DOZE_SERVICE="com.android.systemui.doze.DozeService"
    private val mainHandler=Handler(Looper.getMainLooper())
    private val sessionToken=AtomicLong(0L)
    @Volatile private var pending:Runnable?=null

    fun HookRuntime.hookAodDurationLimit(){
        val clazz=findClass(DOZE_SERVICE)
        runCatching{
            val method=findMethod(clazz,"onDreamingStarted")
            intercept("aod.duration.started",method){chain->
                val result=chain.proceed()
                chain.getThisObject()?.let(::schedule)
                result
            }
        }.onSuccess{AodLog.i("DURATION_HOOK","onDreamingStarted registered")}.onFailure{AodLog.e("DURATION_HOOK","onDreamingStarted unavailable",it)}
        runCatching{
            val method=findMethod(clazz,"onDreamingStopped")
            intercept("aod.duration.stopped",method){chain->cancel("DozeService.onDreamingStopped");chain.proceed()}
        }.onSuccess{AodLog.i("DURATION_HOOK","onDreamingStopped registered")}.onFailure{AodLog.e("DURATION_HOOK","onDreamingStopped unavailable",it)}
    }

    private fun schedule(dozeService:Any){
        cancel("new-session")
        val cfg=AodConfigReader.read(MainHook.hostAppContext)
        val limitMs=durationLimitMs(cfg)
        val modeName=durationModeName(cfg.aodDurationMode)
        if(limitMs==null){AodLog.i("AOD_DURATION","mode=$modeName action=no-module-limit");return}
        val token=sessionToken.incrementAndGet();val startedAt=SystemClock.elapsedRealtime()
        val task=Runnable{
            if(sessionToken.get()!=token)return@Runnable
            val interactive=runCatching{MainHook.hostAppContext?.getSystemService(PowerManager::class.java)?.isInteractive}.getOrNull()
            if(interactive==true){pending=null;return@Runnable}
            val result=runCatching{dozeService.javaClass.getMethod("finish").invoke(dozeService)}
            if(result.isSuccess)AodLog.i("AOD_DURATION_EXPIRED","token=$token mode=$modeName limitMs=$limitMs elapsedMs=${SystemClock.elapsedRealtime()-startedAt}") else AodLog.e("AOD_DURATION_EXPIRED","finish failed",result.exceptionOrNull())
            pending=null
        }
        pending=task;mainHandler.postDelayed(task,limitMs)
    }
    private fun cancel(reason:String){pending?.let{mainHandler.removeCallbacks(it)};pending=null;sessionToken.incrementAndGet();AodLog.d("AOD_DURATION_CANCEL","reason=$reason")}
    private fun durationLimitMs(cfg:AodConfig):Long?=when(cfg.aodDurationMode){
        AodConfigContract.DURATION_MODE_SYSTEM,AodConfigContract.DURATION_MODE_ALWAYS->null
        AodConfigContract.DURATION_MODE_30_SECONDS->30_000L
        AodConfigContract.DURATION_MODE_1_MINUTE->60_000L
        AodConfigContract.DURATION_MODE_5_MINUTES->300_000L
        AodConfigContract.DURATION_MODE_10_MINUTES->600_000L
        AodConfigContract.DURATION_MODE_30_MINUTES->1_800_000L
        AodConfigContract.DURATION_MODE_60_MINUTES->3_600_000L
        AodConfigContract.DURATION_MODE_CUSTOM->cfg.aodDurationCustomMinutes.coerceIn(AodConfigContract.MIN_AOD_DURATION_CUSTOM_MINUTES,AodConfigContract.MAX_AOD_DURATION_CUSTOM_MINUTES).toLong()*60_000L
        else->null
    }
    private fun durationModeName(mode:Int)=arrayOf("system","30s","1m","5m","10m","30m","60m","always","custom").getOrElse(mode){"system"}
}
