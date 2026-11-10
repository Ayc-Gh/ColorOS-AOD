package com.op.aod.enhance.hook

import com.op.aod.enhance.data.AodConfigContract
import com.op.aod.enhance.data.AodValueSanitizer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

internal object BrightnessHook {
    private val pendingInitBrightness = AtomicReference<Int?>(null)

    fun HookRuntime.hookInitBrightnessFix() {
        val method = findMethod(OPLUS_DOZE_SERVICE_EX_IMPL, "setBrightnessBeforeDozing")
        intercept("aod.brightness.init", method) { chain ->
            pendingInitBrightness.set(null)
            val original = chain.proceed() as? Int ?: return@intercept null
            if (original < 0) return@intercept original
            val cfg = AodConfigReader.read(MainHook.hostAppContext)
            val isDark = original < 40
            val useSystem = if (isDark) cfg.useSystemInitDark else cfg.useSystemInitBright
            val target = if (useSystem) original else AodValueSanitizer.sanitizeBrightness(if (isDark) cfg.initDark else cfg.initBright)
            pendingInitBrightness.set(target)
            target
        }
    }

    fun HookRuntime.hookRunningBrightnessBoost() {
        val candidates = listOf(DOZE_SERVICE to "setDozeScreenBrightness", OPLUS_DOZE_SERVICE_EX_IMPL to "setDozeScreenBrightness",
            OPLUS_DOZE_SERVICE_EX_IMPL to "setBrightnessForFallbackStrategy", OPLUS_DOZE_SERVICE_EX_IMPL to "setBrightness4FallbackStrategy")
        for ((className, name) in candidates) {
                val method = runCatching { BrightnessMethodResolver.find(findClass(className), name) }.getOrNull() ?: continue
                method.isAccessible = true
                val type = method.parameterTypes.single()
                // Match the OEM's actual int-to-float mapping, rather than assuming /255.
                val converter = if (type == Float::class.javaPrimitiveType) runCatching {
                    findMethod("com.android.internal.display.BrightnessSynchronizer", "brightnessIntToFloat", Int::class.javaPrimitiveType!!)
                }.getOrElse { throw IllegalStateException("Float brightness converter unavailable", it) } else null
                intercept("aod.brightness.running", method) { chain ->
                    val original = chain.getArg(0) as? Number ?: return@intercept chain.proceed()
                    val pending = pendingInitBrightness.getAndSet(null)
                    val isInitial = pending != null && if (converter == null) original.toInt() == pending
                        else abs(original.toFloat() - (converter.invoke(null, pending) as Float)) < 0.00001f
                    val cfg = AodConfigReader.read(MainHook.hostAppContext)
                    val multiplier = AodValueSanitizer.sanitizeRunningMultiplier(cfg.runningMultiplier, AodConfigContract.DEFAULT_RUNNING_MULTIPLIER)
                    val target: Number = if (isInitial || cfg.useSystemRunningMultiplier) original
                        else if (converter != null) BrightnessPolicy.normalized(original.toFloat(), multiplier)
                        else BrightnessPolicy.integer(original.toInt(), multiplier)
                    HostDiagnostics.brightness = HostDiagnostics.BrightnessSample(original.toFloat(), target.toFloat(), converter != null, isInitial, cfg.useSystemRunningMultiplier)
                    if (target == original) return@intercept chain.proceed()
                    val args = chain.getArgs().toTypedArray(); args[0] = target
                    chain.proceed(args)
                }
                AodLog.i("BRIGHTNESS_HOOK", "registered ${method.declaringClass.name}#$name(${type.simpleName})")
                // One effective entry avoids multiplying twice through nested fallbacks.
                return
        }
        error("No supported int/float running brightness entry")
    }

    private const val OPLUS_DOZE_SERVICE_EX_IMPL = "com.oplus.systemui.aod.OplusDozeServiceExImpl"
    private const val DOZE_SERVICE = "com.android.systemui.doze.DozeService"
}
