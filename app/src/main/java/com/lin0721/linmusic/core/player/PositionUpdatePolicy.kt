package com.lin0721.linmusic.core.player

internal fun positionIntervalFor(lyricsVisible: Boolean, slowProgress: Boolean): Long = when {
    !lyricsVisible -> 1000L
    slowProgress -> 200L
    else -> 50L
}
