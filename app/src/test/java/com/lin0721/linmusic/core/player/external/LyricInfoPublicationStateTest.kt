package com.lin0721.linmusic.core.player.external

import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.preferences.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LyricInfoPublicationStateTest {
    private val settings = LyricInfoOutputSettings()
    private val lyrics = listOf(LyricLine(1000, text = "Ich liebe dich", translation = "我爱你"))
    private fun render(state: LyricInfoPublicationState, config: LyricInfoOutputSettings = settings) =
        JSONObject(state.build("Song", "Artist", "1", "Album", config)).getString("lyric")
    @Test fun coldSongDoesNotPublishAnOriginalBeforeItsDelayedLanguage() {
        val state = LyricInfoPublicationState()
        val token = state.beginTrack()
        assertTrue(render(state).contains(LyricInfoOutputPolicy.HUD_BLANK))
        state.acceptLyrics(token, lyrics)
        assertTrue(state.waiting(settings))
        assertFalse(render(state).contains("Ich liebe dich"))
        state.acceptLanguage(token, "德语")
        assertFalse(state.waiting(settings))
        assertTrue(render(state).contains("我爱你"))
    }
    @Test fun warmSongAndLanguageArrivingFirstProduceTheSameFirstRealLyrics() {
        val state = LyricInfoPublicationState()
        val token = state.beginTrack()
        state.acceptLanguage(token, "德语")
        assertTrue(state.waiting(settings))
        state.acceptLyrics(token, lyrics)
        assertTrue(render(state).contains("我爱你"))
    }
    @Test fun rapidAToBToARejectsAnOldSameSongCompletion() {
        val state = LyricInfoPublicationState()
        val firstA = state.beginTrack()
        val b = state.beginTrack()
        val secondA = state.beginTrack()
        assertFalse(state.acceptLanguage(firstA, "英语"))
        assertFalse(state.acceptLyrics(b, lyrics))
        assertTrue(state.acceptLyrics(secondA, lyrics))
        assertTrue(state.waiting(settings))
        assertTrue(state.acceptLanguage(secondA, "德语"))
        assertTrue(render(state).contains("我爱你"))
    }
    @Test fun timeoutResolvesToUserFallbackInsteadOfWaitingForever() {
        val state = LyricInfoPublicationState()
        val token = state.beginTrack()
        state.acceptLyrics(token, lyrics)
        state.acceptLanguage(token, "")
        assertFalse(state.waiting(settings))
        assertTrue(render(state).contains("Ich liebe dich"))
        assertTrue(render(state, settings.copy(translateUnknown = true)).contains("我爱你"))
    }
    @Test fun explicitModesAndNoTranslationDoNotWaitForWiki() {
        val state = LyricInfoPublicationState()
        val token = state.beginTrack()
        state.acceptLyrics(token, lyrics)
        assertTrue(render(state, settings.copy(mode = LyricInfoLanguageMode.TRANSLATION)).contains("我爱你"))
        assertTrue(render(state, settings.copy(mode = LyricInfoLanguageMode.ORIGINAL)).contains("Ich liebe dich"))
        state.acceptLyrics(token, lyrics.map { it.copy(translation = null) })
        assertFalse(state.waiting(settings))
    }
    @Test fun loadingIsAlwaysSilentEvenWhenUserRequestsInstrumentalMessages() {
        val state = LyricInfoPublicationState()
        state.beginTrack()
        val json = JSONObject(state.build("", "", "1", "", settings.copy(clearInstrumental = false)))
        assertFalse(json.getBoolean("noLyric"))
        assertTrue(json.getString("lyric").contains(LyricInfoOutputPolicy.HUD_BLANK))
    }
    @Test fun instrumentalDoesNotWaitAndNextSongCannotInheritOldLanguage() {
        val state = LyricInfoPublicationState()
        val token = state.beginTrack()
        state.acceptLanguage(token, "英语")
        state.acceptLyrics(token, listOf(LyricLine(0, text = "纯音乐")))
        assertFalse(state.waiting(settings))
        assertTrue(render(state).contains(LyricInfoOutputPolicy.HUD_BLANK))
        val next = state.beginTrack()
        state.acceptLyrics(next, lyrics)
        assertTrue(state.waiting(settings))
        state.acceptLanguage(next, "德语")
        assertTrue(render(state).contains("我爱你"))
    }
}
