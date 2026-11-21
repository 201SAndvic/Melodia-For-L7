package com.lin0721.linmusic.core.log

import org.junit.Assert.*
import org.junit.Test

class PlaybackPostMarkerWindowTest {
    private fun sample(at: Long) = PlaybackHealthSample(at, at, 9000, 3, true, true, 0, 50, 0, 12)
    @Test fun idleDoesNotRecordFollowUp() {
        val window = PlaybackPostMarkerWindow()
        assertNull(window.add(sample(6000)))
        assertNull(window.finish("capture_off"))
    }
    @Test fun emitsFiveSecondWindowOnlyOnce() {
        val window = PlaybackPostMarkerWindow()
        window.start(1000)
        for (at in 2000L..5000L step 1000) assertNull(window.add(sample(at)))
        val result = window.add(sample(6000))!!
        assertTrue(result.startsWith("markerT=1000 reason=complete"))
        assertTrue(result.contains("2000:2000/9000"))
        assertTrue(result.contains("6000:6000/9000"))
        assertNull(window.add(sample(7000)))
    }
    @Test fun repeatedMarkersCannotExtendWindow() {
        val window = PlaybackPostMarkerWindow()
        window.start(1000); window.start(4000)
        assertTrue(window.add(sample(6000))!!.startsWith("markerT=1000"))
    }
    @Test fun stopOrTrackChangeCanFlushPartialWindow() {
        val window = PlaybackPostMarkerWindow()
        window.start(1000); window.add(sample(2000))
        val result = window.finish("track_changed")!!
        assertTrue(result.contains("reason=track_changed"))
        assertTrue(result.contains("2000:2000"))
        assertNull(window.add(sample(6000)))
    }
    @Test fun resetAndOldSamplesDoNotLeakIntoNewSession() {
        val window = PlaybackPostMarkerWindow()
        window.start(1000); window.add(sample(2000)); window.reset()
        assertNull(window.add(sample(6000)))
        window.start(7000)
        assertNull(window.add(sample(6999)))
        val result = window.add(sample(12000))!!
        assertFalse(result.contains("2000:2000"))
        assertFalse(result.contains("6999:"))
    }
}
