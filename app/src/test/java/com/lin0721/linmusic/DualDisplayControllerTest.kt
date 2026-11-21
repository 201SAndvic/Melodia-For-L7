package com.lin0721.linmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualDisplayControllerTest {
    @Test
    fun onlyPublicPresentationScreensAreEligible() {
        assertTrue(DualDisplayController.isPresentationCandidate(1, "HDMI", 8, true))
        assertFalse(DualDisplayController.isPresentationCandidate(0, "HDMI", 8, true))
        assertFalse(DualDisplayController.isPresentationCandidate(3, "TaskVirtualDisplay@1", 8, true))
        assertFalse(DualDisplayController.isPresentationCandidate(1, "HDMI", 0, true))
        assertFalse(DualDisplayController.isPresentationCandidate(1, "HDMI", 12, true))
        assertFalse(DualDisplayController.isPresentationCandidate(1, "HDMI", 8, false))
    }
    @Test
    fun resolvesPreferredDisplayAndFallsBackToFirstConnectedDisplay() {
        assertEquals(1, DualDisplayController.resolveDisplayId(1, listOf(1, 2)))
        assertEquals(2, DualDisplayController.resolveDisplayId(1, listOf(2, 3)))
        assertNull(DualDisplayController.resolveDisplayId(1, emptyList()))
    }
}
