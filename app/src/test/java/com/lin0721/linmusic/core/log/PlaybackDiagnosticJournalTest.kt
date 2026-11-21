package com.lin0721.linmusic.core.log

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlaybackDiagnosticJournalTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun onlyFaultsAndManualMarkersIncludeTenSecondContextNotNormalEvents() {
        listOf("state", "track", "audio_format", "playing", "settings", "capture_on").forEach { assertFalse(needsPlaybackContext(it)) }
        listOf("audio_underrun", "rebuffer_start", "main_stall", "user_heard_stutter").forEach { assertTrue(needsPlaybackContext(it)) }
    }
    @Test fun startupBurstRetainsOutputAndManualEvidenceWithinHardBound() {
        assertTrue(shouldWritePlaybackEvent("settings", 12))
        assertFalse(shouldWritePlaybackEvent("settings", 13))
        listOf("audio_output_initialized", "audio_decoder_initialized", "audio_position_advancing",
            "user_heard_stutter", "user_heard_stutter_after", "audio_underrun").forEach {
            assertTrue(shouldWritePlaybackEvent(it, 13))
            assertTrue(shouldWritePlaybackEvent(it, 32))
            assertFalse(shouldWritePlaybackEvent(it, 33))
        }
    }
    private fun sample(at: Long, buffer: Long = 3000, state: Int = 3, playing: Boolean = true, ready: Boolean = true) =
        PlaybackHealthSample(at, at, buffer, state, ready, playing, 0, 100, 20, 1)
    @Test fun healthySamplesStayInMemoryAndKeepOnlyTenContextRows() {
        val window = PlaybackHealthWindow()
        repeat(30) { assertNull(window.add(sample(it.toLong()))) }
        val rows = window.context().split(';')
        assertEquals(10, rows.size)
        assertTrue(rows.first().startsWith("20:"))
        assertTrue(rows.last().startsWith("29:"))
        assertFalse(File(folder.root, "playback-current.txt").exists())
    }
    @Test fun lowBufferLogsOnlyOnEntryAndMayTriggerAgainAfterRecovery() {
        val window = PlaybackHealthWindow()
        assertEquals("buffer_low", window.add(sample(1, buffer = 100)))
        repeat(10) { assertNull(window.add(sample(2L + it, buffer = 100))) }
        assertNull(window.add(sample(20, buffer = 5000)))
        assertEquals("buffer_low", window.add(sample(21, buffer = 0)))
    }
    @Test fun pausedAndInitialBufferingAreNotMistakenForPlayingLowBuffer() {
        val window = PlaybackHealthWindow()
        assertNull(window.add(sample(1, buffer = 0, playing = false)))
        assertNull(window.add(sample(2, buffer = 0, state = 2)))
        assertNull(window.add(sample(3, buffer = 0, ready = false)))
    }
    @Test fun resetDropsPreviousSongAndSessionContext() {
        val window = PlaybackHealthWindow()
        window.add(sample(1, buffer = 0))
        window.reset()
        assertEquals("", window.context())
        assertEquals("buffer_low", window.add(sample(2, buffer = 0)))
    }
    @Test fun knownSongEndIsNotReportedAsStarvation() {
        val window = PlaybackHealthWindow()
        assertNull(window.add(sample(214546, buffer = 93).copy(duration = 214640)))
        assertNull(window.add(sample(214640, buffer = 0).copy(duration = 214640)))
        assertEquals("buffer_low", window.add(sample(210000, buffer = 93).copy(duration = 214640)))
    }
    @Test fun unknownDurationDoesNotSuppressLowBufferWarning() {
        val window = PlaybackHealthWindow()
        assertEquals("buffer_low", window.add(sample(214546, buffer = 93).copy(duration = -9223372036854775807L)))
    }
    @Test fun journalWritesOnlyItsOwnFilesAndReadsInChronologicalOrder() {
        val unrelated = File(folder.root, "app_log_0.txt").apply { writeText("original performance log") }
        val journal = PlaybackDiagnosticJournal(folder.root)
        journal.append("SESSION test")
        journal.append("audio_underrun t=100")
        assertEquals("SESSION test\naudio_underrun t=100\n", journal.read())
        assertFalse(journal.read().contains("performance log"))
        assertEquals("original performance log", unrelated.readText())
    }
    @Test fun rotationBoundsFilesAndRetainsNewestEvidence() {
        val journal = PlaybackDiagnosticJournal(folder.root, 4096)
        repeat(500) { journal.append("event=$it " + "x".repeat(100)) }
        assertTrue(File(folder.root, "playback-current.txt").length() <= 4096)
        assertTrue(File(folder.root, "playback-previous.txt").length() <= 4096)
        assertTrue(journal.read().contains("event=499"))
        assertFalse(journal.read().contains("event=0 "))
    }
    @Test fun clearOnlyRemovesPlaybackFilesNotOriginalLogs() {
        val unrelated = File(folder.root, "app_log_0.txt").apply { writeText("keep") }
        val journal = PlaybackDiagnosticJournal(folder.root)
        journal.append("event")
        journal.clear()
        assertEquals("", journal.read())
        assertEquals("keep", unrelated.readText())
    }
    @Test fun oversizedLineIsBoundedAndUnicodeIsPreserved() {
        val journal = PlaybackDiagnosticJournal(folder.root)
        journal.append("播放诊断 " + "x".repeat(9000))
        assertEquals(1801, journal.read().length)
        assertTrue(journal.read().startsWith("播放诊断"))
    }
}
