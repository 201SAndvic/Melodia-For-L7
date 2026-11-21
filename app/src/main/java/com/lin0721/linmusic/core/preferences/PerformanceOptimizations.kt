package com.lin0721.linmusic.core.preferences

enum class PerformanceOptimization(val key: String) {
    LINE_HIGHLIGHT("perf_line_highlight"),
    STATIC_LYRIC_SCALE("perf_static_lyric_scale"),
    SLOW_PROGRESS("perf_slow_progress"),
    SUSPEND_COVERED_PLAYER("perf_suspend_covered_player"),
    SKIP_COMMENTS("perf_skip_comments"),
    SKIP_WIKI("perf_skip_wiki"),
    SKIP_ARTISTS("perf_skip_artists"),
    SIMPLE_DECORATIONS("perf_simple_decorations"),
    DISABLE_COVER_GESTURE_ANIMATION("perf_disable_cover_gesture_animation")
}

data class PlayerCardLoadPolicy(val comments: Boolean, val wiki: Boolean, val artists: Boolean)

/** Each option is independent. The old switch only supplies defaults during upgrade. */
data class PerformanceOptimizations(val enabledOptions: Set<PerformanceOptimization> = emptySet()) {
    fun enabled(option: PerformanceOptimization): Boolean = option in enabledOptions

    fun with(option: PerformanceOptimization, enabled: Boolean): PerformanceOptimizations =
        copy(enabledOptions = if (enabled) enabledOptions + option else enabledOptions - option)

    val cardLoadPolicy: PlayerCardLoadPolicy get() = PlayerCardLoadPolicy(
        comments = !enabled(PerformanceOptimization.SKIP_COMMENTS),
        wiki = !enabled(PerformanceOptimization.SKIP_WIKI),
        artists = !enabled(PerformanceOptimization.SKIP_ARTISTS)
    )

    fun filterCards(layout: List<FullPlayerCardSetting>): List<FullPlayerCardSetting> = layout.filter {
        when (it.card) {
            FullPlayerCard.COMMENTS_PREVIEW -> cardLoadPolicy.comments
            FullPlayerCard.SONG_DETAIL -> cardLoadPolicy.wiki
            FullPlayerCard.ABOUT_ARTIST, FullPlayerCard.ARTIST_ALBUMS, FullPlayerCard.SIMILAR_ARTISTS -> cardLoadPolicy.artists
            FullPlayerCard.LYRICS -> true
        }
    }

    companion object {
        fun fromStored(legacyEnabled: Boolean, overrides: Map<PerformanceOptimization, Boolean>): PerformanceOptimizations =
            PerformanceOptimizations(PerformanceOptimization.entries.filter {
                overrides[it] ?: legacyEnabled
            }.toSet())
    }
}
