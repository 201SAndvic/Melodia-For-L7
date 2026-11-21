package com.lin0721.linmusic.core.player.data

import org.junit.Assert.*
import org.junit.Test

class PlaybackAudioInfoTest {
    private val source = SongUrlItem(id=1, url="https://a/song?token=one", br=320000, size=100, md5="same-content", type="mp3")

    @Test fun signedUrlChangesReuseIdenticalAudioButQualityAccountOrBytesDoNotMix() {
        val key = audioCacheKey("A", 1, "standard", source)
        assertEquals(key, audioCacheKey("A", 1, "standard", source.copy(url="https://b/song?token=two")))
        assertNotEquals(key, audioCacheKey("B", 1, "standard", source))
        assertNotEquals(key, audioCacheKey("A", 1, "lossless", source))
        assertNotEquals(key, audioCacheKey("A", 1, "standard", source.copy(md5="other")))
        assertNotEquals(key, audioCacheKey("A", 1, "standard", source.copy(freeTrialInfo=FreeTrialInfo(0,30))))
    }

    @Test fun unknownAudioIdentityDoesNotMergeDifferentUrls() {
        val key = audioCacheKey("A", 1, "standard", source.copy(md5=null))
        assertNotEquals(key, audioCacheKey("A", 1, "standard", source.copy(md5=null, url="https://other/file")))
    }

    @Test fun incompleteUnknownSizeOrTrialAudioCannotBeClaimedFullyOfflinePlayable() {
        val info = PlaybackAudioInfo("https://url", "key", 100)
        assertTrue(canUseCompleteAudio(info, true))
        assertFalse(canUseCompleteAudio(info, false))
        assertFalse(canUseCompleteAudio(info.copy(sizeBytes=0), true))
        assertFalse(canUseCompleteAudio(info.copy(trial=true), true))
        assertFalse(canUseCompleteAudio(info.copy(cacheKey=null), true))
    }
}
