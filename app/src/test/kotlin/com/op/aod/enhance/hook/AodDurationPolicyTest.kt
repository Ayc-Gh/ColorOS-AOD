package com.op.aod.enhance.hook

import android.view.Display
import com.op.aod.enhance.data.AodConfigContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AodDurationPolicyTest {
    @Test
    fun systemModeNeverInterceptsNativeTimeout() {
        assertFalse(
            AodDurationPolicy.shouldInterceptNativeTimeout(
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
                state = Display.STATE_DOZE,
                reason = AodDurationPolicy.NATIVE_TIMEOUT_REASON,
                mode = AodConfigContract.DURATION_MODE_ALWAYS,
                elapsedMs = 1_000L,
                targetMs = null,
            ),
        )
    }
}
