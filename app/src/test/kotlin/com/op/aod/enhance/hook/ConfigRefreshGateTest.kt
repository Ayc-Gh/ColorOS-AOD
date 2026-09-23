package com.op.aod.enhance.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigRefreshGateTest {

    @Test
    fun onlyOneRefreshCanBeInFlight() {
        val gate = ConfigRefreshGate(5_000L)
        assertTrue(gate.tryAcquire(100L))
        assertFalse(gate.tryAcquire(100L))
        gate.success()
        assertTrue(gate.tryAcquire(101L))
    }

    @Test
    fun failureAppliesBackoffButForcedRefreshCanRetry() {
        val gate = ConfigRefreshGate(5_000L)
        assertTrue(gate.tryAcquire(100L))
        gate.failure(100L)
        assertFalse(gate.tryAcquire(200L))
        assertTrue(gate.tryAcquire(200L, force = true))
    }
}
