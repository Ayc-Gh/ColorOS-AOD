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
        var callbacks = 0
        for((cls,label) in targets) {
            val resolution = runCatching { findClass(cls) }.exceptionOrNull()
            if (resolution is ClassNotFoundException) {
                skipped("$label: 当前系统未提供此版本入口")
                AodLog.i("HOOK_REGISTER_DETAIL", "SingleClick $label skipped: class absent on this system")
            } else if (resolution != null) {
                failure("$label: 入口解析失败")
                AodLog.e("HOOK_REGISTER_DETAIL", "SingleClick $label class resolution failed", resolution)
            } else if (registerClickHook(cls,label)) callbacks++
        }
        if (callbacks == 0) failure("没有可用的黑屏单击回调，需适配此系统")
        runCatching{
            val method=findMethod(DOUBLE_CLICK_LISTENER,"onSingleTapConfirmed")
            intercept("aod.single-click.confirmed",method){chain->if(AodConfigReader.read(MainHook.hostAppContext).blockSingleClick)false else chain.proceed()}
        }.onFailure{failure("单击确认入口不可用");AodLog.e("HOOK_REGISTER_DETAIL","SingleClick confirmed unavailable",it)}
    }

    private fun HookRuntime.registerClickHook(targetClass:String,label:String): Boolean {
        val gate=DoubleTapGate()
        return runCatching{
            val method=findMethod(targetClass,"onClick")
            intercept("aod.single-click.$label",method){chain->
                if(!AodConfigReader.read(MainHook.hostAppContext).blockSingleClick){gate.reset();return@intercept chain.proceed()}
                if(gate.shouldAllow(SystemClock.elapsedRealtime()))chain.proceed() else null
            }
        }.onFailure{failure("$label: ${it.javaClass.simpleName}");AodLog.e("HOOK_REGISTER_DETAIL","SingleClick $label unavailable",it)}.isSuccess
    }
}
