package com.op.aod.enhance.hook

internal object PanoramicHook {
    private val FIELD_NAMES=listOf("isSupportPanoramicAllDay","isSupportPanoramicAllDayByPanelFeature","isSupportPanoramicByPanelFeature","isSupportPanoramic")
    private const val SMOOTH_TRANSITION_CONTROLLER="com.oplus.systemui.aod.display.SmoothTransitionController"

    fun HookRuntime.hookPanoramicAllDaySupport(){
        val clazz=runCatching{findClass(SMOOTH_TRANSITION_CONTROLLER)}.getOrElse{failure("全景控制器不可用");AodLog.e("HOOK_REGISTER_DETAIL","Panoramic controller resolve failed",it);return}
        // ColorOS publishes the capability to Settings during init, before our
        // after-hook changes the fields. Intercept that publication as well.
        runCatching {
            val publish = findMethod(clazz, "setPanoramicSupportAllDayForApplication", Boolean::class.javaPrimitiveType!!)
            intercept("aod.panoramic.publish-all-day", publish) { chain ->
                if (!AodConfigReader.read(MainHook.hostAppContext).enablePanoramic) {
                    return@intercept chain.proceed()
                }
                val args = chain.getArgs().toTypedArray()
                args[0] = true
                AodLog.i("PANORAMIC_PUBLISH", "allDay=true original=${chain.getArg(0)}")
                chain.proceed(args)
            }
        }.onFailure { failure("全天能力发布入口不可用"); AodLog.e("HOOK_REGISTER_DETAIL", "Panoramic capability publication unavailable", it) }
        fun apply(instance:Any){
            if(!AodConfigReader.read(MainHook.hostAppContext).enablePanoramic)return
            var changed=0
            for(name in FIELD_NAMES)runCatching{instance.javaClass.getDeclaredField(name).apply{isAccessible=true}.setBoolean(instance,true);changed++}
            if (changed == 0) HostDiagnostics.registry.failed("Panoramic.AllDay", "未找到适用的全景能力字段")
            AodLog.d("PANORAMIC_HOOK","applied changedFields=$changed class=${instance.javaClass.name}")
        }
        for(name in arrayOf("initSmoothTransitionState","setPanoramicSupportedByRemote")){
            runCatching{
                val method=findMethod(clazz,name)
                intercept("aod.panoramic.$name",method){chain->val result=chain.proceed();chain.getThisObject()?.let(::apply);result}
            }.onSuccess{AodLog.i("HOOK_REGISTER_DETAIL","Panoramic $name registered")}.onFailure{failure("$name 入口不可用");AodLog.e("HOOK_REGISTER_DETAIL","Panoramic $name unavailable",it)}
        }
    }
}
