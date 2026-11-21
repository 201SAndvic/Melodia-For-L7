package com.lin0721.linmusic.core.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.lin0721.linmusic.core.log.PlaybackStutterDiagnostics as D
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@UnstableApi
internal class PlaybackStutterObserver(private val player: ExoPlayer, context: Context) : AnalyticsListener {
    private var bufferingAt = 0L
    private var wasReady = false
    private var observedGeneration = -1L
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private var routeJob: Job? = null
    private var decoder: String? = null
    private var sinkConfig: String? = null
    private fun devices(items: Array<AudioDeviceInfo>) = items.filter { it.isSink }.take(12)
        .joinToString(",") { "${it.id}:${it.type}" } // No device names/addresses or user labels.
    private fun recordDevices() {
        if (D.enabled) runCatching {
            D.event("audio_output_devices", "availableIdType=[${devices(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))}] actualRoute=unknown")
        }.onFailure { D.event("audio_output_devices", "unavailable=${it.javaClass.simpleName}") }
    }
    private fun detail(time: AnalyticsListener.EventTime) =
        "posMs=${time.currentPlaybackPositionMs} bufferMs=${time.totalBufferedDurationMs} state=${player.playbackState} ready=${player.playWhenReady} suppression=${player.playbackSuppressionReason}"

    fun bind(scope: CoroutineScope, capture: Flow<Boolean>) {
        player.addAnalyticsListener(this)
        routeJob = scope.launch {
            capture.distinctUntilChanged().collectLatest { enabled ->
                if (!enabled) return@collectLatest
                val callback = object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(added: Array<AudioDeviceInfo>) {
                        if (D.enabled && added.any { it.isSink }) { D.event("audio_output_devices_added", "idType=[${devices(added)}]"); recordDevices() }
                    }
                    override fun onAudioDevicesRemoved(removed: Array<AudioDeviceInfo>) {
                        if (D.enabled && removed.any { it.isSink }) { D.event("audio_output_devices_removed", "idType=[${devices(removed)}]"); recordDevices() }
                    }
                }
                var registered = false
                try {
                    audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
                    registered = true
                    awaitCancellation()
                } catch (e: SecurityException) {
                    D.event("audio_output_devices", "unavailable=SecurityException")
                } finally { if (registered) audioManager.unregisterAudioDeviceCallback(callback) }
            }
        }
        D.bindSnapshot {
            if (observedGeneration != D.generation) {
                observedGeneration = D.generation
                wasReady = player.playbackState == Player.STATE_READY
                bufferingAt = 0
                D.track(player.currentMediaItem?.mediaId)
                D.event("player_snapshot", "state=${player.playbackState} ready=${player.playWhenReady} suppression=${player.playbackSuppressionReason} speed=${player.playbackParameters.speed}")
                val format = player.audioFormat
                if (format != null) recordFormat(format)
                D.event("audio_offload", AudioOffloadRuntime.state.value.details)
                D.event("audio_decoder_snapshot", "name=${decoder ?: "unknown"}")
                D.event("audio_output_snapshot", "${sinkConfig ?: "config=unknown"} sessionId=${player.audioSessionId}")
                recordDevices()
            }
            D.sample(player.currentPosition, player.totalBufferedDuration, player.playbackState,
                player.playWhenReady, player.isPlaying, player.playbackSuppressionReason, player.duration)
        }
    }
    fun unbind() { D.event("player_released"); routeJob?.cancel(); routeJob = null; player.removeAnalyticsListener(this); D.bindSnapshot(null) }
    override fun onAudioDecoderInitialized(time: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        decoder = decoderName
        if (D.enabled) D.event("audio_decoder_initialized", "name=$decoderName initMs=$initializationDurationMs eventT=${time.realtimeMs} ${detail(time)}")
    }
    override fun onAudioDecoderReleased(time: AnalyticsListener.EventTime, decoderName: String) {
        if (D.enabled) D.event("audio_decoder_released", "name=$decoderName eventT=${time.realtimeMs} ${detail(time)}")
        decoder = null
    }
    override fun onAudioDisabled(time: AnalyticsListener.EventTime, counters: DecoderCounters) {
        if (D.enabled) D.event("audio_decoder_counters", "rendered=${counters.renderedOutputBufferCount} skipped=${counters.skippedOutputBufferCount} dropped=${counters.droppedBufferCount} eventT=${time.realtimeMs}")
    }
    override fun onAudioTrackInitialized(time: AnalyticsListener.EventTime, config: AudioSink.AudioTrackConfig) {
        sinkConfig = "encoding=${config.encoding} rate=${config.sampleRate} channelsMask=${config.channelConfig} sinkBytes=${config.bufferSize} offload=${config.offload} tunneling=${config.tunneling}"
        if (D.enabled) D.event("audio_output_initialized", "$sinkConfig eventT=${time.realtimeMs} ${detail(time)}")
    }
    override fun onAudioTrackReleased(time: AnalyticsListener.EventTime, config: AudioSink.AudioTrackConfig) {
        if (D.enabled) D.event("audio_output_released", "${sinkConfig ?: "config=unknown"} eventT=${time.realtimeMs} ${detail(time)}")
        sinkConfig = null
    }
    override fun onAudioSessionIdChanged(time: AnalyticsListener.EventTime, audioSessionId: Int) {
        if (D.enabled) D.event("audio_session", "sessionId=$audioSessionId eventT=${time.realtimeMs}")
    }
    override fun onAudioPositionAdvancing(time: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) {
        if (D.enabled) D.event("audio_position_advancing", "eventT=${time.realtimeMs} ${detail(time)}")
    }
    override fun onMediaItemTransition(time: AnalyticsListener.EventTime, item: MediaItem?, reason: Int) {
        bufferingAt = 0; wasReady = false
        if (D.enabled) { D.track(item?.mediaId); D.event("track_transition", "reason=$reason ${detail(time)}") }
    }
    override fun onPlaybackStateChanged(time: AnalyticsListener.EventTime, state: Int) {
        if (!D.enabled) return
        if (state == Player.STATE_BUFFERING) {
            bufferingAt = time.realtimeMs
            D.event(if (wasReady) "rebuffer_start" else "initial_buffer", detail(time))
        } else {
            if (bufferingAt != 0L) D.event("buffer_end", "durationMs=${time.realtimeMs - bufferingAt} ${detail(time)}")
            bufferingAt = 0
            if (state == Player.STATE_READY) wasReady = true
            D.event("state", "value=$state ${detail(time)}")
        }
    }
    override fun onAudioUnderrun(time: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
        if (D.enabled) D.event("audio_underrun", "sinkBytes=$bufferSize sinkMs=$bufferSizeMs sinceFeedMs=$elapsedSinceLastFeedMs ${detail(time)}")
    }
    override fun onPlayWhenReadyChanged(time: AnalyticsListener.EventTime, ready: Boolean, reason: Int) {
        if (D.enabled) D.event("play_request", "ready=$ready reason=$reason focusLoss=${reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS} ${detail(time)}")
    }
    override fun onPlaybackSuppressionReasonChanged(time: AnalyticsListener.EventTime, reason: Int) {
        if (D.enabled) D.event("suppressed", "reason=$reason ${detail(time)}")
    }
    override fun onIsPlayingChanged(time: AnalyticsListener.EventTime, playing: Boolean) {
        if (D.enabled) D.event("playing", "value=$playing ${detail(time)}")
    }
    private fun recordFormat(format: Format) {
        D.event("audio_format", "mime=${format.sampleMimeType} rate=${format.sampleRate} channels=${format.channelCount} bitrate=${format.bitrate}")
    }
    override fun onAudioInputFormatChanged(time: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        if (D.enabled) recordFormat(format)
    }
    override fun onPlayerError(time: AnalyticsListener.EventTime, error: PlaybackException) {
        if (D.enabled) D.event("player_error", "code=${error.errorCode} type=${error.javaClass.simpleName} cause=${error.cause?.javaClass?.simpleName} ${detail(time)}")
    }
    override fun onAudioSinkError(time: AnalyticsListener.EventTime, error: Exception) {
        if (D.enabled) D.event("audio_sink_error", "type=${error.javaClass.simpleName} ${detail(time)}")
    }
    override fun onAudioCodecError(time: AnalyticsListener.EventTime, error: Exception) {
        if (D.enabled) D.event("audio_codec_error", "type=${error.javaClass.simpleName} ${detail(time)}")
    }
    override fun onLoadError(time: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData, error: IOException, wasCanceled: Boolean) {
        if (D.enabled && data.dataType == C.DATA_TYPE_MEDIA) D.event("audio_load_error",
            "type=${error.javaClass.simpleName} http=${(error as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0} canceled=$wasCanceled loadMs=${info.loadDurationMs} bytes=${info.bytesLoaded} ${detail(time)}")
    }
    companion object {
        /** Counts bytes only; never stores DataSpec URI, request headers or keys. */
        val transfers = object : TransferListener {
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
            override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) { D.bytes(isNetwork, bytesTransferred) }
            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        }
    }
}
