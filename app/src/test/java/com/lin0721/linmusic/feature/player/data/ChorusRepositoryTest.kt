package com.lin0721.linmusic.feature.player.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ChorusRepositoryTest {
    private class Api : PlayerApi {
        var count = 0
        var code = 200
        var entries = listOf(SongChorusItem(1, 45_000, 60_000))
        override suspend fun getSongChorus(body: SongChorusRequest): SongChorusResponse { count++; delay(10); return SongChorusResponse(code, entries) }
        override suspend fun getSongDetail(body: SongDetailRequest) = error("Unrelated details must not be loaded")
        override suspend fun getSongCreators(body: SongCreatorsRequest) = error("Unrelated creators must not be loaded")
        override suspend fun getSongWikiSummary(body: SongWikiSummaryRequest) = error("Unrelated wiki must not be loaded")
    }
    private fun repo(api: Api) = PlayerRepositoryImpl(api, null, SongWikiSummaryRepository(api))
    @Test fun twoScreensAndRepeatConsumersShareOneRequest() = runBlocking {
        val api = Api(); val repo = repo(api)
        val a = async { repo.getChorusStartTime(1).first().getOrThrow() }
        val b = async { repo.getChorusStartTime(1).first().getOrThrow() }
        assertEquals(45_000L, a.await()); assertEquals(45_000L, b.await())
        assertEquals(45_000L, repo.getChorusStartTime(1).first().getOrThrow())
        assertEquals(1, api.count)
    }
    @Test fun noDataIsCachedAsNoMarkerAndLocalTracksDoNotRequest() = runBlocking {
        val api = Api().apply { entries = emptyList() }; val repo = repo(api)
        assertNull(repo.getChorusStartTime(1).first().getOrThrow())
        assertNull(repo.getChorusStartTime(1).first().getOrThrow())
        assertNull(repo.getChorusStartTime(0).first().getOrThrow())
        assertEquals(1, api.count)
    }
    @Test fun failuresCanRetryAndCannotFreezeAFalseMarker() = runBlocking {
        val api = Api().apply { code = 500 }; val repo = repo(api)
        assertTrue(repo.getChorusStartTime(1).first().isFailure)
        api.code = 200
        assertEquals(45_000L, repo.getChorusStartTime(1).first().getOrThrow())
        assertEquals(2, api.count)
    }
    @Test fun responseMatchesTrackAndFirstValidSegmentOnly() {
        val response = SongChorusResponse(200, listOf(SongChorusItem(2, 10, 20), SongChorusItem(1, 0, 20),
            SongChorusItem(1, 50, 20), SongChorusItem(1, 30, 40), SongChorusItem(1, 20, 30)))
        assertEquals(20L, response.startFor(1).timeMs)
        assertNull(response.startFor(3).timeMs)
    }
}
