package com.op.aod.enhance.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AodValueSanitizerTest {

    @Test
    fun brightnessIsClamped() {
        assertEquals(0, AodValueSanitizer.sanitizeBrightness(-1))
        assertEquals(128, AodValueSanitizer.sanitizeBrightness(128))
        assertEquals(255, AodValueSanitizer.sanitizeBrightness(300))
    }

    @Test
    fun multiplierRejectsNonFiniteValues() {
        assertEquals(1.6f, AodValueSanitizer.sanitizeRunningMultiplier(Float.NaN, 1.6f))
        assertEquals(1.6f, AodValueSanitizer.sanitizeRunningMultiplier(Float.POSITIVE_INFINITY, 1.6f))
        assertEquals(1.6f, AodValueSanitizer.sanitizeRunningMultiplier(Float.NEGATIVE_INFINITY, 1.6f))
    }

    @Test
    fun multiplierIsClamped() {
        assertEquals(1.0f, AodValueSanitizer.sanitizeRunningMultiplier(0.5f, 1.6f))
        assertEquals(1.4f, AodValueSanitizer.sanitizeRunningMultiplier(1.4f, 1.6f))
        assertEquals(2.0f, AodValueSanitizer.sanitizeRunningMultiplier(5.0f, 1.6f))
    }
}
