package com.lin0721.linmusic.core.player

import androidx.test.platform.app.InstrumentationRegistry
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

/** Real controller, delayed fake network. The harness backs up/restores target playback preferences. */
class RoamingQueueDeviceTest {
    private fun item(id: Long) = QueueItem(id, "track$id", "", "")
    private class Repository : PlaybackRepository {
        var requests = 0
        val response = CompletableDeferred<List<Track>>()
        override fun getSimilarSongs(songId: Long): Flow<Result<List<Track>>> = flow {
            requests++
            emit(Result.success(response.await()))
        }
        override fun getSongUrl(songId: Long): Flow<Result<String>> = emptyFlow()
        override fun getLyrics(songId: Long): Flow<Result<List<LyricLine>>> = emptyFlow()
        override fun getIntelligenceSongs(songId: Long, playlistId: Long): Flow<Result<List<Track>>> = emptyFlow()
        override fun reportStartPlay(songId: Long): Flow<Result<Unit>> = emptyFlow()
        override fun reportPlayEnd(songId: Long, playedSeconds: Long): Flow<Result<Unit>> = emptyFlow()
    }
    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(3000) {
        while (!condition()) delay(10)
    }

    @Test fun onlyOneRequestAndUnplayedQueueIsPreserved() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            withContext(Dispatchers.Main) {
                val queue = PlaybackQueue().apply {
                    replaceAll((1L..8L).map(::item), 0)
                    setPlayContext(SimilarRoamingController.CONTEXT_ROAMING)
                }
                val repository = Repository()
                val controller = SimilarRoamingController(scope, repository, SettingsPreferences(context), queue,
                    PlaybackStateStore(scope, PlaybackPreferences(context)))
                controller.prefetchOnPlay(1)
                delay(30)
                assertEquals(0, repository.requests) // Seven upcoming tracks: no request needed.
                queue.setCurrentIndex(6)
                controller.prefetchOnPlay(7)
                controller.prefetchOnPlay(7)
                waitFor { repository.requests == 1 }
                repository.response.complete((1L..40L).map { Track(id = it) } + Track(id = 9))
                waitFor { queue.upcomingCount == 20 }
                assertEquals((1L..8L).toList(), queue.items.value.take(8).map { it.songId })
                assertEquals(7L, queue.currentItem()!!.songId)
                assertEquals(queue.size, queue.items.value.map { it.songId }.toSet().size)
                controller.prefetchOnPlay(7)
                delay(30)
                assertEquals(1, repository.requests)
            }
        } finally { scope.cancel() }
    }

    @Test fun exitingRoamingRejectsAnOutstandingResponse() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            withContext(Dispatchers.Main) {
                val queue = PlaybackQueue().apply { replaceAll(listOf(item(1), item(2)), 0) }
                val repository = Repository()
                val controller = SimilarRoamingController(scope, repository, SettingsPreferences(context), queue,
                    PlaybackStateStore(scope, PlaybackPreferences(context)))
                controller.prepare()
                queue.replaceWithSingle(item(1))
                queue.setPlayContext(SimilarRoamingController.CONTEXT_ROAMING)
                controller.prefetchOnPlay(1)
                waitFor { repository.requests == 1 }
                controller.disable()
                repository.response.complete(listOf(Track(id = 99)))
                delay(50)
                assertEquals(listOf(1L, 2L), queue.items.value.map { it.songId })
                assertNull(queue.playContext.value)
            }
        } finally { scope.cancel() }
    }
}
