package com.lin0721.linmusic.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bounded, short-lived read-only cache with single-flight requests across display owners.
 * Failed requests are retryable; cancelling one screen does not cancel the other consumer. */
class SharedResultCache<K : Any, V : Any>(
    private val capacity: Int = 12,
    private val ttlMs: Long = 120_000,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val cacheValue: (V) -> Boolean = { true }
) {
    private data class Entry<V>(val value: V, val expires: Long)
    private val mutex = Mutex()
    private val entries = LinkedHashMap<K, Entry<V>>()
    private val inFlight = mutableMapOf<K, Deferred<Result<V>>>()

    suspend fun get(key: K, load: suspend () -> Result<V>): Result<V> {
        var cached: V? = null
        val pending = mutex.withLock {
            val entry = entries[key]
            if (entry != null && nowMs() < entry.expires) {
                cached = entry.value
                null
            } else {
                entries.remove(key)
                inFlight[key] ?: scope.async {
                    try {
                        val result = load()
                        result.getOrNull()?.takeIf(cacheValue)?.let { value ->
                            mutex.withLock {
                                entries[key] = Entry(value, nowMs() + ttlMs)
                                while (entries.size > capacity) entries.remove(entries.keys.first())
                            }
                        }
                        result
                    } finally { mutex.withLock { inFlight.remove(key) } }
                }.also { inFlight[key] = it }
            }
        }
        return cached?.let { Result.success(it) } ?: pending!!.await()
    }
}
