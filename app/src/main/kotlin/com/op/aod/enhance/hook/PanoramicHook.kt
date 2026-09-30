package com.op.aod.enhance.hook

internal object PanoramicHook {
    private val FIELD_NAMES=listOf("isSupportPanoramicAllDay","isSupportPanoramicAllDayByPanelFeature","isSupportPanoramicByPanelFeature","isSupportPanoramic")
    private const val SMOOTH_TRANSITION_CONTROLLER="com.oplus.systemui.aod.display.SmoothTransitionController"

    fun HookRuntime.hookPanoramicAllDaySupport(){
        val clazz=runCatching{findClass(SMOOTH_TRANSITION_CONTROLLER)}.getOrElse{AodLog.e("HOOK_REGISTER_DETAIL","Panoramic controller resolve failed",it);return}
        fun apply(instance:Any){
            if(!AodConfigReader.read(MainHook.hostAppContext).enablePanoramic)return
            var changed=0
            for(name in FIELD_NAMES)runCatching{instance.javaClass.getDeclaredField(name).apply{isAccessible=true}.setBoolean(instance,true);changed++}
            AodLog.d("PANORAMIC_HOOK","applied changedFields=$changed class=${instance.javaClass.name}")
        }
        for(name in arrayOf("initSmoothTransitionState","setPanoramicSupportedByRemote")){
            runCatching{
                val method=findMethod(clazz,name)
                intercept("aod.panoramic.$name",method){chain->val result=chain.proceed();chain.getThisObject()?.let(::apply);result}
            }.onSuccess{AodLog.i("HOOK_REGISTER_DETAIL","Panoramic $name registered")}.onFailure{AodLog.e("HOOK_REGISTER_DETAIL","Panoramic $name unavailable",it)}
        }
    }
}
