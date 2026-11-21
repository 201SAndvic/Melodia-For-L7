package com.lin0721.linmusic.core.preferences

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.*
import org.junit.Test

class LyricInfoOutputSettingsTest {
    @Test fun defaultsAreChineseEnglishOriginalAndOtherLanguagesTranslatedWithJsonCompatibility() {
        val value = mutablePreferencesOf().readLyricInfoOutputSettings()
        assertEquals(LyricInfoLanguageMode.AUTO, value.mode)
        assertEquals(LyricInfoFormat.JSON, value.format)
        assertFalse("zh" in value.translatedLanguages)
        assertFalse("en" in value.translatedLanguages)
        assertTrue("de" in value.translatedLanguages)
    }
    @Test fun individualUpdatesPersistWithoutOverwritingOtherFieldsOrFullscreenPreferences() {
        val fullscreen = stringPreferencesKey("full_screen_lyric_secondary_mode")
        val data = mutablePreferencesOf(fullscreen to "roma")
        data.updateLyricInfoOutputSettings { it.copy(format = LyricInfoFormat.HUD_LRC) }
        data.updateLyricInfoOutputSettings { it.forLanguage("en", true) }
        data.updateLyricInfoOutputSettings { it.copy(clearInstrumental = false) }
        val restored = data.readLyricInfoOutputSettings()
        assertEquals(LyricInfoFormat.HUD_LRC, restored.format)
        assertTrue("en" in restored.translatedLanguages)
        assertFalse(restored.clearInstrumental)
        assertEquals("roma", data[fullscreen])
    }
    @Test fun malformedOrUnknownEnumSettingsFallBackSafely() {
        val key = stringPreferencesKey("lyric_info_output_v1")
        assertEquals(LyricInfoOutputSettings(), mutablePreferencesOf(key to "broken").readLyricInfoOutputSettings())
        assertEquals(LyricInfoOutputSettings(), mutablePreferencesOf(key to """{"mode":"future"}""").readLyricInfoOutputSettings())
    }
}
