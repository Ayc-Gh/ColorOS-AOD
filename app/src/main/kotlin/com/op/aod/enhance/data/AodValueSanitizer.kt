package com.op.aod.enhance.data

/** Validates persisted settings shared by the manager and SystemUI hooks. */
internal object AodValueSanitizer {

    const val MIN_BRIGHTNESS = 0
    const val MAX_BRIGHTNESS = 255
    const val MIN_RUNNING_MULTIPLIER = 1.0f
    const val MAX_RUNNING_MULTIPLIER = 2.0f

    fun sanitizeBrightness(value: Int): Int = value.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)

    fun sanitizeRunningMultiplier(value: Float, fallback: Float): Float {
        val safeFallback = if (fallback.isFinite()) {
            fallback.coerceIn(MIN_RUNNING_MULTIPLIER, MAX_RUNNING_MULTIPLIER)
        } else {
            MIN_RUNNING_MULTIPLIER
        }
        return if (value.isFinite()) {
            value.coerceIn(MIN_RUNNING_MULTIPLIER, MAX_RUNNING_MULTIPLIER)
        } else {
            safeFallback
        }
    }
}
