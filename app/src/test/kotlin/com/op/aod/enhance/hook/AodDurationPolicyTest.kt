package com.op.aod.enhance.hook

import android.view.Display
import com.op.aod.enhance.data.AodConfigContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AodDurationPolicyTest {
    @Test
    fun minuteQuotaHideIsDeferredButOtherHideReasonsPass() {
        for (reason in listOf(3, 12)) {
            assertTrue(AodDurationPolicy.shouldDeferClockHide(reason, true, AodConfigContract.DURATION_MODE_5_MINUTES, 60_000L, 300_000L))
            assertFalse(AodDurationPolicy.shouldDeferClockHide(reason, true, AodConfigContract.DURATION_MODE_5_MINUTES, 300_000L, 300_000L))
        }
        for (reason in listOf(0, 1, 4, 6, 11, 105, 107, null)) {
            assertFalse(AodDurationPolicy.shouldDeferClockHide(reason, true, AodConfigContract.DURATION_MODE_ALWAYS, 60_000L, null))
        }
    }
    @Test
    fun renderedUiHideIsDeferredUntilDeadline() {
        assertTrue(AodDurationPolicy.shouldDeferEnergySavingHide(true, AodConfigContract.DURATION_MODE_1_MINUTE, 7_500L, 60_000L))
        assertFalse(AodDurationPolicy.shouldDeferEnergySavingHide(true, AodConfigContract.DURATION_MODE_1_MINUTE, 60_000L, 60_000L))
        assertFalse(AodDurationPolicy.shouldDeferEnergySavingHide(true, AodConfigContract.DURATION_MODE_1_MINUTE, -1L, 60_000L))
    }

    @Test
    fun energySavingHidePassesOutsideActiveOverride() {
        assertFalse(AodDurationPolicy.shouldDeferEnergySavingHide(false, AodConfigContract.DURATION_MODE_ALWAYS, 7_500L, null))
        assertFalse(AodDurationPolicy.shouldDeferEnergySavingHide(true, AodConfigContract.DURATION_MODE_SYSTEM, 7_500L, null))
        assertTrue(AodDurationPolicy.shouldDeferEnergySavingHide(true, AodConfigContract.DURATION_MODE_ALWAYS, 86_400_000L, null))
    }
    @Test
    fun finiteDeadlineArmsOnPanoramicShowEvenWithoutNativeOff() {
        assertTrue(AodDurationPolicy.shouldArmDeadline(Display.STATE_DOZE, AodDurationPolicy.NATIVE_TIMEOUT_REASON, 30_000L))
        assertFalse(AodDurationPolicy.shouldArmDeadline(Display.STATE_OFF, AodDurationPolicy.NATIVE_TIMEOUT_REASON, 30_000L))
        assertFalse(AodDurationPolicy.shouldArmDeadline(Display.STATE_DOZE, "FP-FadeOut", 30_000L))
    }

    @Test
    fun systemAndAlwaysModesDoNotArmDeadline() {
        for (mode in listOf(AodConfigContract.DURATION_MODE_SYSTEM, AodConfigContract.DURATION_MODE_ALWAYS)) {
            val target = AodDurationPolicy.targetDurationMs(mode, 5)
            assertFalse(AodDurationPolicy.shouldArmDeadline(Display.STATE_DOZE, AodDurationPolicy.NATIVE_TIMEOUT_REASON, target))
        }
    }

    @Test
    fun customDurationIsBoundedBeforeConvertingToMilliseconds() {
        assertEquals(60_000L, AodDurationPolicy.targetDurationMs(AodConfigContract.DURATION_MODE_CUSTOM, -1))
        assertEquals(86_400_000L, AodDurationPolicy.targetDurationMs(AodConfigContract.DURATION_MODE_CUSTOM, Int.MAX_VALUE))
    }
    @Test
    fun systemModeNeverInterceptsNativeTimeout() {
        assertFalse(
            AodDurationPolicy.shouldInterceptNativeTimeout(
                visible = true,
                state = Display.STATE_OFF,
                reason = AodDurationPolicy.NATIVE_TIMEOUT_REASON,
                mode = AodConfigContract.DURATION_MODE_SYSTEM,
                elapsedMs = 7_500L,
                targetMs = null,
            ),
        )
    }

    @Test
    fun finiteModeInterceptsBeforeTarget() {
        assertTrue(
            AodDurationPolicy.shouldInterceptNativeTimeout(
                visible = true,
                state = Display.STATE_OFF,
                reason = AodDurationPolicy.NATIVE_TIMEOUT_REASON,
                mode = AodConfigContract.DURATION_MODE_1_MINUTE,
                elapsedMs = 7_500L,
                targetMs = 60_000L,
            ),
        )
    }

    @Test
    fun finiteModePassesAtTarget() {
        assertFalse(
            AodDurationPolicy.shouldInterceptNativeTimeout(
                visible = true,
                state = Display.STATE_OFF,
                reason = AodDurationPolicy.NATIVE_TIMEOUT_REASON,
                mode = AodConfigContract.DURATION_MODE_1_MINUTE,
                elapsedMs = 60_000L,
                targetMs = 60_000L,
            ),
        )
    }

    @Test
    fun alwaysModeInterceptsNativeTimeout() {
        assertTrue(
            AodDurationPolicy.shouldInterceptNativeTimeout(
                visible = true,
                state = Display.STATE_OFF,
                reason = AodDurationPolicy.NATIVE_TIMEOUT_REASON,
                mode = AodConfigContract.DURATION_MODE_ALWAYS,
                elapsedMs = 86_400_000L,
                targetMs = null,
            ),
        )
    }

    @Test
    fun unrelatedOffReasonAlwaysPasses() {
        assertFalse(
            AodDurationPolicy.shouldInterceptNativeTimeout(
                visible = true,
                state = Display.STATE_OFF,
                reason = "FP-FadeOut",
                mode = AodConfigContract.DURATION_MODE_ALWAYS,
                elapsedMs = 1_000L,
                targetMs = null,
            ),
        )
    }

    @Test
    fun nonOffStateAlwaysPasses() {
        assertFalse(
            AodDurationPolicy.shouldInterceptNativeTimeout(
                visible = true,
                state = Display.STATE_DOZE,
                reason = AodDurationPolicy.NATIVE_TIMEOUT_REASON,
                mode = AodConfigContract.DURATION_MODE_ALWAYS,
                elapsedMs = 1_000L,
                targetMs = null,
            ),
        )
    }
}
