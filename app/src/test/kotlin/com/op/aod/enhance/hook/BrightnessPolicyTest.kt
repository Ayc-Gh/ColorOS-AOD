package com.op.aod.enhance.hook

import org.junit.Assert.*
import org.junit.Test

class BrightnessPolicyTest {
    @Test fun normalizedKeepsSmallValuesAndFractionalPrecision() {
        assertEquals(0.0128f, BrightnessPolicy.normalized(0.008f, 1.6f), 0.000001f)
        assertEquals(0.3125f, BrightnessPolicy.normalized(0.25f, 1.25f), 0.000001f)
    }
    @Test fun normalizedClampsAndPreservesSentinelsAndUnknownScales() {
        assertEquals(1f, BrightnessPolicy.normalized(0.8f, 2f), 0f)
        assertEquals(-1f, BrightnessPolicy.normalized(-1f, 2f), 0f)
        assertTrue(BrightnessPolicy.normalized(Float.NaN, 2f).isNaN())
        assertEquals(Float.POSITIVE_INFINITY, BrightnessPolicy.normalized(Float.POSITIVE_INFINITY, 2f), 0f)
        assertEquals(1870f, BrightnessPolicy.normalized(1870f, 2f), 0f)
    }
    @Test fun legacyIntegerValuesRoundAndClamp() {
        assertEquals(128, BrightnessPolicy.integer(80, 1.6f))
        assertEquals(255, BrightnessPolicy.integer(200, 2f))
        assertEquals(-1, BrightnessPolicy.integer(-1, 2f))
        assertEquals(8191, BrightnessPolicy.integer(8191, 2f))
    }
}
