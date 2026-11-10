package com.op.aod.enhance.data

import org.junit.Assert.*
import org.junit.Test

class HookStatusRegistryTest {
    @Test fun swallowedFailureCannotReportSuccessWithoutARegisteredPath() {
        val r = HookStatusRegistry(); r.begin("brightness")
        assertEquals("unavailable", r.snapshot().single().state)
    }
    @Test fun obsoleteAbsentAlternativeDoesNotHideWorkingModernPaths() {
        val r = HookStatusRegistry(); r.begin("tap"); r.skipped("tap", "obsolete")
        r.registered("tap", "normal", "modern path")
        assertEquals("loaded", r.snapshot().single().state)
        r.failed("tap", "present but unsupported")
        assertEquals("partial", r.snapshot().single().state)
    }
    @Test fun snapshotsKeepRegistrationSeparateFromHitsAndDoNotChangeRetroactively() {
        val r = HookStatusRegistry(); r.registered("tap", "normal", "path")
        val old = r.snapshot().single(); assertTrue(old.hits.isEmpty())
        r.hit("tap", "normal"); assertEquals(setOf("normal"), r.snapshot().single().hits)
        assertTrue(old.hits.isEmpty())
    }
}
