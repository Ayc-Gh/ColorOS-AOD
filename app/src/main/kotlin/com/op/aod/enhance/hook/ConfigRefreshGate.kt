package com.op.aod.enhance.hook

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Single-flight + retry-backoff gate for cross-process configuration refreshes.
 */
internal class ConfigRefreshGate(
    private val retryBackoffNs: Long,
) {
    private val inFlight = AtomicBoolean(false)
    private val retryAtNs = AtomicLong(0L)

    fun tryAcquire(nowNs: Long, force: Boolean = false): Boolean {
        if (!force && nowNs < retryAtNs.get()) return false
        return inFlight.compareAndSet(false, true)
    }

    fun success() {
        retryAtNs.set(0L)
        inFlight.set(false)
    }

    fun failure(nowNs: Long) {
        retryAtNs.set(nowNs + retryBackoffNs)
        inFlight.set(false)
    }
}
