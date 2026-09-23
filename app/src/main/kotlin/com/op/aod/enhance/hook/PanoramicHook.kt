package com.op.aod.enhance.hook

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.toClass

internal object PanoramicHook {

    private val FIELD_NAMES = listOf(
        "isSupportPanoramicAllDay",
        "isSupportPanoramicAllDayByPanelFeature",
        "isSupportPanoramicByPanelFeature",
        "isSupportPanoramic",
    )
    private const val SMOOTH_TRANSITION_CONTROLLER =
        "com.oplus.systemui.aod.display.SmoothTransitionController"

    fun YukiBaseHooker.hookPanoramicAllDaySupport() {
        val resolveResult = runCatching {
            SMOOTH_TRANSITION_CONTROLLER.toClass(appClassLoader).resolve()
        }
        val clazz = resolveResult.getOrElse {
            AodLog.e("HOOK_REGISTER_DETAIL", "Panoramic controller resolve failed", it)
            return
        }

        fun applyPanoramicSupport(instance: Any) {
            val cfg = AodConfigReader.read(MainHook.hostAppContext)
            if (!cfg.enablePanoramic) {
                AodLog.d("PANORAMIC_HOOK", "pass-through enablePanoramic=false")
                return
            }

            val realClass = instance::class.java
            var changed = 0
            for (name in FIELD_NAMES) {
                runCatching {
                    val field = realClass.getDeclaredField(name)
                    field.isAccessible = true
                    field.setBoolean(instance, true)
                    changed++
                }.onFailure {
                    AodLog.d("PANORAMIC_HOOK", "field=$name unavailable: ${it.message}")
                }
            }
            AodLog.d("PANORAMIC_HOOK", "applied changedFields=$changed class=${realClass.name}")
        }

        val initResult = runCatching {
            clazz.firstMethod { name = "initSmoothTransitionState" }
        }
        initResult.getOrNull()?.hook {
            after { applyPanoramicSupport(instance<Any>()) }
        }
        if (initResult.isSuccess) {
            AodLog.i("HOOK_REGISTER_DETAIL", "Panoramic initSmoothTransitionState registered")
        } else {
            AodLog.e("HOOK_REGISTER_DETAIL", "Panoramic initSmoothTransitionState unavailable", initResult.exceptionOrNull())
        }

        val remoteResult = runCatching {
            clazz.firstMethod { name = "setPanoramicSupportedByRemote" }
        }
        remoteResult.getOrNull()?.hook {
            after { applyPanoramicSupport(instance<Any>()) }
        }
        if (remoteResult.isSuccess) {
            AodLog.i("HOOK_REGISTER_DETAIL", "Panoramic setPanoramicSupportedByRemote registered")
        } else {
            AodLog.e("HOOK_REGISTER_DETAIL", "Panoramic setPanoramicSupportedByRemote unavailable", remoteResult.exceptionOrNull())
        }
    }
}
