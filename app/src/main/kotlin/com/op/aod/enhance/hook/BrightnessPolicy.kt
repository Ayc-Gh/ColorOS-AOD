package com.op.aod.enhance.hook

import kotlin.math.roundToInt

/** Integer APIs use 0..255; Android 17 float APIs use normalized 0..1. */
internal object BrightnessPolicy {
    fun integer(original: Int, multiplier: Float): Int =
        if (original < 0 || original > 255) original else (original * multiplier).roundToInt().coerceIn(0, 255)
    fun normalized(original: Float, multiplier: Float): Float =
        if (!original.isFinite() || original < 0f || original > 1f) original
        else (original * multiplier).coerceIn(0f, 1f)
}
