package com.op.aod.enhance.hook

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.toClass

/** 保留原有三重拦截设计，并将注册/触发结果写入 Debug logcat。 */
internal object LowLightHideHook {

    private const val AOD_UPDATE_MANAGER =
        "com.oplus.systemui.aod.aodclock.off.AodUpdateManager"
    private const val SENSOR_CALLBACK =
        "com.oplus.systemui.aod.aodclock.off.AodUpdateManager\$2"

    fun YukiBaseHooker.hookLowLightAodHide() {
        hookNeedDisplayAodInSpecialRule()
        hookSetIsHideBySpecialRule()
        hookHideAodByDarkLight()
    }

    private fun YukiBaseHooker.hookNeedDisplayAodInSpecialRule() {
        runCatching {
            AOD_UPDATE_MANAGER
                .toClass(appClassLoader).resolve()
                .firstMethod { name = "needDisplayAodInSpecialRule" }
                .hook {
                    after {
                        val cfg = AodConfigReader.read(MainHook.hostAppContext)
                        if (cfg.blockLowLightHide) {
                            result = true
                            AodLog.d(
                                "LOW_LIGHT_HOOK",
                                "needDisplayAodInSpecialRule forced=true blockLowLightHide=true",
                            )
                        } else {
                            AodLog.d(
                                "LOW_LIGHT_HOOK",
                                "needDisplayAodInSpecialRule pass-through blockLowLightHide=false",
                            )
                        }
                    }
                }
        }.onSuccess {
            AodLog.i("HOOK_REGISTER_DETAIL", "LowLight needDisplayAodInSpecialRule registered")
        }.onFailure {
            AodLog.e("HOOK_REGISTER_DETAIL", "LowLight needDisplayAodInSpecialRule failed", it)
        }
    }

    private fun YukiBaseHooker.hookSetIsHideBySpecialRule() {
        runCatching {
            AOD_UPDATE_MANAGER
                .toClass(appClassLoader).resolve()
                .firstMethod {
                    name = "setIsHideBySpecialRule"
                    parameters(Boolean::class)
                }
                .hook {
                    before {
                        val cfg = AodConfigReader.read(MainHook.hostAppContext)
                        val originalValue = args(0).any() as? Boolean
                        if (originalValue == null) {
                            AodLog.w("LOW_LIGHT_HOOK", "setIsHideBySpecialRule arg0 not Boolean")
                            return@before
                        }
                        if (cfg.blockLowLightHide && originalValue) {
                            args(0).set(false)
                            AodLog.d(
                                "LOW_LIGHT_HOOK",
                                "setIsHideBySpecialRule original=true forced=false",
                            )
                        } else {
                            AodLog.d(
                                "LOW_LIGHT_HOOK",
                                "setIsHideBySpecialRule pass-through value=$originalValue block=${cfg.blockLowLightHide}",
                            )
                        }
                    }
                }
        }.onSuccess {
            AodLog.i("HOOK_REGISTER_DETAIL", "LowLight setIsHideBySpecialRule registered")
        }.onFailure {
            AodLog.e("HOOK_REGISTER_DETAIL", "LowLight setIsHideBySpecialRule failed", it)
        }
    }

    private fun YukiBaseHooker.hookHideAodByDarkLight() {
        runCatching {
            SENSOR_CALLBACK
                .toClass(appClassLoader).resolve()
                .firstMethod { name = "hideAodByDarkLight" }
                .hook {
                    before {
                        val cfg = AodConfigReader.read(MainHook.hostAppContext)
                        val reason = args(0).any()
                        if (cfg.blockLowLightHide) {
                            result = null
                            AodLog.d(
                                "LOW_LIGHT_HOOK",
                                "hideAodByDarkLight blocked reason=$reason",
                            )
                        } else {
                            AodLog.d(
                                "LOW_LIGHT_HOOK",
                                "hideAodByDarkLight pass-through reason=$reason",
                            )
                        }
                    }
                }
        }.onSuccess {
            AodLog.i("HOOK_REGISTER_DETAIL", "LowLight hideAodByDarkLight registered")
        }.onFailure {
            AodLog.w(
                "HOOK_REGISTER_DETAIL",
                "LowLight hideAodByDarkLight unavailable (non-critical)",
                it,
            )
        }
    }
}
