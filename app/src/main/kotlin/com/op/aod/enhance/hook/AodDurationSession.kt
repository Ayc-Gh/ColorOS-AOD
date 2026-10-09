package com.op.aod.enhance.hook

/** A physical OFF cannot extend a session whose UI was allowed to hide. */
internal class AodDurationSession(
    val id: Long,
    val startedAtMs: Long,
    val mode: Int,
    val customMinutes: Int,
) {
    enum class Phase { VISIBLE, HIDDEN, ENDED }
    @Volatile var phase = Phase.VISIBLE
        private set
    val targetMs = AodDurationPolicy.targetDurationMs(mode, customMinutes)
    val visible get() = phase == Phase.VISIBLE
    fun elapsedMs(now: Long) = (now - startedAtMs).coerceAtLeast(0L)
    fun allowHide() { phase = Phase.HIDDEN }
    fun end() { phase = Phase.ENDED }
}
