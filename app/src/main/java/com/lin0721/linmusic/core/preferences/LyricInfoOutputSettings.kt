package com.lin0721.linmusic.core.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class LyricInfoLanguageMode { AUTO, ORIGINAL, TRANSLATION }

@Serializable
enum class LyricInfoFormat { JSON, HUD_LRC }

@Serializable
data class LyricInfoOutputSettings(
    val mode: LyricInfoLanguageMode = LyricInfoLanguageMode.AUTO,
    val format: LyricInfoFormat = LyricInfoFormat.JSON,
    val translatedLanguages: Set<String> = setOf("ja", "ko", "de", "ru", "fr", "es", "other"),
    val translateUnknown: Boolean = false,
    val clearInstrumental: Boolean = true,
    val filterCredits: Boolean = true
) {
    fun forLanguage(language: String, translate: Boolean) = copy(
        translatedLanguages = if (translate) translatedLanguages + language else translatedLanguages - language)
}

private val lyricInfoOutputKey = stringPreferencesKey("lyric_info_output_v1")
private val outputJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal fun Preferences.readLyricInfoOutputSettings(): LyricInfoOutputSettings =
    this[lyricInfoOutputKey]?.let {
        runCatching { outputJson.decodeFromString<LyricInfoOutputSettings>(it) }.getOrNull()
    } ?: LyricInfoOutputSettings()

internal fun MutablePreferences.updateLyricInfoOutputSettings(change: (LyricInfoOutputSettings) -> LyricInfoOutputSettings) {
    this[lyricInfoOutputKey] = outputJson.encodeToString(LyricInfoOutputSettings.serializer(), change(readLyricInfoOutputSettings()))
}
