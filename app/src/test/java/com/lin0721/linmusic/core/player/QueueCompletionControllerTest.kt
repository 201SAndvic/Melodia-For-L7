package com.lin0721.linmusic.core.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class QueueCompletionControllerTest {
    private fun item(id: Long) = QueueItem(id, "song$id", "artist", "")
    private fun queue() = PlaybackQueue().apply { replaceAll(listOf(item(1), item(2)), 0) }

    @Test fun fillsInBatchesWithoutReplacingCurrentOrExistingNext() = runTest {
        val queue = queue()
        var saves = 0
        val fill = QueueCompletionController(this, queue, { saves++ }, { throw it })
        fill.start(queue.revision, listOf(listOf(2, 3), listOf(4))) { it.map(::item) }
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L, 4L), queue.items.value.map { it.songId })
        assertEquals(1L, queue.currentItem()!!.songId)
        assertEquals(2, saves)
    }

    @Test fun discardsLateResultWhenSameSongsAreSelectedAsANewQueue() = runTest {
        val queue = queue()
        val response = CompletableDeferred<List<QueueItem>>()
        val fill = QueueCompletionController(this, queue, {}, { throw it })
        fill.start(queue.revision, listOf(listOf(3))) { response.await() }
        runCurrent()
        queue.replaceAll(listOf(item(1), item(2)), 0)
        response.complete(listOf(item(3)))
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), queue.items.value.map { it.songId })
    }

    @Test fun clearingOrEditingQueuePreventsLateRefill() = runTest {
        for (edit in listOf<(PlaybackQueue) -> Unit>({ it.keepOnlyCurrent() }, { it.move(0, 1) }, { it.insertNext(listOf(item(9))) })) {
            val queue = queue()
            val response = CompletableDeferred<List<QueueItem>>()
            val fill = QueueCompletionController(this, queue, {}, { throw it })
            fill.start(queue.revision, listOf(listOf(3))) { response.await() }
            runCurrent()
            edit(queue)
            val edited = queue.items.value
            response.complete(listOf(item(3)))
            advanceUntilIdle()
            assertEquals(edited, queue.items.value)
        }
    }

    @Test fun failedBatchLeavesEarlierAppendsAndPlaybackIntact() = runTest {
        val queue = queue()
        var failures = 0
        val fill = QueueCompletionController(this, queue, {}, { failures++ })
        fill.start(queue.revision, listOf(listOf(3), listOf(4))) {
            if (it == listOf(4L)) error("network") else it.map(::item)
        }
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), queue.items.value.map { it.songId })
        assertEquals(1, failures)
        assertEquals(1L, queue.currentItem()!!.songId)
    }

    @Test fun timeoutDoesNotHangOrReplaceQueue() = runTest {
        val queue = queue()
        var failures = 0
        val fill = QueueCompletionController(this, queue, {}, { failures++ })
        fill.start(queue.revision, listOf(listOf(3))) { awaitCancellation() }
        advanceUntilIdle()
        assertEquals(1, failures)
        assertEquals(2, queue.size)
    }

    @Test fun shuffledAppendCanReturnToOriginalOrderAndDeduplicatesIncomingBatch() {
        val queue = queue()
        queue.applyMode(PlayMode.SHUFFLE, queue.currentItem())
        queue.extend(listOf(item(2), item(3), item(3), item(4)))
        assertEquals(4, queue.size)
        assertEquals(1L, queue.currentItem()!!.songId)
        queue.applyMode(PlayMode.LIST_LOOP, queue.currentItem())
        assertEquals(listOf(1L, 2L, 3L, 4L), queue.items.value.map { it.songId })
    }
}
