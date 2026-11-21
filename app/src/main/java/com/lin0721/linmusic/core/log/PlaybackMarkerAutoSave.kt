package com.lin0721.linmusic.core.log

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class MarkerSaveStatus(val busy: Boolean = false, val message: String = "")

/** Application-owned, not tied to the settings screen. One bounded save per marker. */
internal class PlaybackMarkerAutoSave(
    private val scope: CoroutineScope,
    private val mark: () -> Unit,
    private val save: suspend () -> String,
    private val notify: (String) -> Unit,
    private val delayMs: Long = 6000L
) {
    private val pending = AtomicBoolean(false)
    private val mutableStatus = MutableStateFlow(MarkerSaveStatus())
    val status = mutableStatus.asStateFlow()
    val busy get() = pending.get()

    fun request(): Boolean {
        if (!pending.compareAndSet(false, true)) return false
        try { mark() } catch (error: Exception) { pending.set(false); throw error }
        mutableStatus.value = MarkerSaveStatus(true, "已标记，约6秒后自动保存；可离开本页")
        scope.launch {
            try {
                delay(delayMs)
                val path = save()
                val message = "卡顿日志已自动保存：$path"
                mutableStatus.value = MarkerSaveStatus(message = message)
                notify(message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val message = "自动保存失败，记录仍在应用内，请用“保存播放诊断”重试"
                mutableStatus.value = MarkerSaveStatus(message = message)
                notify(message)
            } finally {
                pending.set(false)
                if (mutableStatus.value.busy) mutableStatus.value = MarkerSaveStatus(message = "自动保存已中止，可手动保存已有记录")
            }
        }
        return true
    }
}
