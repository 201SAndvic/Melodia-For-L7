package com.lin0721.linmusic.core.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class WeakNetworkCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private fun cache(scope: CoroutineScope, owner: () -> String = { "A" }, online: () -> Boolean = { true },
                      enabled: () -> Boolean = { true }, now: () -> Long = { 1000L }, fresh: Long = 30_000,
                      maxBytes: Long = 32 * 1024 * 1024, timeout: Long = 6000) = WeakNetworkCache(
        folder.root, account = { owner() }, enabled = { enabled() }, online = online,
        scope = scope, now = now, freshMs = fresh, maxBytes = maxBytes, requestTimeoutMs = timeout
    )

    @Test fun successfulDataSurvivesRecreationAndWorksOfflineWithoutRequest() = runTest {
        cache(this).cached("playlist") { flowOf(Result.success("stored")) }.first()
        val offline = cache(this, online = { false })
        val value = offline.cached<String>("playlist") { error("Must not request while offline") }.first().getOrThrow()
        assertEquals("stored", value)
    }

    @Test fun freshHitDoesNotStartRedundantNetworkWork() = runTest {
        val store = cache(this)
        assertEquals("one", store.cached("lyrics") { flowOf(Result.success("one")) }.first().getOrThrow())
        assertEquals("one", store.cached<String>("lyrics") { error("Already fresh") }.first().getOrThrow())
    }

    @Test fun firstConsumerDoesNotCancelStaleRefreshAndScreensShareRequest() = runBlocking {
        var clock = 1000L
        var requests = 0
        // This case uses actual filesystem IO. A virtual timeout can advance
        // while the real IO thread is still busy, producing a false timeout.
        val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
        val store = cache(refreshScope, now = { clock }, fresh = 100)
        store.cached("song") { flowOf(Result.success("old")) }.first()
        clock += 2000
        val releaseRefresh = CompletableDeferred<Unit>()
        val source: () -> Flow<Result<String>> = { flow { requests++; releaseRefresh.await(); emit(Result.success("new")) } }
        assertEquals("old", store.cached("song", source = source).first().getOrThrow())
        assertEquals("old", store.cached("song", source = source).first().getOrThrow())
        releaseRefresh.complete(Unit)
        refreshScope.coroutineContext[Job]!!.children.toList().joinAll()
        assertEquals(1, requests)
        assertEquals("new", store.readSaved("song", String.serializer()))
        } finally { refreshScope.cancel() }
    }

    @Test fun weakOnlineFailurePreservesStaleDataButUnauthorizedRemovesIt() = runTest {
        var clock = 1000L
        val store = cache(this, now = { clock }, fresh = 10)
        store.cached("library") { flowOf(Result.success("old")) }.first()
        clock += 2000
        val network = store.cached<String>("library") { flowOf(Result.failure(AppError.NetworkError)) }.toList()
        assertEquals(listOf("old"), network.map { it.getOrNull() })
        clock += 2000
        val unauthorized = store.cached<String>("library") { flowOf(Result.failure(AppError.Unauthorized)) }.toList()
        assertEquals(AppError.Unauthorized, unauthorized.last().exceptionOrNull())
        assertNull(store.readSaved("library", String.serializer()))
    }

    @Test fun accountsAreIsolatedAndDisabledModeBypassesDisk() = runTest {
        var owner = "A"
        var enabled = true
        val store = cache(this, owner = { owner }, enabled = { enabled }, online = { false })
        store.save("private_playlist", String.serializer(), "A data")
        owner = "B"
        assertNull(store.readSaved("private_playlist", String.serializer()))
        owner = "A"
        enabled = false
        assertEquals("direct", store.cached("private_playlist") { flowOf(Result.success("direct")) }.first().getOrThrow())
    }

    @Test fun corruptFilesAndDiskLimitDoNotBreakRequests() = runTest {
        val store = cache(this, maxBytes = 300)
        repeat(5) { store.save("entry:$it", String.serializer(), "x".repeat(100)) }
        assertTrue(folder.root.listFiles().orEmpty().sumOf { it.length() } <= 300)
        folder.root.listFiles().orEmpty().forEach { it.writeText("bad json") }
        assertNull(store.readSaved("entry:4", String.serializer()))
        assertEquals("fresh", store.cached("entry:4") { flowOf(Result.success("fresh")) }.first().getOrThrow())
    }

    @Test fun requestTimeoutDoesNotRemoveUsableCachedData() = runTest {
        var clock = 1000L
        val store = cache(this, now = { clock }, fresh = 10, timeout = 100)
        store.save("slow", String.serializer(), "usable")
        clock += 100
        val result = store.cached<String>("slow") { flow { delay(2000); emit(Result.success("late")) } }.toList()
        assertEquals(listOf("usable"), result.map { it.getOrNull() })
        assertEquals("usable", store.readSaved("slow", String.serializer()))
    }

    @Test fun businessAndParseFailuresAreNotSilentlyReportedAsSuccess() {
        assertTrue(isCacheFallbackError(AppError.NetworkError))
        assertTrue(isCacheFallbackError(AppError.BizError(503, null)))
        assertFalse(isCacheFallbackError(AppError.BizError(403, null)))
        assertFalse(isCacheFallbackError(AppError.Unauthorized))
        assertFalse(isCacheFallbackError(AppError.ParseError))
    }

    @Test fun mutationInvalidationDoesNotClearAudioOrOtherAccounts() = runTest {
        var owner = "A"
        val store = cache(this, owner = { owner })
        store.save("playlist", String.serializer(), "A old")
        store.save("audio:1:standard", String.serializer(), "audio index")
        owner = "B"
        store.save("playlist", String.serializer(), "B data")
        owner = "A"
        store.invalidateMetadata()
        assertNull(store.readSaved("playlist", String.serializer()))
        assertEquals("audio index", store.readSaved("audio:1:standard", String.serializer()))
        owner = "B"
        assertEquals("B data", store.readSaved("playlist", String.serializer()))
    }

    @Test fun mutationDoesNotReusePreviousShortLivedNetworkResult() = runTest {
        val store = cache(this)
        store.cached("playlist") { flowOf(Result.success("old")) }.first()
        store.invalidateMetadata()
        assertEquals("updated", store.cached("playlist") { flowOf(Result.success("updated")) }.first().getOrThrow())
    }
}
