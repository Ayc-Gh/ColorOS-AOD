package com.op.aod.enhance.hook

internal object AodSettingsHook {
    fun HookRuntime.hookAodAllDaySupportSettings() {
        val method=findMethod(SETTINGS_UTILS,"getKeyAodAllDaySupportSettings")
        intercept("aod.settings.all-day",method){chain->
            chain.proceed()
            val cfg=AodConfigReader.read(MainHook.hostAppContext)
            val resultValue=if(cfg.enableSettingsSupport)1 else 0
            AodLog.d("AOD_SETTINGS_HOOK","getKeyAodAllDaySupportSettings -> $resultValue enable=${cfg.enableSettingsSupport}")
            resultValue
        }
        AodLog.i("HOOK_REGISTER_DETAIL","AodSettings getKeyAodAllDaySupportSettings registered")
    }
    private const val SETTINGS_UTILS="com.oplus.aod.util.SettingsUtils"
}
