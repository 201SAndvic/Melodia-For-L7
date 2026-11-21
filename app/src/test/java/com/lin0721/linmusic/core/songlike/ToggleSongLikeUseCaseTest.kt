package com.lin0721.linmusic.core.songlike

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ToggleSongLikeUseCaseTest {
    private class Api : SongLikeApi {
        val calls = mutableListOf<LikeSongRequest>()
        var code = 200
        override suspend fun getLikedSongIds(body: LikeSongListRequest) = LikeSongListResponse(200)
        override suspend fun likeSong(body: LikeSongRequest): LikeSongResponse { calls += body; delay(10); return LikeSongResponse(code) }
    }
    @Test fun miniAndFullActionsBothDirectlyToggleSameGlobalLikedSet() = runBlocking {
        val api = Api(); val repo = SongLikeRepositoryImpl(api); val action = ToggleSongLikeUseCase(repo)
        assertTrue(action(1).isSuccess); assertTrue(1L in repo.likedSongIds.value)
        assertTrue(action(1).isSuccess); assertFalse(1L in repo.likedSongIds.value)
        assertEquals(listOf(true, false), api.calls.map { it.like })
    }
    @Test fun failuresRollBackLikeAndUnlikeWithoutCallingPlaylistCollection() = runBlocking {
        val api = Api().apply { code = 500 }; val repo = SongLikeRepositoryImpl(api); val action = ToggleSongLikeUseCase(repo)
        assertTrue(action(1).isFailure); assertFalse(1L in repo.likedSongIds.value)
        repo.syncLikedSongIds(setOf(1))
        assertTrue(action(1).isFailure); assertTrue(1L in repo.likedSongIds.value)
    }
    @Test fun concurrentDisplayOwnersReadLatestStateInsideSharedMutex() = runBlocking {
        val api = Api(); val repo = SongLikeRepositoryImpl(api); val action = ToggleSongLikeUseCase(repo)
        val a = async { action(1) }; val b = async { action(1) }
        assertTrue(a.await().isSuccess); assertTrue(b.await().isSuccess)
        assertEquals(listOf(true, false), api.calls.map { it.like }); assertFalse(1L in repo.likedSongIds.value)
        assertTrue(action(0).isFailure); assertEquals(2, api.calls.size)
    }
}
