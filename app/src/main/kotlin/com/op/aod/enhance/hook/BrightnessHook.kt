package com.op.aod.enhance.hook

import com.op.aod.enhance.data.AodConfigContract
import com.op.aod.enhance.data.AodValueSanitizer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

internal object BrightnessHook {
    private val pendingInitBrightness=AtomicReference<Int?>(null)

    fun HookRuntime.hookInitBrightnessFix(){
        val method=findMethod(OPLUS_DOZE_SERVICE_EX_IMPL,"setBrightnessBeforeDozing")
        intercept("aod.brightness.init",method){chain->
            val original=chain.proceed() as? Int ?: return@intercept null
            if(original==-1)return@intercept original
            val cfg=AodConfigReader.read(MainHook.hostAppContext)
            val isDark=original<INIT_DARK_THRESHOLD
            val useSystem=if(isDark)cfg.useSystemInitDark else cfg.useSystemInitBright
            val target=if(useSystem)original else AodValueSanitizer.sanitizeBrightness(if(isDark)cfg.initDark else cfg.initBright)
            pendingInitBrightness.set(target)
            AodLog.d("BRIGHTNESS_INIT","original=$original target=$target")
            target
        }
    }

    fun HookRuntime.hookRunningBrightnessBoost(){
        val main=runCatching{hookBrightnessMethod(DOZE_SERVICE,"setDozeScreenBrightness","DozeService","aod.brightness.running.main",true)}
        if(main.isSuccess){AodLog.i("BRIGHTNESS_HOOK","main DozeService path registered");return}
        runCatching{hookBrightnessMethod(OPLUS_DOZE_SERVICE_EX_IMPL,"setDozeScreenBrightness","OplusDozeService","aod.brightness.running.oplus",true)}
        for(name in FALLBACK_METHOD_NAMES){
            if(runCatching{hookBrightnessMethod(OPLUS_DOZE_SERVICE_EX_IMPL,name,name,"aod.brightness.running.$name",false)}.isSuccess)return
        }
    }

    private fun HookRuntime.hookBrightnessMethod(className:String,methodName:String,label:String,id:String,consumePending:Boolean){
        val method=findMethod(className,methodName,Int::class.javaPrimitiveType!!)
        intercept(id,method){chain->
            val original=chain.getArg(0) as? Int ?: return@intercept chain.proceed()
            if(consumePending&&consumePendingInit(original))return@intercept chain.proceed()
            val target=boost(label,original)
            if(target==original)return@intercept chain.proceed()
            val args=chain.getArgs().toTypedArray();args[0]=target;chain.proceed(args)
        }
    }

    private fun boost(path:String,original:Int):Int{
        if(original<0)return original
        val cfg=AodConfigReader.read(MainHook.hostAppContext)
        if(cfg.useSystemRunningMultiplier)return original
        val multiplier=AodValueSanitizer.sanitizeRunningMultiplier(cfg.runningMultiplier,AodConfigContract.DEFAULT_RUNNING_MULTIPLIER)
        val target=AodValueSanitizer.sanitizeBrightness((original*multiplier).roundToInt())
        AodLog.d("BRIGHTNESS_RUNNING","path=$path original=$original multiplier=$multiplier target=$target")
        return target
    }

    private fun consumePendingInit(original:Int):Boolean{val pending=pendingInitBrightness.getAndSet(null)?:return false;return original==pending}
    private const val OPLUS_DOZE_SERVICE_EX_IMPL="com.oplus.systemui.aod.OplusDozeServiceExImpl"
    private const val DOZE_SERVICE="com.android.systemui.doze.DozeService"
    private const val INIT_DARK_THRESHOLD=40
    private val FALLBACK_METHOD_NAMES=arrayOf("setBrightnessForFallbackStrategy","setBrightness4FallbackStrategy")
}
