package com.lin0721.linmusic.feature.player.ui

import com.lin0721.linmusic.core.preferences.LyricCardColorSettings
import kotlin.math.max

/** Text tint is independent of the background toggle and brightness controls. */
internal fun lyricCardInactiveTextRgb(vividRgb: List<Float>, settings: LyricCardColorSettings): List<Float> {
    if (!settings.textFollowCover) return List(3) { 224f / 255f }
    val strength = settings.normalized().textTint / 100f
    return vividRgb.map { 1f - (1f - it.coerceIn(0f, 1f)) * 0.5f * strength }
}

/** Apply user controls to the existing cover-derived colors, not to the palette
 * extraction algorithm. Multiplying HSV V preserves hue and existing dark colors. */
internal fun adjustedLyricCardRgb(rgb: List<Float>, settings: LyricCardColorSettings): List<Float> {
    val options = settings.normalized()
    if (!options.enabled) return List(3) { 40f / 255f }
    val maximum = rgb.maxOrNull()?.coerceIn(0f, 1f) ?: 0f
    val minimum = rgb.minOrNull()?.coerceIn(0f, 1f) ?: 0f
    val currentSaturation = if (maximum > 0f) (maximum - minimum) / maximum else 0f
    val targetSaturation = (currentSaturation * options.saturation / 100f).coerceIn(0f, 1f)
    val saturationScale = if (currentSaturation > 0f) targetSaturation / currentSaturation else 1f
    val saturated = rgb.map { (maximum - (maximum - it.coerceIn(0f, 1f)) * saturationScale).coerceIn(0f, 1f) }
    val dimmed = saturated.map { it * options.brightness / 100f }
    val peak = dimmed.maxOrNull() ?: 0f
    val cap = options.maxBrightness / 100f
    val factor = if (options.limitBrightness && peak > cap) cap / max(peak, 0.0001f) else 1f
    return dimmed.map { it * factor }
}
