package com.op.aod.enhance.hook

internal object LowLightHideHook {
    private const val AOD_UPDATE_MANAGER="com.oplus.systemui.aod.aodclock.off.AodUpdateManager"
    private const val SENSOR_CALLBACK="com.oplus.systemui.aod.aodclock.off.AodUpdateManager\$2"

    fun HookRuntime.hookLowLightAodHide(){hookNeedDisplay();hookSetHide();hookHideByDarkLight()}

    private fun HookRuntime.hookNeedDisplay(){
        runCatching{
            val method=findMethod(AOD_UPDATE_MANAGER,"needDisplayAodInSpecialRule")
            intercept("aod.lowlight.need-display",method){chain->val original=chain.proceed();if(AodConfigReader.read(MainHook.hostAppContext).blockLowLightHide)true else original}
        }.onFailure{AodLog.e("HOOK_REGISTER_DETAIL","LowLight needDisplay failed",it)}
    }

    private fun HookRuntime.hookSetHide(){
        runCatching{
            val method=findMethod(AOD_UPDATE_MANAGER,"setIsHideBySpecialRule",Boolean::class.javaPrimitiveType!!)
            intercept("aod.lowlight.set-hide",method){chain->
                val original=chain.getArg(0) as? Boolean ?: return@intercept chain.proceed()
                if(!AodConfigReader.read(MainHook.hostAppContext).blockLowLightHide||!original)return@intercept chain.proceed()
                val args=chain.getArgs().toTypedArray();args[0]=false;chain.proceed(args)
            }
        }.onFailure{AodLog.e("HOOK_REGISTER_DETAIL","LowLight setIsHide failed",it)}
    }

    private fun HookRuntime.hookHideByDarkLight(){
        runCatching{
            val method=findMethod(SENSOR_CALLBACK,"hideAodByDarkLight")
            intercept("aod.lowlight.hide-dark",method){chain->if(AodConfigReader.read(MainHook.hostAppContext).blockLowLightHide)null else chain.proceed()}
        }.onFailure{AodLog.w("HOOK_REGISTER_DETAIL","LowLight hideAodByDarkLight unavailable",it)}
    }
}
