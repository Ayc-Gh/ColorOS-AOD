package com.op.aod.enhance.hook

import android.view.Display
import com.op.aod.enhance.data.AodConfigContract

/** Pure policy for the ColorOS panoramic-AOD native timeout. */
internal object AodDurationPolicy {
    const val NATIVE_TIMEOUT_REASON = "Panoramic-Aod-Show"

    fun shouldArmDeadline(state: Int, reason: String?, targetMs: Long?): Boolean =
        state == Display.STATE_DOZE && reason == NATIVE_TIMEOUT_REASON && targetMs != null

    fun shouldDeferEnergySavingHide(active: Boolean, mode: Int, elapsedMs: Long, targetMs: Long?): Boolean {
        if (!active || elapsedMs < 0 || mode == AodConfigContract.DURATION_MODE_SYSTEM) return false
        return mode == AodConfigContract.DURATION_MODE_ALWAYS || (targetMs != null && elapsedMs < targetMs)
    }

    fun shouldDeferClockHide(reason: Int?, active: Boolean, mode: Int, elapsedMs: Long, targetMs: Long?): Boolean =
        (reason == 3 || reason == 12) && shouldDeferEnergySavingHide(active, mode, elapsedMs, targetMs)

    fun targetDurationMs(mode: Int, customMinutes: Int): Long? = when (mode) {
        AodConfigContract.DURATION_MODE_SYSTEM,
        AodConfigContract.DURATION_MODE_ALWAYS -> null
        AodConfigContract.DURATION_MODE_30_SECONDS -> 30_000L
        AodConfigContract.DURATION_MODE_1_MINUTE -> 60_000L
        AodConfigContract.DURATION_MODE_5_MINUTES -> 300_000L
        AodConfigContract.DURATION_MODE_10_MINUTES -> 600_000L
        AodConfigContract.DURATION_MODE_30_MINUTES -> 1_800_000L
        AodConfigContract.DURATION_MODE_60_MINUTES -> 3_600_000L
        AodConfigContract.DURATION_MODE_CUSTOM -> customMinutes
            .coerceIn(
                AodConfigContract.MIN_AOD_DURATION_CUSTOM_MINUTES,
                AodConfigContract.MAX_AOD_DURATION_CUSTOM_MINUTES,
            ).toLong() * 60_000L
        else -> null
    }

    fun shouldInterceptNativeTimeout(
        state: Int,
        reason: String?,
        mode: Int,
        elapsedMs: Long,
        targetMs: Long?,
        visible: Boolean,
    ): Boolean {
        if (!visible) return false
        if (state != Display.STATE_OFF) return false
        if (reason != NATIVE_TIMEOUT_REASON) return false
        if (mode == AodConfigContract.DURATION_MODE_SYSTEM) return false
        if (mode == AodConfigContract.DURATION_MODE_ALWAYS) return true
        return targetMs != null && elapsedMs in 0 until targetMs
    }
}
