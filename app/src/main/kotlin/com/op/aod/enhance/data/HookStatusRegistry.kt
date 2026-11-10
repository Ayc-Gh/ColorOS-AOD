package com.op.aod.enhance.data

/** Per-process in-memory evidence, queried on demand; never polls the host. */
internal class HookStatusRegistry {
    data class Status(val name: String, val paths: Map<String, String>, val hits: Set<String>, val failures: Set<String>, val skipped: Set<String>) {
        val state: String get() = when { paths.isEmpty() -> "unavailable"; failures.isNotEmpty() -> "partial"; else -> "loaded" }
    }
    private class Entry {
        val paths = linkedMapOf<String, String>()
        val hits = linkedSetOf<String>()
        val failures = linkedSetOf<String>()
        val skipped = linkedSetOf<String>()
    }
    private val entries = linkedMapOf<String, Entry>()
    @Synchronized fun begin(name: String) { entries.getOrPut(name) { Entry() } }
    @Synchronized fun registered(name: String, id: String, path: String) { entries.getOrPut(name) { Entry() }.paths[id] = path }
    @Synchronized fun hit(name: String, id: String) { entries.getOrPut(name) { Entry() }.hits.add(id) }
    @Synchronized fun failed(name: String, detail: String) { entries.getOrPut(name) { Entry() }.failures.add(detail.take(300)) }
    @Synchronized fun skipped(name: String, detail: String) { entries.getOrPut(name) { Entry() }.skipped.add(detail.take(300)) }
    @Synchronized fun snapshot(): List<Status> = entries.map { (name, e) -> Status(name, e.paths.toMap(), e.hits.toSet(), e.failures.toSet(), e.skipped.toSet()) }
}
