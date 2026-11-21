package com.lin0721.linmusic.feature.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerBackdropGradientTest {
    @Test fun portraitKeepsItsOriginalFixedGradientGeometry() {
        val original = 1100f * (1440f / 745f)
        assertEquals(original, collapsedBackdropGradientEndY(false, 1920f, original), 0f)
    }
    @Test fun wideHeadUnitFadesToBlackBeforeTheBottomInsteadOfBeyondTheScreen() {
        val height = 1080f
        val end = collapsedBackdropGradientEndY(true, height, 1100f * (2880f / 1480f))
        assertEquals(height, end, 0f)
        assertTrue(end * 0.76f < height)
    }
    @Test fun resizedWideWindowsUseTheirActualDrawHeight() {
        assertEquals(720f, collapsedBackdropGradientEndY(true, 720f, 2140f), 0f)
        assertEquals(1400f, collapsedBackdropGradientEndY(true, 1400f, 2140f), 0f)
    }
    @Test fun zeroSizeBeforeLayoutDoesNotCreateInvalidGradient() {
        assertEquals(1f, collapsedBackdropGradientEndY(true, 0f, 2140f), 0f)
    }
}
