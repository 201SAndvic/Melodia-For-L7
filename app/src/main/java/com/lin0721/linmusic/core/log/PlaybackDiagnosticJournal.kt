package com.lin0721.linmusic.core.log

import java.io.File

/** IO-thread-only journal. Deliberately independent of AppLogger/performance logs. */
internal class PlaybackDiagnosticJournal(private val directory: File, private val maxBytes: Int = 64 * 1024) {
    private val current get() = File(directory, "playback-current.txt")
    private val previous get() = File(directory, "playback-previous.txt")
    fun append(line: String) {
        directory.mkdirs()
        val text = line.take(1800) + "\n"
        if (current.length() + text.toByteArray(Charsets.UTF_8).size > maxBytes) {
            previous.delete()
            if (!current.renameTo(previous)) current.delete()
        }
        current.appendText(text, Charsets.UTF_8)
    }
    fun read(): String = listOf(previous, current).filter { it.isFile }.joinToString("") { it.readText(Charsets.UTF_8) }
    fun clear() { listOf(previous, current).forEach { if (it.exists()) check(it.delete()) } }
}

internal data class PlaybackHealthSample(val elapsed: Long, val position: Long, val buffer: Long,
    val state: Int, val ready: Boolean, val playing: Boolean, val suppression: Int,
    val networkBytes: Long, val cachedBytes: Long, val cpuMs: Long,
    val duration: Long = -1L)

internal fun needsPlaybackContext(event: String): Boolean = event in setOf("audio_underrun", "rebuffer_start",
    "audio_load_error", "player_error", "audio_sink_error", "audio_codec_error", "main_delay", "main_stall",
    "sampler_gap", "buffer_low", "user_heard_stutter")

/** Reserve a bounded burst allowance for evidence that commonly arrives at playback start. */
internal fun shouldWritePlaybackEvent(event: String, ordinal: Int): Boolean = ordinal <= 12 ||
    (ordinal <= 32 && (needsPlaybackContext(event) || event in setOf(
        "user_heard_stutter_after", "audio_decoder_initialized", "audio_decoder_released",
        "audio_decoder_counters", "audio_output_initialized", "audio_output_released",
        "audio_session", "audio_position_advancing", "audio_output_devices_added", "audio_output_devices_removed")))

/** Keeps only ten seconds of context in memory. Normal samples never reach disk. */
internal class PlaybackHealthWindow {
    private val samples = ArrayDeque<PlaybackHealthSample>()
    private var low = false
    fun reset() { samples.clear(); low = false }
    fun add(sample: PlaybackHealthSample): String? {
        samples.addLast(sample)
        while (samples.size > 10) samples.removeFirst()
        // Reaching the known song end is not a starvation event. Unknown/live durations
        // still use the ordinary low-buffer check; never guess that they are at EOF.
        val nearEnd = sample.duration > 0 && sample.position >= 0 &&
            sample.position >= (sample.duration - 1000).coerceAtLeast(0)
        val danger = sample.state == 3 && sample.ready && sample.playing &&
            sample.buffer in 0..499 && !nearEnd
        val event = if (danger && !low) "buffer_low" else null
        low = danger
        return event
    }
    fun context(): String = samples.joinToString(";") {
        "${it.elapsed}:${it.position}/${it.buffer}/${it.state}/${if (it.playing) 1 else 0}/${it.networkBytes}/${it.cachedBytes}/${it.cpuMs}"
    }
}

/** One bounded five-second follow-up per manual marker; repeated taps cannot extend it. */
internal class PlaybackPostMarkerWindow {
    private var markedAt: Long? = null
    private val samples = ArrayList<PlaybackHealthSample>(6)
    fun start(at: Long) { if (markedAt == null) { markedAt = at; samples.clear() } }
    fun reset() { markedAt = null; samples.clear() }
    fun add(sample: PlaybackHealthSample): String? {
        val start = markedAt ?: return null
        if (sample.elapsed < start) return null
        if (samples.size < 6) samples.add(sample)
        return if (sample.elapsed - start >= 5000 || samples.size >= 6) finish("complete") else null
    }
    fun finish(reason: String): String? {
        val start = markedAt ?: return null
        val rows = samples.joinToString(";") {
            "${it.elapsed}:${it.position}/${it.buffer}/${it.state}/${if (it.playing) 1 else 0}/${it.networkBytes}/${it.cachedBytes}/${it.cpuMs}"
        }
        reset()
        return "markerT=$start reason=$reason samples=[$rows]"
    }
}
