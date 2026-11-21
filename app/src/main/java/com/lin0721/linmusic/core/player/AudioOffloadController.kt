package com.lin0721.linmusic.core.player

import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import androidx.media3.common.*
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.lin0721.linmusic.core.log.PlaybackStutterDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioOffloadStatus(val requested: Boolean = false, val applied: Boolean = false,
    val supported: Boolean? = null, val active: Boolean? = null, val sleeping: Boolean = false, val failed: Boolean = false) {
    val details get() = "requested=$requested applied=$applied supported=$supported active=$active sleeping=$sleeping failed=$failed"
}

object AudioOffloadRuntime {
    internal val mutableState = MutableStateFlow(AudioOffloadStatus())
    val state = mutableState.asStateFlow()
}

internal fun canApplyOffloadNow(playWhenReady: Boolean, playbackState: Int, changingTrack: Boolean = false) =
    !playWhenReady || playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED || changingTrack

/** Uses upstream's AudioOffloadPreferences on our existing single ExoPlayer.
 * No CrossfadePlayer/second engine or Media3 upgrade. ENABLED permits normal fallback.
 * During music, defer reconfiguration to pause or a new track to avoid creating a glitch. */
@UnstableApi
internal class AudioOffloadController(private val player: ExoPlayer) : Player.Listener,
    ExoPlayer.AudioOffloadListener, AnalyticsListener {
    private var requested = false
    private var applied = false
    private var supported: Boolean? = null
    private var active: Boolean? = null
    private var sleeping = false
    private var failed = false
    init { player.addListener(this); player.addAudioOffloadListener(this); player.addAnalyticsListener(this) }
    fun request(value: Boolean) { requested = value; applyIfSafe(); publish() }
    private fun applyIfSafe(changingTrack: Boolean = false) {
        if (requested == applied || !canApplyOffloadNow(player.playWhenReady, player.playbackState, changingTrack)) return
        try {
            val mode = if (requested) AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED else AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(AudioOffloadPreferences.Builder().setAudioOffloadMode(mode).build()).build()
            applied = requested
            active = null
            failed = false
        } catch (_: Exception) {
            failed = true
            // Keep the last applied path rather than stopping or recreating audio.
        }
    }
    private fun checkSupport() {
        val format = player.audioFormat
        supported = if (Build.VERSION.SDK_INT < 29 || format == null || format.channelCount <= 0 || format.sampleRate <= 0) null else runCatching {
            val encoding = MimeTypes.getEncoding(format.sampleMimeType ?: return@runCatching null, format.codecs)
            if (encoding == C.ENCODING_INVALID) return@runCatching null
            val audio = AudioFormat.Builder().setEncoding(encoding).setSampleRate(format.sampleRate)
                .setChannelMask(if (format.channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else if (format.channelCount == 2) AudioFormat.CHANNEL_OUT_STEREO else return@runCatching null).build()
            AudioManager.isOffloadedPlaybackSupported(audio, android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
        }.getOrNull()
    }
    private fun publish() {
        val value = AudioOffloadStatus(requested, applied, supported, active, sleeping, failed)
        if (AudioOffloadRuntime.mutableState.value != value) {
            AudioOffloadRuntime.mutableState.value = value
            PlaybackStutterDiagnostics.event("audio_offload", value.details)
        }
    }
    override fun onMediaItemTransition(item: MediaItem?, reason: Int) { active = null; supported = null; sleeping = false; applyIfSafe(true); publish() }
    override fun onPlayWhenReadyChanged(value: Boolean, reason: Int) { applyIfSafe(); publish() }
    override fun onPlaybackStateChanged(state: Int) { applyIfSafe(); checkSupport(); publish() }
    override fun onSleepingForOffloadChanged(value: Boolean) { sleeping = value; publish() }
    override fun onOffloadedPlayback(value: Boolean) { active = value; checkSupport(); publish() }
    override fun onAudioTrackInitialized(time: AnalyticsListener.EventTime, config: AudioSink.AudioTrackConfig) {
        active = config.offload; checkSupport(); publish()
    }
    override fun onAudioTrackReleased(time: AnalyticsListener.EventTime, config: AudioSink.AudioTrackConfig) { active = null; sleeping = false; publish() }
    fun release() {
        player.removeListener(this); player.removeAudioOffloadListener(this); player.removeAnalyticsListener(this)
        AudioOffloadRuntime.mutableState.value = AudioOffloadStatus()
    }
}
