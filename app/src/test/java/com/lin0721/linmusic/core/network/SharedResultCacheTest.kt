package com.lin0721.linmusic.core.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SharedResultCacheTest {
    @Test fun concurrentDisplaysLoadOnce() = runTest {
        val cache = SharedResultCache<Int, String>(scope = this)
        val ready = CompletableDeferred<Unit>()
        var requests = 0
        val a = async { cache.get(1) { requests++; ready.await(); Result.success("lyrics") } }
        val b = async { cache.get(1) { error("Duplicate request") } }
        runCurrent(); ready.complete(Unit)
        assertEquals("lyrics", a.await().getOrThrow())
        assertEquals("lyrics", b.await().getOrThrow())
        assertEquals(1, requests)
    }
    @Test fun failedResultsAreRetryable() = runTest {
        val cache = SharedResultCache<Int, String>(scope = this)
        assertTrue(cache.get(1) { Result.failure(Exception("offline")) }.isFailure)
        assertEquals("online", cache.get(1) { Result.success("online") }.getOrThrow())
    }
    @Test fun expiredAndEvictedEntriesReload() = runTest {
        var time = 0L
        val cache = SharedResultCache<Int, String>(capacity = 1, ttlMs = 10, scope = this, nowMs = { time })
        cache.get(1) { Result.success("old") }
        assertEquals("old", cache.get(1) { error("Should hit cache") }.getOrThrow())
        time = 10
        assertEquals("new", cache.get(1) { Result.success("new") }.getOrThrow())
        cache.get(2) { Result.success("two") }
        assertEquals("reload", cache.get(1) { Result.success("reload") }.getOrThrow())
    }
    @Test fun fallbackPaletteIsNotCached() = runTest {
        val cache = SharedResultCache<Int, String>(scope = this, cacheValue = { it != "fallback" })
        cache.get(1) { Result.success("fallback") }
        assertEquals("real", cache.get(1) { Result.success("real") }.getOrThrow())
    }
    @Test fun oneDisplayCancellationDoesNotCancelOther() = runTest {
        val cache = SharedResultCache<Int, String>(scope = this)
        val ready = CompletableDeferred<Unit>()
        val a = async { cache.get(1) { ready.await(); Result.success("ok") } }
        runCurrent()
        val b = async { cache.get(1) { error("Duplicated") } }
        runCurrent(); a.cancel(); ready.complete(Unit)
        assertEquals("ok", b.await().getOrThrow())
    }
}
