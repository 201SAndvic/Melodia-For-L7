package com.lin0721.linmusic.core.ui.components

import org.junit.Assert.*
import org.junit.Test

class CoverDisplayGesturePolicyTest {
    @Test fun tapsAndAmbiguousDiagonalMotionDoNotSelectVertical() {
        assertEquals(CoverGestureAxis.UNDECIDED, coverGestureAxis(3f, 4f, 8f))
        assertEquals(CoverGestureAxis.UNDECIDED, coverGestureAxis(30f, 35f, 8f))
        assertNull(coverVerticalAction(CoverGestureAxis.UNDECIDED, -100f, 50f))
    }
    @Test fun horizontalSwipesRemainOwnedByOriginalSongSkipGesture() {
        assertEquals(CoverGestureAxis.HORIZONTAL, coverGestureAxis(100f, 30f, 8f))
        assertNull(coverVerticalAction(CoverGestureAxis.HORIZONTAL, -100f, 50f))
    }
    @Test fun onlyFullVerticalDistanceTriggersEnableOrDisable() {
        assertEquals(CoverGestureAxis.VERTICAL, coverGestureAxis(20f, -100f, 8f))
        assertNull(coverVerticalAction(CoverGestureAxis.VERTICAL, -49f, 50f))
        assertEquals(true, coverVerticalAction(CoverGestureAxis.VERTICAL, -50f, 50f))
        assertEquals(false, coverVerticalAction(CoverGestureAxis.VERTICAL, 50f, 50f))
    }
}
