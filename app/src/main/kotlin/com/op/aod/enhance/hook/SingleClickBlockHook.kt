package com.op.aod.enhance.hook

import android.os.SystemClock

internal object SingleClickBlockHook {
    private const val DOUBLE_CLICK_LISTENER="com.oplus.systemui.keyguard.gesture.OplusDoubleClickSleep\$OnDoubleClickListener"

    fun HookRuntime.hookSingleClickWakeUpBlock(){
        val targets=arrayOf(
            "com.oplus.systemui.aod.scene.AodViewSingleClickWakeUpHolder\$AodSingleClickWakeUpCallback" to "NormalAod",
            "com.oplus.systemui.aod.scene.PanoramicAodSingleClickWakeUpController\$PanoramicAodSingleClickWakeUpCallback" to "PanoramicAod",
            "com.oplus.systemui.aod.display.OplusWakeUpController\$AodSingleClickWakeUpCallback" to "WakeUpController",
        )
        for((cls,label) in targets)registerClickHook(cls,label)
        runCatching{
            val method=findMethod(DOUBLE_CLICK_LISTENER,"onSingleTapConfirmed")
            intercept("aod.single-click.confirmed",method){chain->if(AodConfigReader.read(MainHook.hostAppContext).blockSingleClick)false else chain.proceed()}
        }.onFailure{AodLog.e("HOOK_REGISTER_DETAIL","SingleClick confirmed unavailable",it)}
    }

    private fun HookRuntime.registerClickHook(targetClass:String,label:String){
        val gate=DoubleTapGate()
        runCatching{
            val method=findMethod(targetClass,"onClick")
            intercept("aod.single-click.$label",method){chain->
                if(!AodConfigReader.read(MainHook.hostAppContext).blockSingleClick){gate.reset();return@intercept chain.proceed()}
                if(gate.shouldAllow(SystemClock.elapsedRealtime()))chain.proceed() else null
            }
        }.onFailure{AodLog.e("HOOK_REGISTER_DETAIL","SingleClick $label unavailable",it)}
    }
}
