package com.lin0721.linmusic.feature.player.ui

/** Portrait keeps its existing hero geometry. A short wide player must reach
 * the transparent stops within its own viewport, revealing the black host. */
internal fun collapsedBackdropGradientEndY(
    useTwoPane: Boolean,
    viewportHeightPx: Float,
    portraitEndPx: Float
): Float = if (useTwoPane) viewportHeightPx.coerceAtLeast(1f) else portraitEndPx
