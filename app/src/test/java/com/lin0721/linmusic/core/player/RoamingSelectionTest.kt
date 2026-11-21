package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.*
import org.junit.Test

class RoamingSelectionTest {
    private fun queued(id: Long) = QueueItem(id, "", "", "")
    @Test fun excludesSeedQueuedAndDuplicateCandidates() {
        val result = selectRoamingSongs(listOf(1L, 2, 3, 3, 4).map { Track(id = it) }, 1, listOf(queued(2)), 20)
        assertEquals(listOf(3L, 4L), result.map { it.id })
    }
    @Test fun exhaustedCandidatesDoNotFallBackToOldSongs() {
        assertTrue(selectRoamingSongs(listOf(Track(id = 1), Track(id = 2)), 1, listOf(queued(2)), 20).isEmpty())
    }
    @Test fun respectsFillLimit() {
        assertEquals(2, selectRoamingSongs((1L..10L).map { Track(id = it) }, 1, emptyList(), 2).size)
        assertTrue(selectRoamingSongs(listOf(Track(id = 2)), 1, emptyList(), 0).isEmpty())
    }
}
