package com.lin0721.linmusic.core.player.external

import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.preferences.LyricInfoLanguageMode
import com.lin0721.linmusic.core.preferences.LyricInfoOutputSettings

/** Main-thread state. Never publish a provisional original before its language resolves.
 * Generation checks also reject late completions in A -> B -> A track changes. */
internal class LyricInfoPublicationState {
    var generation: Long = 0
        private set
    private var lines: List<LyricLine>? = null
    private var language: String? = null

    fun beginTrack(): Long {
        generation++
        lines = null
        language = null
        return generation
    }

    fun acceptLyrics(token: Long, value: List<LyricLine>): Boolean {
        if (token != generation) return false
        lines = value
        return true
    }

    fun acceptLanguage(token: Long, value: String): Boolean {
        if (token != generation) return false
        language = value // An empty resolved label is different from a pending request.
        return true
    }

    fun waiting(settings: LyricInfoOutputSettings): Boolean = lines == null ||
        (settings.mode == LyricInfoLanguageMode.AUTO && language == null &&
            lines.orEmpty().any { !it.translation.isNullOrBlank() } &&
            lines.orEmpty().none { LyricInfoOutputPolicy.isInstrumental(it.text) })

    fun build(songName: String, artist: String, songId: String, album: String, settings: LyricInfoOutputSettings): String {
        val pending = waiting(settings)
        return LyricInfoOutputPolicy.build(songName, artist, songId, album,
            if (pending) emptyList() else lines.orEmpty(), language.orEmpty(),
            if (pending) settings.copy(clearInstrumental = true) else settings)
    }
}
