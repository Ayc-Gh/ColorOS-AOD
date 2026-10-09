package com.op.aod.enhance.hook

import android.view.Display
import com.op.aod.enhance.data.AodConfigContract
import org.junit.Assert.*
import org.junit.Test

class AodDurationSessionTest {
    @Test fun lowLightHideReleasesOffEvenWithTheSameNativeReason() {
        for (mode in listOf(AodConfigContract.DURATION_MODE_CUSTOM, AodConfigContract.DURATION_MODE_ALWAYS)) {
            val session = AodDurationSession(1, 1000, mode, 1440)
            fun intercept() = AodDurationPolicy.shouldInterceptNativeTimeout(
                Display.STATE_OFF, AodDurationPolicy.NATIVE_TIMEOUT_REASON, mode,
                session.elapsedMs(9763), session.targetMs, session.visible,
            )
            assertTrue(intercept())
            session.allowHide()
            assertFalse(intercept())
            assertEquals(AodDurationSession.Phase.HIDDEN, session.phase)
        }
    }

    @Test fun wakeEndsSessionAndNewSessionHasIndependentDeadline() {
        val old = AodDurationSession(1, 1000, AodConfigContract.DURATION_MODE_1_MINUTE, 5)
        old.end()
        val next = AodDurationSession(2, 9000, AodConfigContract.DURATION_MODE_1_MINUTE, 5)
        assertFalse(old.visible)
        assertTrue(next.visible)
        assertEquals(0L, next.elapsedMs(9000))
        assertEquals(60_000L, next.targetMs)
    }
}
