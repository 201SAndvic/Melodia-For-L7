package com.lin0721.linmusic.feature.player.ui

import org.junit.Assert.*
import org.junit.Test

class ChorusSeekPolicyTest {
    @Test fun missingInvalidOrOutOfTrackChorusDoesNotDisplay() {
        listOf(null, -1L, 0L, 100L, 200L).forEach { assertNull(chorusFraction(it, 100)) }
        assertNull(chorusFraction(50, 0))
        assertEquals(0.5f, chorusFraction(50, 100)!!, 0f)
    }
    @Test fun snapRadiusUsesPhysicalDistanceOnBothLayouts() {
        assertTrue(snapChorusSeek(0.51f, 0.5f, 1000f, 24f).snapped)
        assertFalse(snapChorusSeek(0.53f, 0.5f, 1000f, 24f).snapped)
        assertEquals(0.5f, snapChorusSeek(0.51f, 0.5f, 1000f, 24f).fraction, 0f)
        assertFalse(snapChorusSeek(0.5f, null, 1000f, 24f).snapped)
    }
    @Test fun invalidSizeNeverSnapsAndSeekStaysInBounds() {
        assertFalse(snapChorusSeek(0.5f, 0.5f, 0f, 24f).snapped)
        assertEquals(0f, snapChorusSeek(-1f, null, 1000f, 24f).fraction, 0f)
        assertEquals(1f, snapChorusSeek(2f, null, 1000f, 24f).fraction, 0f)
    }
}
