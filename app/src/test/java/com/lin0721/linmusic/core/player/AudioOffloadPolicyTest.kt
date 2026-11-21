package com.lin0721.linmusic.core.player

import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test

class AudioOffloadPolicyTest {
    @Test fun requestDoesNotReconfigureActivelyPlayingOrBufferingAudio() {
        assertFalse(canApplyOffloadNow(true, Player.STATE_READY))
        assertFalse(canApplyOffloadNow(true, Player.STATE_BUFFERING))
    }
    @Test fun pauseNextTrackOrIdlePermitApplyingAnExplicitRequest() {
        assertTrue(canApplyOffloadNow(false, Player.STATE_READY))
        assertTrue(canApplyOffloadNow(true, Player.STATE_READY, true))
        assertTrue(canApplyOffloadNow(true, Player.STATE_IDLE))
        assertTrue(canApplyOffloadNow(true, Player.STATE_ENDED))
    }
    @Test fun defaultStatusDoesNotClaimSupportOrHardwareActive() {
        val status = AudioOffloadStatus()
        assertFalse(status.requested)
        assertFalse(status.applied)
        assertNull(status.supported)
        assertNull(status.active)
    }
}
