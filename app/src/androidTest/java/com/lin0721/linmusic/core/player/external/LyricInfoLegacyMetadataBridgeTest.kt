package com.lin0721.linmusic.core.player.external

import android.content.Context
import android.media.session.MediaController
import android.os.Bundle
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LyricInfoLegacyMetadataBridge
import androidx.media3.session.MediaSession
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Device test: verifies Android's actual platform MediaController receives a
 * same-song lyricInfo change despite MediaMetadata.equals ignoring extras.
 * No music, network requests or ADB/car-specific services are required. */
@UnstableApi
class LyricInfoLegacyMetadataBridgeTest {
    @Test fun platformSessionGetsTranslationAndVisualBlankWithoutChangingTracks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context: Context = instrumentation.targetContext
            val engine = ExoPlayer.Builder(context).build()
            var session: MediaSession? = null
            try {
                val original = MediaMetadata.Builder().setTitle("Same song").setArtist("Same artist").setAlbumTitle("Same album")
                    .setExtras(Bundle().apply { putString("lyricInfo", "[00:01.000]Original\n") }).build()
                var output = original
                engine.setMediaItem(MediaItem.Builder().setMediaId("lyric-test").setUri("asset:///not-played")
                    .setMediaMetadata(original).build())
                val wrapper = object : ForwardingPlayer(engine) {
                    override fun getMediaMetadata(): MediaMetadata = output
                }
                val activeSession = MediaSession.Builder(context, wrapper).setId("lyric-info-platform-test").build()
                session = activeSession
                val controller = MediaController(context, activeSession.platformToken)
                LyricInfoLegacyMetadataBridge.refresh(activeSession)
                assertEquals("[00:01.000]Original\n", requireNotNull(controller.metadata).getString("lyricInfo"))
                output = original.buildUpon().setExtras(Bundle().apply { putString("lyricInfo", "[00:01.000]译文\n") }).build()
                assertEquals(original, output) // Rechecked against the actual Media3 1.11.1 dependency.
                assertTrue(LyricInfoLegacyMetadataBridge.refresh(activeSession))
                assertEquals("[00:01.000]译文\n", requireNotNull(controller.metadata).getString("lyricInfo"))
                assertEquals("Same song", requireNotNull(controller.metadata).getString("android.media.metadata.TITLE"))
                assertEquals("Same artist", requireNotNull(controller.metadata).getString("android.media.metadata.ARTIST"))
                assertEquals("Same album", requireNotNull(controller.metadata).getString("android.media.metadata.ALBUM"))
                assertEquals("lyric-test", wrapper.currentMediaItem!!.mediaId)
                assertFalse(LyricInfoLegacyMetadataBridge.refresh(activeSession)) // No redundant publication.
                output = original.buildUpon().setExtras(Bundle().apply {
                    putString("lyricInfo", "[00:00.000]${LyricInfoOutputPolicy.HUD_BLANK}\n")
                }).build()
                assertTrue(LyricInfoLegacyMetadataBridge.refresh(activeSession))
                assertTrue(requireNotNull(controller.metadata).getString("lyricInfo").orEmpty().contains(LyricInfoOutputPolicy.HUD_BLANK))
                assertFalse(engine.playWhenReady)
            } finally {
                session?.release()
                engine.release()
            }
        }
    }
}
