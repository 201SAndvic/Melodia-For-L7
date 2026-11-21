package com.lin0721.linmusic.core.log

import android.app.Activity
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import android.system.Os
import android.system.OsConstants

/** Opt-in process sampling. No periodic work runs unless the user enables it. */
object PerformanceMonitor {
    private const val SAMPLE_INTERVAL_MS = 5_000L
    private val enabled = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var foreground = "none"
    private var previousThreads = emptyMap<Int, Long>()

    fun start(enabledFlow: Flow<Boolean>) {
        scope.launch {
            enabledFlow.distinctUntilChanged().collectLatest { capture ->
                enabled.set(capture)
                if (!capture) {
                    AppLogger.performance("capture=off")
                    return@collectLatest
                }

                AppLogger.performance(
                    "capture=on version=${com.lin0721.linmusic.BuildConfig.VERSION_NAME}" +
                        " device=${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}" +
                        " api=${android.os.Build.VERSION.SDK_INT} cores=${Runtime.getRuntime().availableProcessors()}"
                )
                var previousCpuMs = Process.getElapsedCpuTime()
                previousThreads = emptyMap()
                var previousWallMs = SystemClock.elapsedRealtime()
                while (true) {
                    delay(SAMPLE_INTERVAL_MS)
                    val nowCpuMs = Process.getElapsedCpuTime()
                    val nowWallMs = SystemClock.elapsedRealtime()
                    val elapsedMs = (nowWallMs - previousWallMs).coerceAtLeast(1L)
                    val cpuPercent = ((nowCpuMs - previousCpuMs).coerceAtLeast(0L) * 100.0 / elapsedMs)
                    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                    val normalizedCpuPercent = cpuPercent / cores
                    val memory = readMemoryInfo()
                    val load = readLoadAverage()
                    AppLogger.performance(
                        String.format(
                            Locale.US,
                            "sample cpu_core=%.1f%% cpu_device=%.1f%% pss_mb=%.1f java_heap_mb=%.1f/%.1f " +
                                "native_heap_mb=%.1f threads=%d cores=%d load=%s foreground=%s",
                            cpuPercent,
                            normalizedCpuPercent,
                            memory.totalPssKb / 1024.0,
                            memory.javaHeapUsedMb,
                            memory.javaHeapMaxMb,
                            memory.nativeHeapUsedMb,
                            readThreadCount(),
                            cores,
                            load,
                            foreground
                        )
                    )
                    previousCpuMs = nowCpuMs
                    previousWallMs = nowWallMs
                    AppLogger.performance("sample_threads scope=app_process $foreground " + threadCpuSummary(elapsedMs))
                }
            }
        }
    }

    fun onActivityResumed(activity: Activity) {
        foreground = "${activity.javaClass.simpleName}:display=${activity.display?.displayId ?: -1}:task=${activity.taskId}"
        if (enabled.get()) AppLogger.performance("activity_resumed $foreground")
    }

    fun onActivityPaused(activity: Activity) {
        val paused = "${activity.javaClass.simpleName}:display=${activity.display?.displayId ?: -1}:task=${activity.taskId}"
        if (enabled.get()) AppLogger.performance("activity_paused $paused")
        if (foreground.startsWith(activity.javaClass.simpleName)) foreground = "background"
    }

    fun mark(event: String, details: String = "") {
        if (!enabled.get()) return
        AppLogger.performance("event=$event foreground=$foreground $details")
    }

    private fun readMemoryInfo(): MemorySnapshot {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        val runtime = Runtime.getRuntime()
        val javaUsed = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        return MemorySnapshot(
            totalPssKb = info.totalPss,
            javaHeapUsedMb = javaUsed / (1024.0 * 1024.0),
            javaHeapMaxMb = runtime.maxMemory() / (1024.0 * 1024.0),
            nativeHeapUsedMb = Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0)
        )
    }

    private fun readThreadCount(): Int = runCatching {
        File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("Threads:") }
                ?.substringAfter(':')?.trim()?.toIntOrNull() ?: -1
        }
    }.getOrDefault(-1)

    /** Opt-in /proc/self only. No stacks, credentials, network payloads or other processes. */
    private fun threadCpuSummary(elapsedMs: Long): String = runCatching {
        val ticksPerSecond = Os.sysconf(OsConstants._SC_CLK_TCK).coerceAtLeast(1L)
        val times = mutableMapOf<Int, Long>()
        val busiest = File("/proc/self/task").listFiles().orEmpty().mapNotNull { dir ->
            val id = dir.name.toIntOrNull() ?: return@mapNotNull null
            runCatching {
                val stat = File(dir, "stat").readText()
                val name = stat.substringAfter('(').substringBeforeLast(')').replace(' ', '_')
                val fields = stat.substringAfterLast(')').trim().split(Regex("\\s+"))
                val ticks = fields[11].toLong() + fields[12].toLong()
                times[id] = ticks
                val delta = previousThreads[id]?.let { (ticks - it).coerceAtLeast(0L) } ?: 0L
                val percent = delta * 100_000.0 / ticksPerSecond / elapsedMs.coerceAtLeast(1L)
                Triple(id, name, percent)
            }.getOrNull()
        }.sortedByDescending { it.third }.take(5)
        previousThreads = times
        "cpu_unit=single_core_equivalent top=" + busiest.joinToString { "${it.first}:${it.second}:${String.format(Locale.US, "%.1f", it.third)}%" }
    }.getOrElse { "unavailable=${it.javaClass.simpleName}" }

    private fun readLoadAverage(): String = runCatching {
        File("/proc/loadavg").readText().trim().substringBeforeLast(' ')
    }.getOrDefault("unavailable")

    private data class MemorySnapshot(
        val totalPssKb: Int,
        val javaHeapUsedMb: Double,
        val javaHeapMaxMb: Double,
        val nativeHeapUsedMb: Double
    )
}
