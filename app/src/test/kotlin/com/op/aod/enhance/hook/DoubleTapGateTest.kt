package com.op.aod.enhance.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleTapGateTest {

    @Test
    fun firstTapIsBlockedAndSecondFastTapIsAllowed() {
        val gate = DoubleTapGate(350L)
        assertFalse(gate.shouldAllow(1000L))
        assertTrue(gate.shouldAllow(1200L))
        assertFalse(gate.shouldAllow(1400L))
    }

    @Test
    fun slowSecondTapIsBlocked() {
        val gate = DoubleTapGate(350L)
        assertFalse(gate.shouldAllow(1000L))
        assertFalse(gate.shouldAllow(1400L))
    }

    @Test
    fun backwardsTimestampIsNeverTreatedAsDoubleTap() {
        val gate = DoubleTapGate(350L)
        assertFalse(gate.shouldAllow(1000L))
        assertFalse(gate.shouldAllow(500L))
    }
}
