package com.lin0721.linmusic.core.log

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackMarkerAutoSaveTest {
    @Test fun markerWaitsSixSecondsThenSavesOnceWithoutStoppingCapture() = runTest {
        var markers = 0; var saves = 0
        val notifications = mutableListOf<String>()
        val saver = PlaybackMarkerAutoSave(this, { markers++ }, { saves++; "Download/test.txt" }, { notifications.add(it) })
        assertTrue(saver.request()); assertEquals(1, markers)
        runCurrent(); advanceTimeBy(5999); runCurrent()
        assertEquals(0, saves); assertTrue(saver.status.value.busy)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, saves); assertFalse(saver.busy)
        assertTrue(saver.status.value.message.contains("Download/test.txt"))
        assertEquals(1, notifications.size)
    }
    @Test fun repeatedTapsDoNotExtendWindowOrDuplicateFile() = runTest {
        var markers = 0; var saves = 0
        val saver = PlaybackMarkerAutoSave(this, { markers++ }, { saves++; "file" }, {})
        saver.request(); runCurrent(); advanceTimeBy(3000)
        assertFalse(saver.request()); advanceTimeBy(3000); runCurrent()
        assertEquals(1, markers); assertEquals(1, saves)
        assertTrue(saver.request()); runCurrent(); advanceUntilIdle()
        assertEquals(2, saves)
    }
    @Test fun failureKeepsRetryAvailableAndReportsManualFallback() = runTest {
        val messages = mutableListOf<String>()
        val saver = PlaybackMarkerAutoSave(this, {}, { error("storage denied") }, { messages.add(it) })
        saver.request(); advanceUntilIdle()
        assertFalse(saver.busy); assertTrue(messages.single().contains("保存失败"))
        assertTrue(saver.request()); advanceUntilIdle()
    }
    @Test fun stopCaptureDoesNotCancelPendingApplicationOwnedSave() = runTest {
        var capture = true; var saved = false
        val saver = PlaybackMarkerAutoSave(this, { assertTrue(capture) }, { saved = true; "partial" }, {})
        saver.request(); runCurrent(); capture = false
        advanceUntilIdle(); assertTrue(saved)
    }
    @Test fun busyIncludesStorageWriteNotJustDelay() = runTest {
        val storage = CompletableDeferred<String>()
        val saver = PlaybackMarkerAutoSave(this, {}, { storage.await() }, {})
        saver.request(); runCurrent(); advanceTimeBy(6000); runCurrent()
        assertTrue(saver.busy); assertFalse(saver.request())
        storage.complete("file"); runCurrent(); assertFalse(saver.busy)
    }
}
