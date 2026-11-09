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
        var writable = true
        var rejectClear = false
        override fun get() = value
        override fun set(value: Boolean): Boolean {
            if (!writable || (!value && rejectClear)) return false
            this.value = value
            return true
        }
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

    @Test fun offlineWriteShowsPendingThenReconnectShowsSynced() {
        val local = Memory()
        val sync = AodConfigSync(local, Marker())
        assertTrue(sync.write(custom) { null })
        assertEquals(AodSyncState.PENDING, sync.status.state)
        assertTrue(sync.status.hasPendingChanges)
        val remote = Memory()
        assertTrue(sync.bind(remote))
        assertEquals(AodSyncState.SYNCED, sync.status.state)
        assertFalse(sync.status.hasPendingChanges)
        assertEquals(custom, AodConfigCodec.decode(remote.values))
    }

    @Test fun onlineWriteRequiresSuccessfulRemoteCommitForSyncedStatus() {
        val sync = AodConfigSync(Memory(), Marker())
        val remote = Memory()
        assertTrue(sync.write(custom) { remote })
        assertEquals(AodSyncState.SYNCED, sync.status.state)
        assertEquals(custom, AodConfigCodec.decode(remote.values))
    }

    @Test fun remoteCommitFailureIsDistinctFromOfflineAndRetryPreservesEdit() {
        val sync = AodConfigSync(Memory(), Marker())
        val remote = Memory().apply { writable = false }
        assertTrue(sync.write(custom) { remote })
        assertEquals(AodSyncState.FAILED, sync.status.state)
        assertEquals(AodSyncIssue.REMOTE, sync.status.issue)
        assertEquals(custom, sync.read { remote })
        assertEquals(AodSyncState.FAILED, sync.status.state)
        remote.writable = true
        assertTrue(sync.bind(remote))
        assertEquals(AodSyncState.SYNCED, sync.status.state)
        assertEquals(custom, AodConfigCodec.decode(remote.values))
    }

    @Test fun disconnectRevokesSyncedStatusWithoutInventingAnUnsentEdit() {
        val sync = AodConfigSync(Memory(), Marker())
        assertTrue(sync.bind(Memory(custom)))
        sync.disconnected()
        assertEquals(AodSyncState.PENDING, sync.status.state)
        assertFalse(sync.status.hasPendingChanges)
        assertEquals(custom, sync.read { null })
    }

    @Test fun failedLocalSaveIsNotReportedAsAnOfflineSuccess() {
        val local = Memory().apply { writable = false }
        val sync = AodConfigSync(local, Marker())
        assertFalse(sync.write(custom) { null })
        assertEquals(AodSyncIssue.LOCAL_SAVE, sync.status.issue)
        sync.disconnected()
        assertEquals(AodSyncState.FAILED, sync.status.state)
        assertNotEquals(custom, sync.read { null })
    }

    @Test fun failedPendingMarkerDoesNotWriteRemoteOrReportSynced() {
        val remote = Memory()
        val sync = AodConfigSync(Memory(), Marker().apply { writable = false })
        assertFalse(sync.write(custom) { remote })
        assertEquals(AodSyncIssue.LOCAL_SAVE, sync.status.issue)
        assertEquals(AodSyncState.FAILED, sync.status.state)
        assertTrue(remote.values.isEmpty())
    }

    @Test fun failedConfirmationRemainsRetryableEvenAfterRemoteCommit() {
        val marker = Marker().apply { rejectClear = true }
        val sync = AodConfigSync(Memory(), marker)
        val remote = Memory()
        assertTrue(sync.write(custom) { remote })
        assertEquals(AodSyncIssue.CONFIRMATION, sync.status.issue)
        assertEquals(AodSyncState.FAILED, sync.status.state)
        assertTrue(marker.value)
        marker.rejectClear = false
        assertTrue(sync.bind(remote))
        assertEquals(AodSyncState.SYNCED, sync.status.state)
        assertFalse(marker.value)
    }

    @Test fun remoteReadExceptionRetainsFallbackAndReportsFailureUntilRetry() {
        val sync = AodConfigSync(Memory(), Marker())
        assertTrue(sync.bind(Memory(custom)))
        assertEquals(custom, sync.read { error("bridge disconnected") })
        assertEquals(AodSyncState.FAILED, sync.status.state)
        assertEquals(AodSyncIssue.REMOTE, sync.status.issue)
        assertTrue(sync.bind(Memory(custom)))
        assertEquals(AodSyncState.SYNCED, sync.status.state)
    }
}
