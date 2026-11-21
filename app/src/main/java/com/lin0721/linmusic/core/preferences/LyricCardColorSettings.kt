package com.lin0721.linmusic.core.preferences

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

@Serializable
data class LyricCardColorSettings(
    val enabled: Boolean = true,
    val brightness: Int = 100,
    val saturation: Int = 100,
    val limitBrightness: Boolean = false,
    val maxBrightness: Int = 35,
    val gradientStrength: Int = 100,
    val textTint: Int = 100,
    val textFollowCover: Boolean = true
) {
    fun normalized() = copy(brightness = brightness.coerceIn(20, 100), saturation = saturation.coerceIn(0, 150),
        maxBrightness = maxBrightness.coerceIn(10, 65), gradientStrength = gradientStrength.coerceIn(0, 100),
        textTint = textTint.coerceIn(0, 100))

    fun update(field: LyricCardColorField, value: Int): LyricCardColorSettings = when (field) {
        LyricCardColorField.ENABLED -> copy(enabled = value != 0)
        LyricCardColorField.BRIGHTNESS -> copy(brightness = value)
        LyricCardColorField.SATURATION -> copy(saturation = value)
        LyricCardColorField.LIMIT_BRIGHTNESS -> copy(limitBrightness = value != 0)
        LyricCardColorField.MAX_BRIGHTNESS -> copy(maxBrightness = value)
        LyricCardColorField.GRADIENT_STRENGTH -> copy(gradientStrength = value)
        LyricCardColorField.TEXT_TINT -> copy(textTint = value)
        LyricCardColorField.TEXT_FOLLOW_COVER -> copy(textFollowCover = value != 0)
    }.normalized()

    companion object {
        val ORIGINAL = LyricCardColorSettings()
        val SOFT = LyricCardColorSettings(brightness = 70, saturation = 60, limitBrightness = true,
            maxBrightness = 35, gradientStrength = 50, textTint = 40)
        val NEUTRAL = LyricCardColorSettings(enabled = false, textFollowCover = false)
    }
}

enum class LyricCardColorField { ENABLED, BRIGHTNESS, SATURATION, LIMIT_BRIGHTNESS, MAX_BRIGHTNESS, GRADIENT_STRENGTH, TEXT_TINT, TEXT_FOLLOW_COVER }

data class DisplayLyricCardColors(
    val main: LyricCardColorSettings = LyricCardColorSettings.NEUTRAL,
    val passenger: LyricCardColorSettings = LyricCardColorSettings.NEUTRAL
) {
    fun forDisplay(isPassenger: Boolean) = if (isPassenger) passenger else main
}

private val mainCardColorKey = stringPreferencesKey("main_lyric_card_colors_v1")
private val passengerCardColorKey = stringPreferencesKey("passenger_lyric_card_colors_v1")
private val cardColorJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal fun decodeLyricCardColors(raw: String?): LyricCardColorSettings =
    raw?.let { runCatching { cardColorJson.decodeFromString<LyricCardColorSettings>(it).normalized() }.getOrNull() }
        ?: LyricCardColorSettings.NEUTRAL

internal fun Preferences.readDisplayLyricCardColors() = DisplayLyricCardColors(
    decodeLyricCardColors(this[mainCardColorKey]), decodeLyricCardColors(this[passengerCardColorKey]))

internal fun MutablePreferences.writeLyricCardColors(isPassenger: Boolean, settings: LyricCardColorSettings) {
    this[if (isPassenger) passengerCardColorKey else mainCardColorKey] =
        cardColorJson.encodeToString(LyricCardColorSettings.serializer(), settings.normalized())
}

internal fun MutablePreferences.updateLyricCardColors(isPassenger: Boolean, field: LyricCardColorField, value: Int) {
    writeLyricCardColors(isPassenger, readDisplayLyricCardColors().forDisplay(isPassenger).update(field, value))
}
