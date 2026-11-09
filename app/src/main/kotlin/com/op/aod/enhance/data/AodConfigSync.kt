package com.op.aod.enhance.data

/** Serialized by the Android store. No Android dependency, so retry paths are testable. */
internal class AodConfigSync(private val local: Storage, private val pending: Pending) {
    interface Storage {
        fun read(): Map<String, *>
        fun write(config: AodConfig): Boolean
    }
    interface Pending {
        fun get(): Boolean
        fun set(value: Boolean): Boolean
    }
    private var cached: AodConfig? = null
    var status = AodSyncStatus(hasPendingChanges = pending.get())
        private set

    private fun fail(issue: AodSyncIssue): Boolean {
        status = AodSyncStatus(AodSyncState.FAILED, issue, pending.get())
        return false
    }

    fun disconnected() {
        // A disconnect must not conceal a failed local save.
        if (status.issue != AodSyncIssue.LOCAL_SAVE) {
            status = AodSyncStatus(hasPendingChanges = pending.get())
        }
    }

    fun bind(remote: Storage): Boolean {
        val values = runCatching { remote.read() }.getOrElse { return fail(AodSyncIssue.REMOTE) }
        if (pending.get() || values.isEmpty()) {
            val config = AodConfigCodec.decode(local.read())
            if (!runCatching { remote.write(config) }.getOrDefault(false)) return fail(AodSyncIssue.REMOTE)
            cached = config
        } else {
            val config = AodConfigCodec.decode(values)
            if (!local.write(config)) return fail(AodSyncIssue.LOCAL_BACKUP)
            cached = config
        }
        if (!pending.set(false)) return fail(AodSyncIssue.CONFIRMATION)
        status = AodSyncStatus(AodSyncState.SYNCED)
        return true
    }

    fun read(remote: () -> Storage?): AodConfig {
        if (pending.get()) return AodConfigCodec.decode(local.read()).also { cached = it }
        val storage = runCatching { remote() }.getOrElse {
            fail(AodSyncIssue.REMOTE)
            return cached ?: AodConfigCodec.decode(local.read()).also { cached = it }
        }
        if (storage == null) {
            disconnected()
            return cached ?: AodConfigCodec.decode(local.read()).also { cached = it }
        }
        val fresh = runCatching { AodConfigCodec.decode(storage.read()) }.getOrElse {
            fail(AodSyncIssue.REMOTE)
            return cached ?: AodConfigCodec.decode(local.read()).also { cached = it }
        }
        if (fresh != cached && !local.write(fresh)) {
            fail(AodSyncIssue.LOCAL_BACKUP)
        } else if (status.state != AodSyncState.FAILED) {
            status = AodSyncStatus(AodSyncState.SYNCED)
        }
        cached = fresh
        return fresh
    }

    /** True means durable locally; check status separately for remote delivery. */
    fun write(config: AodConfig, remote: () -> Storage?): Boolean {
        val safe = AodConfigCodec.sanitize(config)
        if (!pending.set(true) || !local.write(safe)) return fail(AodSyncIssue.LOCAL_SAVE)
        cached = safe
        status = AodSyncStatus(hasPendingChanges = true)
        val storage = runCatching { remote() }.getOrElse {
            fail(AodSyncIssue.REMOTE)
            return true
        } ?: return true
        if (!runCatching { storage.write(safe) }.getOrDefault(false)) {
            fail(AodSyncIssue.REMOTE)
        } else if (!pending.set(false)) {
            fail(AodSyncIssue.CONFIRMATION)
        } else {
            status = AodSyncStatus(AodSyncState.SYNCED)
        }
        return true
    }
}
