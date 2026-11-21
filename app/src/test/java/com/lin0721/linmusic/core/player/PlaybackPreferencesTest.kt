package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPreferencesTest {
    @Test
    fun missingOrInvalidModeIsANormalDefaultNotAnException() {
        listOf(null, "", "unknown").forEach {
            assertEquals(PlayMode.LIST_LOOP, decodeSavedPlayMode(it))
        }
    }

    @Test
    fun validModesStillRoundTrip() {
        PlayMode.entries.forEach { assertEquals(it, decodeSavedPlayMode(it.name)) }
    }
}
