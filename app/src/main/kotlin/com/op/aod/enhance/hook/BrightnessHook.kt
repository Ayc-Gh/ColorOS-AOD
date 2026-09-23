package com.op.aod.enhance.hook

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.toClass
import com.op.aod.enhance.data.AodConfigContract
import com.op.aod.enhance.data.AodValueSanitizer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/** AOD 亮度修正 Hook；Debug 版记录每一次初始/运行时亮度输入与最终动作。 */
internal object BrightnessHook {

    private val pendingInitBrightness = AtomicReference<Int?>(null)

    fun YukiBaseHooker.hookInitBrightnessFix() {
        OPLUS_DOZE_SERVICE_EX_IMPL
            .toClass(appClassLoader)
            .resolve()
            .firstMethod {
                name = "setBrightnessBeforeDozing"
                emptyParameters()
            }.hook {
                after {
                    val originalResult = result<Int>()
                    if (originalResult == null) {
                        AodLog.w("BRIGHTNESS_INIT", "result=null")
                        return@after
                    }
                    if (originalResult == -1) {
                        AodLog.d("BRIGHTNESS_INIT", "original=-1 sentinel pass-through")
                        return@after
                    }

                    val cfg = AodConfigReader.read(MainHook.hostAppContext)
                    val isDark = originalResult < INIT_DARK_THRESHOLD
                    val useSystem = if (isDark) cfg.useSystemInitDark else cfg.useSystemInitBright
                    val target = if (useSystem) {
                        originalResult
                    } else {
                        val configured = if (isDark) cfg.initDark else cfg.initBright
                        AodValueSanitizer.sanitizeBrightness(configured)
                    }

                    result = target
                    pendingInitBrightness.set(target)
                    AodLog.d(
                        "BRIGHTNESS_INIT",
                        "env=${if (isDark) "dark" else "bright"} mode=${if (useSystem) "system" else "custom"} " +
                            "original=$originalResult target=$target pending=true",
                    )
                }
            }
    }

    fun YukiBaseHooker.hookRunningBrightnessBoost() {
        val mainResult = runCatching { hookDozeServiceBrightnessBoost() }
        if (mainResult.isSuccess) {
            AodLog.i("BRIGHTNESS_HOOK", "main DozeService path registered")
            return
        }

        AodLog.w(
            "BRIGHTNESS_HOOK",
            "main DozeService path failed; registering Oplus fallbacks",
            mainResult.exceptionOrNull(),
        )

        runCatching { hookOplusDozeServiceBrightnessBoost() }
            .onSuccess { AodLog.i("BRIGHTNESS_HOOK", "Oplus setDozeScreenBrightness fallback registered") }
            .onFailure { AodLog.e("BRIGHTNESS_HOOK", "Oplus setDozeScreenBrightness fallback failed", it) }
        hookFallbackBrightnessBoost()
    }

    private fun YukiBaseHooker.hookDozeServiceBrightnessBoost() {
        DOZE_SERVICE
            .toClass(appClassLoader)
            .resolve()
            .firstMethod {
                name = "setDozeScreenBrightness"
                parameters(Int::class)
            }.hook {
                before {
                    val original = args(0).any() as? Int
                    if (original == null) {
                        AodLog.w("BRIGHTNESS_RUNNING", "path=DozeService arg0 is not Int")
                        return@before
                    }
                    if (consumePendingInit(original)) {
                        AodLog.d("BRIGHTNESS_RUNNING", "path=DozeService original=$original action=skip-init")
                        return@before
                    }
                    applyBoostToArg("DozeService", original) { args(0).set(it) }
                }
            }
    }

    private fun YukiBaseHooker.hookOplusDozeServiceBrightnessBoost() {
        OPLUS_DOZE_SERVICE_EX_IMPL
            .toClass(appClassLoader)
            .resolve()
            .firstMethod {
                name = "setDozeScreenBrightness"
                parameters(Int::class)
            }.hook {
                before {
                    val original = args(0).any() as? Int
                    if (original == null) {
                        AodLog.w("BRIGHTNESS_RUNNING", "path=OplusDozeService arg0 is not Int")
                        return@before
                    }
                    if (consumePendingInit(original)) {
                        AodLog.d("BRIGHTNESS_RUNNING", "path=OplusDozeService original=$original action=skip-init")
                        return@before
                    }
                    applyBoostToArg("OplusDozeService", original) { args(0).set(it) }
                }
            }
    }

    private fun YukiBaseHooker.hookFallbackBrightnessBoost() {
        for (methodName in FALLBACK_METHOD_NAMES) {
            val result = runCatching {
                OPLUS_DOZE_SERVICE_EX_IMPL
                    .toClass(appClassLoader)
                    .resolve()
                    .firstMethod {
                        name = methodName
                        parameters(Int::class)
                    }.hook {
                        before {
                            val original = args(0).any() as? Int
                            if (original == null) {
                                AodLog.w("BRIGHTNESS_RUNNING", "path=$methodName arg0 is not Int")
                                return@before
                            }
                            applyBoostToArg(methodName, original) { args(0).set(it) }
                        }
                    }
            }
            if (result.isSuccess) {
                AodLog.i("BRIGHTNESS_HOOK", "fallback method=$methodName registered")
                return
            }
            AodLog.w("BRIGHTNESS_HOOK", "fallback method=$methodName unavailable", result.exceptionOrNull())
        }
        AodLog.e("BRIGHTNESS_HOOK", "no fallback brightness method registered")
    }

    private fun applyBoostToArg(path: String, original: Int, setter: (Int) -> Unit) {
        if (original < 0) {
            AodLog.d("BRIGHTNESS_RUNNING", "path=$path original=$original action=sentinel-pass-through")
            return
        }

        val cfg = AodConfigReader.read(MainHook.hostAppContext)
        if (cfg.useSystemRunningMultiplier) {
            AodLog.d("BRIGHTNESS_RUNNING", "path=$path original=$original mode=system target=$original")
            return
        }

        val multiplier = AodValueSanitizer.sanitizeRunningMultiplier(
            cfg.runningMultiplier,
            AodConfigContract.DEFAULT_RUNNING_MULTIPLIER,
        )
        if (multiplier == 1.0f) {
            AodLog.d("BRIGHTNESS_RUNNING", "path=$path original=$original mode=custom multiplier=1.0 target=$original")
            return
        }

        val boosted = (original * multiplier).roundToInt()
        val clamped = AodValueSanitizer.sanitizeBrightness(boosted)
        setter(clamped)
        AodLog.d(
            "BRIGHTNESS_RUNNING",
            "path=$path original=$original mode=custom multiplier=$multiplier boosted=$boosted target=$clamped",
        )
    }

    private fun consumePendingInit(original: Int): Boolean {
        val pending = pendingInitBrightness.getAndSet(null) ?: return false
        val matched = original == pending
        if (!matched) {
            AodLog.d("BRIGHTNESS_PENDING", "consumed pending=$pending by next write original=$original matched=false")
        }
        return matched
    }

    private const val OPLUS_DOZE_SERVICE_EX_IMPL = "com.oplus.systemui.aod.OplusDozeServiceExImpl"
    private const val DOZE_SERVICE = "com.android.systemui.doze.DozeService"
    private const val INIT_DARK_THRESHOLD = 40

    private val FALLBACK_METHOD_NAMES = arrayOf(
        "setBrightnessForFallbackStrategy",
        "setBrightness4FallbackStrategy",
    )
}
