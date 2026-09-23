package com.op.aod.enhance.hook

import java.util.concurrent.atomic.AtomicLong

/**
 * Per-callback double-tap gate.
 *
 * The caller supplies a monotonic timestamp (SystemClock.elapsedRealtime()).
 * First tap is blocked; a second tap inside [thresholdMs] is allowed and resets
 * the gate. Each hooked callback owns its own instance to avoid cross-area state.
 */
internal class DoubleTapGate(
    private val thresholdMs: Long = DEFAULT_THRESHOLD_MS,
) {
    private val lastBlockedTimeMs = AtomicLong(NO_TAP)

    fun shouldAllow(nowMs: Long): Boolean {
        while (true) {
            val previous = lastBlockedTimeMs.get()
            if (previous != NO_TAP) {
                val delta = nowMs - previous
                if (delta >= 0L && delta < thresholdMs) {
                    if (lastBlockedTimeMs.compareAndSet(previous, NO_TAP)) return true
                    continue
                }
            }
            if (lastBlockedTimeMs.compareAndSet(previous, nowMs)) return false
        }
    }

    fun reset() {
        lastBlockedTimeMs.set(NO_TAP)
    }

    companion object {
        const val DEFAULT_THRESHOLD_MS = 350L
        private const val NO_TAP = Long.MIN_VALUE
    }
}
