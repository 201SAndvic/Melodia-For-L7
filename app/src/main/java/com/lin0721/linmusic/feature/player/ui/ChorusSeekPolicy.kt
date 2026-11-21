package com.lin0721.linmusic.feature.player.ui

import kotlin.math.abs

internal fun chorusFraction(start: Long?, duration: Long): Float? =
    start?.takeIf { duration > 0 && it > 0 && it < duration }?.let { it.toFloat() / duration }

internal data class ChorusSeek(val fraction: Float, val snapped: Boolean)
internal fun snapChorusSeek(raw: Float, chorus: Float?, width: Float, radius: Float): ChorusSeek {
    val safe = raw.coerceIn(0f, 1f)
    val snap = chorus != null && width > 0 && abs(safe - chorus) * width <= radius
    return ChorusSeek(if (snap) chorus!! else safe, snap)
}
