package com.lin0721.linmusic.core.preferences

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.booleanPreferencesKey
import org.junit.Assert.*
import org.junit.Test

class LyricCardColorSettingsTest {
    @Test fun grayBackgroundCanFollowCoverTextIndependentlyAndPersistPerDisplay() {
        val data = mutablePreferencesOf()
        data.writeLyricCardColors(true, LyricCardColorSettings.NEUTRAL.copy(textTint = 55))
        data.updateLyricCardColors(true, LyricCardColorField.TEXT_FOLLOW_COVER, 1)
        val saved = data.readDisplayLyricCardColors()
        assertFalse(saved.passenger.enabled)
        assertTrue(saved.passenger.textFollowCover)
        assertEquals(55, saved.passenger.textTint)
        assertEquals(LyricCardColorSettings.NEUTRAL, saved.main)
        data.updateLyricCardColors(true, LyricCardColorField.TEXT_FOLLOW_COVER, 0)
        assertEquals(LyricCardColorSettings.NEUTRAL.copy(textTint = 55), data.readDisplayLyricCardColors().passenger)
    }
    @Test fun oldStoredColorsDefaultToOriginalTextTintAndNeutralPresetIsNeutral() {
        assertTrue(decodeLyricCardColors("""{"enabled":true,"textTint":40}""").textFollowCover)
        assertFalse(LyricCardColorSettings.NEUTRAL.textFollowCover)
        assertTrue(LyricCardColorSettings.ORIGINAL.textFollowCover)
    }
    @Test fun missingPreferencesUseDeepGrayOnBothScreensButSavedColorsSurviveUpgrade() {
        val data = mutablePreferencesOf()
        val deepGray = LyricCardColorSettings.NEUTRAL
        assertEquals(DisplayLyricCardColors(deepGray, deepGray), data.readDisplayLyricCardColors())
        data.writeLyricCardColors(false, LyricCardColorSettings.ORIGINAL)
        assertEquals(DisplayLyricCardColors(LyricCardColorSettings.ORIGINAL, deepGray), data.readDisplayLyricCardColors())
    }
    @Test fun passengerChangesNeverOverwriteMainAndOtherSettings() {
        val unrelated = booleanPreferencesKey("dual_display_enabled")
        val data = mutablePreferencesOf(unrelated to true)
        data.writeLyricCardColors(false, LyricCardColorSettings.SOFT)
        data.updateLyricCardColors(true, LyricCardColorField.ENABLED, 0)
        assertEquals(LyricCardColorSettings.SOFT, data.readDisplayLyricCardColors().main)
        assertFalse(data.readDisplayLyricCardColors().passenger.enabled)
        assertEquals(true, data[unrelated])
    }
    @Test fun disablingKeepsCustomValuesAndEnablingRestoresThem() {
        val data = mutablePreferencesOf()
        data.writeLyricCardColors(true, LyricCardColorSettings.SOFT)
        data.updateLyricCardColors(true, LyricCardColorField.ENABLED, 0)
        data.updateLyricCardColors(true, LyricCardColorField.ENABLED, 1)
        assertEquals(LyricCardColorSettings.SOFT, data.readDisplayLyricCardColors().passenger)
    }
    @Test fun fieldUpdatesDoNotLoseOtherControls() {
        val data = mutablePreferencesOf()
        data.updateLyricCardColors(false, LyricCardColorField.BRIGHTNESS, 60)
        data.updateLyricCardColors(false, LyricCardColorField.SATURATION, 40)
        assertEquals(60, data.readDisplayLyricCardColors().main.brightness)
        assertEquals(40, data.readDisplayLyricCardColors().main.saturation)
        assertEquals(LyricCardColorSettings.NEUTRAL, data.readDisplayLyricCardColors().passenger)
    }
    @Test fun outOfRangeAndBrokenStoredDataFallBackSafely() {
        assertEquals(LyricCardColorSettings.NEUTRAL, decodeLyricCardColors("broken"))
        val value = decodeLyricCardColors("""{"brightness":999,"saturation":-50,"maxBrightness":999,"gradientStrength":-1,"textTint":500,"future":true}""")
        assertEquals(100, value.brightness)
        assertEquals(0, value.saturation)
        assertEquals(65, value.maxBrightness)
        assertEquals(0, value.gradientStrength)
        assertEquals(100, value.textTint)
    }
    @Test fun displayRoleIsIndependentOfLayoutDimensions() {
        val colors = DisplayLyricCardColors(main = LyricCardColorSettings.SOFT, passenger = LyricCardColorSettings.NEUTRAL)
        assertEquals(LyricCardColorSettings.SOFT, colors.forDisplay(false))
        assertEquals(LyricCardColorSettings.NEUTRAL, colors.forDisplay(true))
    }
}
