package com.lin0721.linmusic.core.player.data

import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class PlaybackAudioInfo(
    val url: String,
    val cacheKey: String? = null,
    val sizeBytes: Long = 0L,
    val trial: Boolean = false,
    val savedAt: Long = 0L
)

internal fun audioCacheKey(account: String, songId: Long, quality: String, item: SongUrlItem): String {
    // The same signed URL may change; quality alone does not identify the bytes.
    // Missing content identity falls back to the exact URL, never merges versions.
    val identity = item.md5?.takeIf { it.isNotBlank() } ?: item.url.orEmpty()
    val raw = "$account|$songId|$quality|${item.br}|${item.type}|${item.size}|${item.freeTrialInfo?.start}|${item.freeTrialInfo?.end}|$identity"
    return "melodia-v1:" + MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
}

internal fun canUseCompleteAudio(info: PlaybackAudioInfo, cachedEntireFile: Boolean): Boolean =
    !info.trial && info.sizeBytes > 0 && !info.cacheKey.isNullOrBlank() && cachedEntireFile
