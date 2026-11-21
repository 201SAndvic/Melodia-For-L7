package com.lin0721.linmusic.feature.player.ui

internal fun modulateInactiveLyricAlpha(current: Boolean, alpha: Float): Boolean = !current && alpha < 0.999f
internal fun needsKaraokeOffscreen(layoutReady: Boolean, featheredMask: Boolean): Boolean = layoutReady && featheredMask
