package com.op.aod.enhance.data

import org.junit.Assert.*
import org.junit.Test

class AodInputValidationTest {
    @Test fun durationRejectsOutOfRangeInsteadOfSilentlyClamping() {
        for (draft in listOf("0", "9999", "-1", "1441", "999999999999999999999")) {
            assertNull(draft, AodInputValidation.durationMinutes(draft))
        }
        assertEquals(1, AodInputValidation.durationMinutes("1"))
        assertEquals(1440, AodInputValidation.durationMinutes("1440"))
    }

    @Test fun emptyDraftsNeverReplaceSavedValues() {
        for (draft in listOf("", " ", "-", ".")) {
            assertNull(AodInputValidation.durationMinutes(draft))
            assertNull(AodInputValidation.brightness(draft))
            assertNull(AodInputValidation.multiplier(draft))
        }
    }

    @Test fun brightnessAcceptsEndpointsAndRejectsInvalidDrafts() {
        assertEquals(0, AodInputValidation.brightness("0"))
        assertEquals(255, AodInputValidation.brightness("255"))
        for (draft in listOf("-1", "256", "300", "1.5", "abc")) {
            assertNull(draft, AodInputValidation.brightness(draft))
        }
    }

    @Test fun decimalsAllowIntermediateEditingWithoutReformatting() {
        assertEquals(1f, AodInputValidation.multiplier("1.")!!, 0f)
        assertEquals(1.3f, AodInputValidation.multiplier("1.3")!!, 0f)
        assertEquals(1.32f, AodInputValidation.multiplier("1.32")!!, 0f)
        assertEquals(2f, AodInputValidation.multiplier("2.0")!!, 0f)
    }

    @Test fun multiplierRejectsNonFiniteAndOutOfRangeDrafts() {
        for (draft in listOf("NaN", "Infinity", "-Infinity", "1e99", "0.9", "2.1")) {
            assertNull(draft, AodInputValidation.multiplier(draft))
        }
    }
}
