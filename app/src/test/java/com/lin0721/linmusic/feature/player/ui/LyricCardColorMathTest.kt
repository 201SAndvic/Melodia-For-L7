package com.lin0721.linmusic.feature.player.ui

import com.lin0721.linmusic.core.preferences.LyricCardColorSettings
import org.junit.Assert.*
import org.junit.Test

class LyricCardColorMathTest {
    private val bright = listOf(0.62f, 0.19f, 0.52f)
    @Test fun textTintOnGrayKeepsOriginalCoverTintIndependentOfBackgroundControls() {
        val gray = LyricCardColorSettings.NEUTRAL.copy(textFollowCover = true, brightness = 20, saturation = 0)
        assertEquals(lyricCardInactiveTextRgb(bright, LyricCardColorSettings.ORIGINAL), lyricCardInactiveTextRgb(bright, gray))
        assertEquals(List(3) { 40f / 255f }, adjustedLyricCardRgb(bright, gray))
        lyricCardInactiveTextRgb(bright, gray).zip(listOf(0.81f, 0.595f, 0.76f)).forEach { (a, b) -> assertEquals(b, a, 0.00001f) }
    }
    @Test fun textTintCanBeDisabledOrReducedWithoutChangingBackground() {
        val options = LyricCardColorSettings.SOFT.copy(textFollowCover = false)
        assertEquals(List(3) { 224f / 255f }, lyricCardInactiveTextRgb(bright, options))
        assertEquals(adjustedLyricCardRgb(bright, LyricCardColorSettings.SOFT), adjustedLyricCardRgb(bright, options))
        assertEquals(List(3) { 1f }, lyricCardInactiveTextRgb(bright, options.copy(textFollowCover = true, textTint = 0)))
    }
    @Test fun originalPreservesExistingCardFormulaResults() {
        adjustedLyricCardRgb(bright, LyricCardColorSettings.ORIGINAL).zip(bright).forEach { (a,b) -> assertEquals(b,a,0.00001f) }
    }
    @Test fun disabledAlwaysUsesThePreviousNeutralGrayRegardlessOfCover() {
        assertEquals(List(3) {40f/255f}, adjustedLyricCardRgb(bright, LyricCardColorSettings.NEUTRAL))
        assertEquals(List(3) {40f/255f}, adjustedLyricCardRgb(listOf(0f,1f,0f), LyricCardColorSettings.NEUTRAL))
        assertEquals(List(3) {40f/255f}, adjustedLyricCardRgb(bright, LyricCardColorSettings.NEUTRAL.copy(textFollowCover = true)))
    }
    @Test fun upperLimitDimsBrightColorsWithoutChangingHueOrRaisingDarkOnes() {
        val options = LyricCardColorSettings(limitBrightness=true,maxBrightness=30)
        val result = adjustedLyricCardRgb(bright, options)
        assertEquals(0.3f,result.maxOrNull()!!,0.00001f)
        assertEquals(bright[1]/bright[0],result[1]/result[0],0.00001f)
        val dark = listOf(0.1f,0.02f,0.08f)
        adjustedLyricCardRgb(dark, options).zip(dark).forEach { (a,b) -> assertEquals(b,a,0.00001f) }
    }
    @Test fun desaturationProducesNeutralGrayAndBrightnessKeepsBlackBlack() {
        val gray = adjustedLyricCardRgb(bright,LyricCardColorSettings(saturation=0))
        assertEquals(gray[0],gray[1],0f)
        assertEquals(gray[1],gray[2],0f)
        assertEquals(listOf(0f,0f,0f),adjustedLyricCardRgb(listOf(0f,0f,0f),LyricCardColorSettings.SOFT))
    }
    @Test fun softPresetCapsBrightColorsAndDoesNotCreateOutOfGamutChannels() {
        for (r in 0..10) for (g in 0..10) for (b in 0..10) {
            val result=adjustedLyricCardRgb(listOf(r/10f,g/10f,b/10f),LyricCardColorSettings.SOFT)
            assertTrue(result.all {it in 0f..0.35001f})
        }
    }
    @Test fun boostedSaturationClampsHsvSWithoutDistortingRelativeHue() {
        val options = LyricCardColorSettings(saturation=150)
        val result = adjustedLyricCardRgb(bright,options)
        assertEquals(0f,result.minOrNull()!!,0.00001f)
        assertEquals((bright[0]-bright[2])/(bright[0]-bright[1]),
            (result[0]-result[2])/(result[0]-result[1]),0.00001f)
    }
}
