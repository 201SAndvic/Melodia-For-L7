package com.lin0721.linmusic.core.player.external

import android.os.Bundle
import androidx.media3.common.MediaMetadata
import org.junit.Assert.*
import org.junit.Test

class Media3LyricMetadataEqualityTest {
    @Test fun changedLyricExtrasAreStillIgnoredByMetadataEquality() {
        // Media3 1.11 uses Android TextUtils.equals; JVM returnDefaultValues stubs
        // always return false. This invariant must run on a real Android runtime.
        val firstExtras = Bundle().apply { putString("lyricInfo", "original") }
        val secondExtras = Bundle().apply { putString("lyricInfo", "translation") }
        assertNotSame(firstExtras, secondExtras)
        val original = MediaMetadata.Builder().setTitle("Same song").setExtras(firstExtras).build()
        val translated = original.buildUpon().setExtras(secondExtras).build()
        assertEquals(original, translated)
        assertNotEquals(original, translated.buildUpon().setTitle("Different song").build())
    }
}
