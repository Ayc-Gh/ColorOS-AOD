package com.op.aod.enhance.data

/** Serialized by the Android store. No Android dependency, so disconnect/retry paths are testable. */
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

    fun bind(remote: Storage): Boolean {
        val values = remote.read()
        if (pending.get() || values.isEmpty()) {
            val config = AodConfigCodec.decode(local.read())
            if (!remote.write(config)) return false
            cached = config
            return pending.set(false)
        }
        val config = AodConfigCodec.decode(values)
        if (!local.write(config)) return false
        cached = config
        return true
    }

    fun read(remote: () -> Storage?): AodConfig {
        if (pending.get()) return AodConfigCodec.decode(local.read()).also { cached = it }
        val fresh = runCatching { remote()?.read()?.let(AodConfigCodec::decode) }.getOrNull()
        if (fresh != null) {
            if (fresh != cached) runCatching { local.write(fresh) }
            cached = fresh
            return fresh
        }
        return cached ?: AodConfigCodec.decode(local.read()).also { cached = it }
    }

    fun write(config: AodConfig, remote: () -> Storage?): Boolean {
        val safe = AodConfigCodec.sanitize(config)
        if (!pending.set(true) || !local.write(safe)) return false
        cached = safe
        if (runCatching { remote()?.write(safe) == true }.getOrDefault(false)) pending.set(false)
        return true
    }
}
