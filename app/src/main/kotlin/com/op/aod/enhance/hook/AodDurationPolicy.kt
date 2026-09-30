package com.op.aod.enhance.hook

import android.view.Display

internal object AodDurationPolicy {
    private val safetyKeywords = arrayOf(
        "proximity", "pocket", "thermal", "overheat", "battery",
        "power-save", "powersave", "shutdown", "emergency",
    )

    fun shouldHoldNativeSuspend(
        active: Boolean,
        beforeDeadline: Boolean,
        powerSave: Boolean,
        requestState: Int,
        voteState: Int,
        reason: String?,
    ): Boolean {
        if (!active || !beforeDeadline || powerSave) return false
        if (requestState != Display.STATE_DOZE) return false
        if (voteState != Display.STATE_DOZE_SUSPEND) return false
        val normalized = reason.orEmpty().lowercase()
        if (safetyKeywords.any(normalized::contains)) return false
        return true
    }
}
