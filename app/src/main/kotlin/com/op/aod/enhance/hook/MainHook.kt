package com.op.aod.enhance.hook

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import com.op.aod.enhance.data.AodConfigStore
import com.op.aod.enhance.hook.AodDurationHook.hookAodDurationLimit
import com.op.aod.enhance.hook.AodSettingsHook.hookAodAllDaySupportSettings
import com.op.aod.enhance.hook.BrightnessHook.hookInitBrightnessFix
import com.op.aod.enhance.hook.BrightnessHook.hookRunningBrightnessBoost
import com.op.aod.enhance.hook.LowLightHideHook.hookLowLightAodHide
import com.op.aod.enhance.hook.PanoramicHook.hookPanoramicAllDaySupport
import com.op.aod.enhance.hook.SingleClickBlockHook.hookSingleClickWakeUpBlock
import io.github.libxposed.api.XposedModule

object MainHook {
    @Volatile private var cachedContext: Context? = null
    val hostAppContext: Context? get()=cachedContext?:fetchContext()?.also{bindHostContext(it,"reflection-fallback")}

    fun install(module:XposedModule,classLoader:ClassLoader,hostPackage:String){
        val runtime=HookRuntime(module,classLoader)
        AodConfigReader.bindPrefs(module.getRemotePreferences(AodConfigStore.PREFS_NAME),hostPackage)
        installContextCapture(runtime)
        when(hostPackage){
            SYSTEM_UI->{
                runtime.hookWithLog("Brightness.Init"){hookInitBrightnessFix()}
                runtime.hookWithLog("Brightness.Running"){hookRunningBrightnessBoost()}
                runtime.hookWithLog("Duration.Limit"){hookAodDurationLimit()}
                runtime.hookWithLog("Panoramic.AllDay"){hookPanoramicAllDaySupport()}
                runtime.hookWithLog("SingleClick.Block"){hookSingleClickWakeUpBlock()}
                runtime.hookWithLog("LowLight.Block"){hookLowLightAodHide()}
            }
            OPLUS_AOD->runtime.hookWithLog("AodSettings.AllDay"){hookAodAllDaySupportSettings()}
        }
    }

    private fun installContextCapture(runtime:HookRuntime){
        runCatching {
            val method=Instrumentation::class.java.getDeclaredMethod("callApplicationOnCreate",Application::class.java)
            runtime.intercept("aod.context.application-on-create",method){chain->
                val app=chain.getArg(0) as? Application
                if(app!=null)bindHostContext(app,"Instrumentation.callApplicationOnCreate")
                chain.proceed()
            }
        }.onFailure{AodLog.w("HOST_CONTEXT","context capture hook unavailable",it)}
    }

    private fun bindHostContext(context:Context,source:String){
        val app=context.applicationContext?:context
        val first=cachedContext==null
        cachedContext=app
        if(first)AodLog.i("HOST_CONTEXT","bound source=$source package=${app.packageName}")
    }

    private fun fetchContext():Context?=runCatching{
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context
    }.getOrNull()

    private inline fun HookRuntime.hookWithLog(name:String,block:HookRuntime.()->Unit){
        AodLog.i("HOOK_REGISTER","start name=$name")
        runCatching{block()}.onSuccess{AodLog.i("HOOK_REGISTER","success name=$name")}.onFailure{AodLog.e("HOOK_REGISTER","failed name=$name",it)}
    }

    private const val SYSTEM_UI="com.android.systemui"
    private const val OPLUS_AOD="com.oplus.aod"
}
