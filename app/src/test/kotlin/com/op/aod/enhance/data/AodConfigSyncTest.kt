package com.op.aod.enhance.data

import org.junit.Assert.*
import org.junit.Test

class AodConfigSyncTest {
    private class Memory(config: AodConfig? = null) : AodConfigSync.Storage {
        var values: Map<String, *> = config?.let(AodConfigCodec::encode) ?: emptyMap<String, Any>()
        var writable = true
        override fun read() = values
        override fun write(config: AodConfig): Boolean {
            if (!writable) return false
            values = AodConfigCodec.encode(config)
            return true
        }
    }
    private class Marker(var value: Boolean = false) : AodConfigSync.Pending {
        override fun get() = value
        override fun set(value: Boolean): Boolean { this.value = value; return true }
    }
    private val custom = AodConfig(initDark = 123, blockSingleClick = false, aodDurationCustomMinutes = 17)

    @Test fun existingRemoteSurvivesDisconnectAndProcessRestart() {
        val local = Memory()
        val pending = Marker()
        val sync = AodConfigSync(local, pending)
        assertTrue(sync.bind(Memory(custom)))
        assertEquals(custom, sync.read { null })
        assertEquals(custom, AodConfigSync(local, pending).read { null })
    }

    @Test fun offlineSingleEditPreservesOtherRemoteSettings() {
        val local = Memory()
        val pending = Marker()
        val remote = Memory(custom)
        val sync = AodConfigSync(local, pending)
        assertTrue(sync.bind(remote))
        val edited = sync.read { null }.copy(blockLowLightHide = false)
        assertTrue(sync.write(edited) { null })
        assertTrue(pending.value)
        assertTrue(sync.bind(remote))
        assertEquals(edited, AodConfigCodec.decode(remote.values))
        assertFalse(pending.value)
    }

    @Test fun failedRemoteCommitRetainsPendingEditForRetry() {
        val local = Memory()
        val pending = Marker()
        val remote = Memory().apply { writable = false }
        val sync = AodConfigSync(local, pending)
        assertTrue(sync.write(custom) { remote })
        assertTrue(pending.value)
        assertFalse(sync.bind(remote))
        remote.writable = true
        assertTrue(sync.bind(remote))
        assertEquals(custom, AodConfigCodec.decode(remote.values))
        assertFalse(pending.value)
    }

    @Test fun externalRemoteRefreshUpdatesDurableFallback() {
        val local = Memory()
        val pending = Marker()
        val sync = AodConfigSync(local, pending)
        assertEquals(custom, sync.read { Memory(custom) })
        assertEquals(custom, AodConfigSync(local, pending).read { error("bridge disconnected") })
    }

    @Test fun failedLocalBackupDoesNotReportSuccessfulBind() {
        val local = Memory().apply { writable = false }
        assertFalse(AodConfigSync(local, Marker()).bind(Memory(custom)))
    }

    @Test fun emptyRemoteMigratesLocalConfiguration() {
        val remote = Memory()
        assertTrue(AodConfigSync(Memory(custom), Marker()).bind(remote))
        assertEquals(custom, AodConfigCodec.decode(remote.values))
    }

    @Test fun malformedConfigIsSanitizedIdenticallyOnBothSides() {
        val values = mapOf<String, Any>(
            AodConfigContract.KEY_AOD_DURATION_CUSTOM_MINUTES to Int.MAX_VALUE,
            AodConfigContract.KEY_RUNNING_MULTIPLIER to Float.NaN,
            AodConfigContract.KEY_BLOCK_SINGLE_CLICK to "not a Boolean",
        )
        val parsed = AodConfigCodec.decode(values)
        assertEquals(AodConfigContract.MAX_AOD_DURATION_CUSTOM_MINUTES, parsed.aodDurationCustomMinutes)
        assertTrue(parsed.runningMultiplier.isFinite())
        assertEquals(parsed, AodConfigCodec.decode(AodConfigCodec.encode(parsed)))
    }
}
