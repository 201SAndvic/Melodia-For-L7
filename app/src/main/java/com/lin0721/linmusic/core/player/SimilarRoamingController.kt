package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "SimilarRoamingController"

// 私人漫游：播放到队列末尾时自动续接相似歌曲，退出漫游时还原原队列
class SimilarRoamingController(
    private val scope: CoroutineScope,
    private val repository: PlaybackRepository,
    private val settingsPreferences: SettingsPreferences,
    private val queue: PlaybackQueue,
    private val stateStore: PlaybackStateStore
) {

    companion object {
        const val CONTEXT_ROAMING = "similar_roaming"

        // 距当前曲目结束的续接预取阈值
        private const val PREFETCH_THRESHOLD_MS = 15000L
        private const val MIN_UNPLAYED = 5
        private const val TARGET_UNPLAYED = 20
    }

    private var roamingJob: Job? = null
    private var generation = 0L

    // 记录已触发过续接的歌曲，避免同一首歌反复拉取
    private var lastTriggeredSongId = -1L

    val isRoaming: Boolean get() = queue.playContext.value == CONTEXT_ROAMING

    fun cancel() {
        generation++
        roamingJob?.cancel()
        roamingJob = null
    }

    // 进入漫游前备份队列，并强制切回列表循环
    fun prepare() {
        cancel()
        lastTriggeredSongId = -1L
        queue.takeSnapshot()
        queue.setPlayMode(PlayMode.LIST_LOOP)
        stateStore.savePlayMode(PlayMode.LIST_LOOP)
    }

    // 起播时若已处于漫游态，后台预取后续歌曲
    fun prefetchOnPlay(songId: Long) {
        if (!isRoaming || queue.upcomingCount >= MIN_UNPLAYED) return
        scheduleFill(songId)
    }

    // 播放到队列末尾且临近结束时触发续接
    suspend fun onProgressTick(songId: Long, remainingMs: Long) {
        if (queue.upcomingCount > 0) return
        if (remainingMs > PREFETCH_THRESHOLD_MS) return
        if (lastTriggeredSongId == songId) return
        if (!settingsPreferences.autoPlayNext.first()) return

        lastTriggeredSongId = songId
        queue.setPlayContext(CONTEXT_ROAMING)
        scheduleFill(songId)
    }

    // 关闭漫游并还原备份的队列数据
    fun disable() {
        if (!isRoaming) return
        cancel()
        queue.restoreSnapshot()
        stateStore.saveQueue(queue)
    }

    private fun scheduleFill(songId: Long) {
        if (roamingJob?.isActive == true) return
        val expectedGeneration = generation
        val expectedRevision = queue.revision
        roamingJob = scope.launch {
            try {
                repository.getSimilarSongs(songId).collect { result ->
                    result.onSuccess { simiSongs ->
                        if (isRoaming && generation == expectedGeneration && queue.revision == expectedRevision) {
                            val needed = (TARGET_UNPLAYED - queue.upcomingCount).coerceAtLeast(0)
                            val simiItems = selectRoamingSongs(simiSongs, songId, queue.items.value, needed).map { track ->
                                QueueItem(track.id, track.name, track.ar.joinToString("/") { it.name }, track.al.picUrl)
                            }
                            if (queue.extend(simiItems)) stateStore.saveQueue(queue)
                        }
                    }.onFailure {
                        AppLogger.w(TAG, "漫游续接相似歌曲失败 songId=$songId", it)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLogger.w(TAG, "漫游续接失败，保留当前队列", failure)
            }
        }
    }
}

internal fun selectRoamingSongs(
    candidates: List<com.lin0721.linmusic.core.model.Track>,
    seedSongId: Long,
    queued: List<QueueItem>,
    limit: Int
): List<com.lin0721.linmusic.core.model.Track> {
    if (limit <= 0) return emptyList()
    val seen = queued.mapTo(HashSet()) { it.songId }.apply { add(seedSongId) }
    return candidates.filter { seen.add(it.id) }.take(limit)
}
