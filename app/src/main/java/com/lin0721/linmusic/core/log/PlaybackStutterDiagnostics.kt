package com.lin0721.linmusic.core.log

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.lin0721.linmusic.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Explicit opt-in. No logcat/AppLogger writes, no URLs, lyrics, titles or credentials.
 * Off: no sampler/watchdog, no transfers counted. On: one main-loop probe/second,
 * ten in-memory samples, disk output only for events, transitions and summaries. */
object PlaybackStutterDiagnostics {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = AtomicBoolean(false)
    private val dropped = AtomicLong()
    private val network = AtomicLong()
    private val cached = AtomicLong()
    private val lastCpu = AtomicLong()
    private val started = AtomicBoolean(false)
    @Volatile private var configuration = "pending"
    private val session = AtomicLong()
    private val health = PlaybackHealthWindow()
    private val postMarker = PlaybackPostMarkerWindow()
    private val lock = Any()
    private var snapshot: (() -> PlaybackHealthSample?)? = null // Accessed on main only.
    private var songId = "none"
    private var journal: PlaybackDiagnosticJournal? = null
    private val queue = Channel<() -> Unit>(256)
    private val totals = linkedMapOf<String, Long>() // Writer only.
    private var rateSecond = -1L
    private var rateCount = 0
    private var rateLimited = 0L // Writer only; reported separately from a full queue.
    @Volatile private var markerAutoSave: PlaybackMarkerAutoSave? = null
    private val emptySaveStatus = kotlinx.coroutines.flow.MutableStateFlow(MarkerSaveStatus())
    internal val markerSaveStatus get() = markerAutoSave?.status ?: emptySaveStatus
    val enabled: Boolean get() = active.get()
    internal val generation: Long get() = session.get()

    fun initialize(context: Context, setting: Flow<Boolean>) {
        if (!started.compareAndSet(false, true)) return
        journal = PlaybackDiagnosticJournal(File(context.filesDir, "playback-diagnostics"))
        val appContext = context.applicationContext
        markerAutoSave = PlaybackMarkerAutoSave(scope, mark = { markHeardStutter() }, save = {
            synchronized(lock) {
                // A blocked main thread may not have completed the sampling window.
                postMarker.finish("auto_save_deadline")?.let { event("user_heard_stutter_after", it) }
            }
            writeDiagnosticText(appContext, export(), "Melodia-播放卡顿")
        }, notify = { com.lin0721.linmusic.core.ui.components.ToastManager.showToast(it) })
        scope.launch {
            for (action in queue) runCatching { action() } // Recording failure must never interrupt music.
        }
        scope.launch {
            setting.distinctUntilChanged().collectLatest { capture ->
                val generation: Long
                synchronized(lock) {
                    if (!capture && active.get()) postMarker.finish("capture_off")?.let { event("user_heard_stutter_after", it) }
                    val previouslyActive = active.getAndSet(capture)
                    generation = session.incrementAndGet()
                    health.reset()
                    postMarker.reset()
                    network.set(0); cached.set(0); lastCpu.set(0)
                    if (capture) enqueue {
                        totals.clear()
                        rateLimited = 0; rateSecond = -1; rateCount = 0
                        journal?.append("\nSESSION ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} version=${BuildConfig.VERSION_NAME} build=${BuildConfig.VERSION_CODE} api=${android.os.Build.VERSION.SDK_INT} schema=2")
                        journal?.append("Only playback events. ctx=t:pos/buffer/state/playing/netBytes/cacheBytes/processCpuMs; CPU=process delta, not whole-device. No periodic CPU/general logs included.")
                    } else if (previouslyActive) enqueue { summary("capture_off") }
                }
                if (!capture) return@collectLatest
                event("capture_on", "settings=$configuration")
                val main = Handler(Looper.getMainLooper())
                val awaiting = AtomicLong(0)
                var reported = false
                var previous = SystemClock.elapsedRealtime()
                try {
                    while (currentCoroutineContext().isActive) {
                        delay(1000)
                        val now = SystemClock.elapsedRealtime()
                        val ioGap = now - previous - 1000
                        previous = now
                        if (ioGap > 1000) event("sampler_gap", "extraMs=$ioGap")
                        val posted = awaiting.get()
                        if (posted != 0L) {
                            if (now - posted >= 750 && !reported) {
                                reported = true
                                event("main_stall", "pendingMs=${now - posted}")
                            }
                        } else {
                            awaiting.set(now)
                            main.post {
                                if (enabled && session.get() == generation) {
                                    val lag = SystemClock.elapsedRealtime() - now
                                    if (lag >= 300) event("main_delay", "delayMs=$lag")
                                    runCatching { snapshot?.invoke() }.getOrNull()?.let { sample ->
                                        val reason = synchronized(lock) { health.add(sample) }
                                        if (reason != null) event(reason)
                                        synchronized(lock) { postMarker.add(sample) }?.let { event("user_heard_stutter_after", it) }
                                    }
                                }
                                awaiting.set(0)
                            }
                            reported = false
                        }
                    }
                } finally { synchronized(lock) { health.reset() } }
            }
        }
    }

    internal fun bindSnapshot(provider: (() -> PlaybackHealthSample?)?) { snapshot = provider }
    fun bytes(isNetwork: Boolean, count: Int) { if (enabled) (if (isNetwork) network else cached).addAndGet(count.toLong()) }
    fun cacheRead(count: Long) { if (enabled) cached.addAndGet(count) }
    fun configuration(value: String) { configuration = value; event("settings", value) }
    fun track(id: String?) {
        synchronized(lock) {
            postMarker.finish("track_changed")?.let { event("user_heard_stutter_after", it) }
            songId = id?.toLongOrNull()?.toString() ?: "local"; health.reset()
        }
        event("track", "id=$songId")
    }
    internal fun sample(position: Long, buffer: Long, state: Int, ready: Boolean, playing: Boolean, suppression: Int, duration: Long = -1L): PlaybackHealthSample {
        val cpu = Process.getElapsedCpuTime()
        val previous = lastCpu.getAndSet(cpu)
        return PlaybackHealthSample(SystemClock.elapsedRealtime(), position, buffer, state, ready, playing, suppression,
            network.getAndSet(0), cached.getAndSet(0), if (previous == 0L) 0 else (cpu - previous).coerceAtLeast(0), duration)
    }

    fun event(kind: String, detail: String = "") {
        if (!enabled) return
        synchronized(lock) {
            if (!enabled) return
            val now = SystemClock.elapsedRealtime()
            val context = if (needsPlaybackContext(kind)) health.context() else ""
            val id = songId
            val safe = detail.replace('\n', ' ').replace('\r', ' ').take(if (kind == "user_heard_stutter_after") 1000 else 500)
            enqueue {
                totals[kind] = (totals[kind] ?: 0) + 1
                if (now / 1000 != rateSecond) { rateSecond = now / 1000; rateCount = 0 }
                if (shouldWritePlaybackEvent(kind, ++rateCount)) {
                    journal?.append("t=$now song=$id $kind $safe" + if (context.isEmpty()) "" else " ctx=[$context]")
                } else rateLimited++
            }
        }
    }
    /** Explicit user timestamp marker helps locate an audible glitch even without an engine event. */
    fun markHeardStutter() {
        synchronized(lock) {
            if (!enabled) return
            event("user_heard_stutter")
            postMarker.start(SystemClock.elapsedRealtime())
        }
    }
    /** Returns false when disabled, uninitialized, or an earlier marker is still saving. */
    fun markAndAutoSave(): Boolean = synchronized(lock) {
        enabled && markerAutoSave?.request() == true
    }
    private fun enqueue(action: () -> Unit) { if (queue.trySend(action).isFailure) dropped.incrementAndGet() }
    private fun summary(reason: String) { journal?.append("SUMMARY $reason events=${totals.entries.joinToString { "${it.key}:${it.value}" }} rateLimited=$rateLimited queueDropped=${dropped.getAndSet(0)}") }

    suspend fun export(): String {
        val result = CompletableDeferred<String>()
        return withTimeout(5000) {
            queue.send {
                runCatching {
                    if (journal?.read().isNullOrBlank()) "尚无播放卡顿诊断记录。请单独开启后复现。"
                    else { summary("export"); journal?.read().orEmpty() }
                }
                    .onSuccess { result.complete(it) }.onFailure { result.completeExceptionally(it) }
            }
            result.await()
        }
    }
    suspend fun clear() = withTimeout(5000) {
        check(markerAutoSave?.busy != true) { "请等待本次自动保存完成" }
        val result = CompletableDeferred<Unit>()
        queue.send {
            runCatching { journal?.clear(); totals.clear(); dropped.set(0); rateLimited = 0 }
                .onSuccess { result.complete(Unit) }.onFailure { result.completeExceptionally(it) }
        }
        result.await()
    }
}
