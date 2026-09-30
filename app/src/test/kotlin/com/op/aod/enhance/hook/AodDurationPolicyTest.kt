package com.op.aod.enhance.hook

import android.view.Display
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AodDurationPolicyTest {
    @Test
    fun holdsNormalDozeSuspendBeforeDeadline() {
        assertTrue(
            AodDurationPolicy.shouldHoldNativeSuspend(
                active = true,
                beforeDeadline = true,
                powerSave = false,
                requestState = Display.STATE_DOZE,
                voteState = Display.STATE_DOZE_SUSPEND,
                reason = "Panoramic-Aod-Show",
            ),
        )
    }

    @Test
    fun doesNotHoldAfterDeadline() {
        assertFalse(
            AodDurationPolicy.shouldHoldNativeSuspend(
                active = true,
                beforeDeadline = false,
                powerSave = false,
                requestState = Display.STATE_DOZE,
                voteState = Display.STATE_DOZE_SUSPEND,
                reason = "Aod-Show",
            ),
        )
    }

    @Test
    fun preservesSafetyVotes() {
        assertFalse(
            AodDurationPolicy.shouldHoldNativeSuspend(
                active = true,
                beforeDeadline = true,
                powerSave = false,
                requestState = Display.STATE_DOZE,
                voteState = Display.STATE_DOZE_SUSPEND,
                reason = "Aod-Proximity-Pocket",
            ),
        )
    }

    @Test
    fun preservesPowerSave() {
        assertFalse(
            AodDurationPolicy.shouldHoldNativeSuspend(
                active = true,
                beforeDeadline = true,
                powerSave = true,
                requestState = Display.STATE_DOZE,
                voteState = Display.STATE_DOZE_SUSPEND,
                reason = "Aod-Show",
            ),
        )
    }
}
