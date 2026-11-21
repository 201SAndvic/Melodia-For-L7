package com.lin0721.linmusic.feature.player.ui

import org.junit.Assert.*
import org.junit.Test

class LyricCompositingPolicyTest {
    @Test fun onlyNonOverlappingInactiveRowsUseAlphaModulation() {
        assertTrue(modulateInactiveLyricAlpha(false, 0.3f))
        assertFalse(modulateInactiveLyricAlpha(true, 0.3f))
        assertFalse(modulateInactiveLyricAlpha(false, 1f))
    }
    @Test fun featheredMaskAlwaysRetainsIsolationAfterMeasurement() {
        assertTrue(needsKaraokeOffscreen(true, true))
        assertFalse(needsKaraokeOffscreen(false, true))
        assertFalse(needsKaraokeOffscreen(true, false))
    }
}
