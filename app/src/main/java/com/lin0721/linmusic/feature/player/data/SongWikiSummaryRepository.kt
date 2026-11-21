package com.lin0721.linmusic.feature.player.data

import com.lin0721.linmusic.core.network.SharedResultCache
import com.lin0721.linmusic.core.network.WeakNetworkCache
import com.lin0721.linmusic.core.network.apiFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withTimeoutOrNull

/** Reuse one summary request for both the wiki UI and LyricInfo language selection.
 * No creators/detail request, language model, progress polling or display-specific copy. */
class SongWikiSummaryRepository(private val api: PlayerApi, private val cache: WeakNetworkCache? = null) {
    private val shared = SharedResultCache<Long, SongWikiSummaryResponse>(capacity = 32, ttlMs = 24 * 60 * 60 * 1000L,
        cacheValue = { it.languageLabels().isNotBlank() })

    suspend fun get(songId: Long): Result<SongWikiSummaryResponse> = shared.get(songId) {
        withTimeoutOrNull(8_000L) {
            val source = {
                apiFlow(request = { api.getSongWikiSummary(SongWikiSummaryRequest(songId)) },
                    isSuccess = { it.isSuccess }, code = { it.code }, transform = { it })
            }
            // A stale cache hit can be followed by the refreshed language response.
            // first() permanently froze the stale label in the 24-hour memory cache.
            (cache?.cached("wiki_summary:$songId", source = source) ?: source()).last()
        } ?: Result.failure(java.io.IOException("Song language request timed out"))
    }
}

internal fun SongWikiSummaryResponse.languageLabels(): String = data?.blocks.orEmpty()
    .filter { it.code == "SONG_PLAY_ABOUT_SONG_BASIC" }.flatMap { it.creatives }
    .filter { it.creativeType == "language" }.flatMap { it.uiElement?.textLinks.orEmpty() }
    .map { it.text.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" / ")
