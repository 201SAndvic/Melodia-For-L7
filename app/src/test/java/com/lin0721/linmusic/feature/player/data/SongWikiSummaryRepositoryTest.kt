package com.lin0721.linmusic.feature.player.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.*
import com.lin0721.linmusic.core.network.WeakNetworkCache
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SongWikiSummaryRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun staleUnknownSummaryIsNotFrozenBeforeFreshLanguageArrives() = runBlocking {
        var clock = 1000L
        val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val cache = WeakNetworkCache(folder.root, account = { "test" }, enabled = { true }, online = { true },
                scope = cacheScope, now = { clock }, freshMs = 10)
            cache.save("wiki_summary:1", SongWikiSummaryResponse.serializer(), SongWikiSummaryResponse(200))
            clock += 1000
            val api = Api()
            val result = SongWikiSummaryRepository(api, cache).get(1).getOrThrow()
            assertEquals("国语 / 英语", result.languageLabels())
            assertEquals(1, api.count)
        } finally { cacheScope.cancel() }
    }
    @Test fun offlineUsesStoredLanguageWithoutStartingANewNetworkRequest() = runBlocking {
        val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val api = Api()
            val saved = api.getSongWikiSummary(SongWikiSummaryRequest(1))
            val cache = WeakNetworkCache(folder.root, account = { "test" }, enabled = { true }, online = { false }, scope = cacheScope)
            cache.save("wiki_summary:1", SongWikiSummaryResponse.serializer(), saved)
            assertEquals("国语 / 英语", SongWikiSummaryRepository(api, cache).get(1).getOrThrow().languageLabels())
            assertEquals(1, api.count)
        } finally { cacheScope.cancel() }
    }
    private class Api : PlayerApi {
        override suspend fun getSongChorus(body: SongChorusRequest) = SongChorusResponse(200)
        var count = 0
        var code = 200
        var labels = listOf("国语", "英语")
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun getSongDetail(body: SongDetailRequest): SongDetailResponse = error("Language must not fetch song details")
        override suspend fun getSongCreators(body: SongCreatorsRequest): SongCreatorsResponse = error("Language must not fetch creators")
        override suspend fun getSongWikiSummary(body: SongWikiSummaryRequest): SongWikiSummaryResponse {
            count++
            gate?.await()
            return SongWikiSummaryResponse(code, SongWikiSummaryData(listOf(SongWikiBlock(
                code = "SONG_PLAY_ABOUT_SONG_BASIC", creatives = listOf(SongWikiCreative(
                    creativeType = "language", uiElement = SongWikiUiElement(textLinks = labels.map { SongWikiTextLink(it) })))))))
        }
    }
    @Test fun summaryIsSharedAndEveryLanguageLabelIsRead() = runTest {
        val api = Api()
        val repo = SongWikiSummaryRepository(api)
        assertEquals("国语 / 英语", repo.get(1).getOrThrow().languageLabels())
        assertTrue(repo.get(1).isSuccess)
        assertEquals(1, api.count)
    }
    @Test fun missingLanguageDoesNotBecomeA24HourNegativeMemoryCache() = runTest {
        val api = Api().apply { labels = emptyList() }
        val repo = SongWikiSummaryRepository(api)
        assertEquals("", repo.get(1).getOrThrow().languageLabels())
        api.labels = listOf("德语")
        assertEquals("德语", repo.get(1).getOrThrow().languageLabels())
        assertEquals(2, api.count)
    }
    @Test fun failureDoesNotPoisonCacheAndAnotherSongUsesItsOwnSummary() = runTest {
        val api = Api()
        val repo = SongWikiSummaryRepository(api)
        api.code = 500
        assertTrue(repo.get(1).isFailure)
        api.code = 200
        assertTrue(repo.get(1).isSuccess)
        assertTrue(repo.get(2).isSuccess)
        assertEquals(3, api.count)
    }
    @Test fun wikiAndLyricsConsumersShareOneInflightRequest() = runTest {
        val api = Api().apply { gate = CompletableDeferred() }
        val repo = SongWikiSummaryRepository(api)
        val first = async { repo.get(1) }
        val second = async { repo.get(1) }
        runCurrent()
        api.gate!!.complete(Unit)
        assertTrue(first.await().isSuccess)
        assertTrue(second.await().isSuccess)
        assertEquals(1, api.count)
    }
    @Test fun absentSummaryIsUnknownAndUnrelatedTagsDoNotBecomeLanguage() {
        assertEquals("", SongWikiSummaryResponse().languageLabels())
        val response = SongWikiSummaryResponse(200, SongWikiSummaryData(listOf(SongWikiBlock(
            code = "SONG_PLAY_ABOUT_WIKI", creatives = listOf(SongWikiCreative(creativeType = "language",
                uiElement = SongWikiUiElement(textLinks = listOf(SongWikiTextLink("英语")))))))))
        assertEquals("", response.languageLabels())
    }
}
