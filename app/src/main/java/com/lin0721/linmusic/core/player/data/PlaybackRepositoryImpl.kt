package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.core.network.mapToAppError
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricParser
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.emitAll
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "PlaybackRepositoryImpl"

class PlaybackRepositoryImpl(
    private val apiService: PlaybackApi,
    private val settingsPreferences: SettingsPreferences,
    private val userPreferences: UserPreferences,
    private val userPlaylistRepository: UserPlaylistRepository,
    private val contentFilter: ContentFilter,
    private val context: android.content.Context,
    private val json: Json,
    private val weakCache: com.lin0721.linmusic.core.network.WeakNetworkCache? = null
) : PlaybackRepository {

    private fun isWifiConnected(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Wi-Fi 状态检测异常，按移动网络处理", e)
            false
        }
    }

    private suspend fun currentQuality(): String = if (isWifiConnected()) settingsPreferences.wifiQuality.first() else settingsPreferences.mobileQuality.first()

    override fun getSongUrl(songId: Long): Flow<Result<String>> = getSongAudioInfo(songId).map { it.map { info -> info.url } }

    override fun getSongAudioInfo(songId: Long): Flow<Result<PlaybackAudioInfo>> = flow {
        val quality = currentQuality()
        val owner = userPreferences.userProfile.first()?.uid?.toString() ?: "guest"
        emitAll(apiFlow(
            request = { apiService.getSongUrl(body = SongUrlRequest(ids = "[$songId]", level = quality)) },
            isSuccess = { it.isSuccess && !it.data.firstOrNull()?.url.isNullOrBlank() },
            code = { it.code },
            transform = {
                val item = it.data.first()
                PlaybackAudioInfo(item.url!!, audioCacheKey(owner, songId, quality, item), item.size,
                    item.freeTrialInfo != null, System.currentTimeMillis())
            }
        ).onEach { result ->
            result.getOrNull()?.let {
                weakCache?.save("audio:$songId:$quality", PlaybackAudioInfo.serializer(), it)
                weakCache?.save("audio_latest:$songId", PlaybackAudioInfo.serializer(), it)
            }
        })
    }

    // Do not treat a valid old URL as permission to stream: this lookup only
    // returns complete, non-trial bytes that already exist in our local cache.
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override suspend fun getCompleteCachedAudio(songId: Long): PlaybackAudioInfo? {
        if (!settingsPreferences.weakNetworkCacheEnabled.first()) return null
        val preferred = weakCache?.readSaved("audio:$songId:${currentQuality()}", PlaybackAudioInfo.serializer())
        val latest = weakCache?.readSaved("audio_latest:$songId", PlaybackAudioInfo.serializer())
        for (info in listOfNotNull(preferred, latest).distinctBy { it.cacheKey }) {
          val full = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val cache = com.lin0721.linmusic.core.player.AudioCacheManager.getCache(context, settingsPreferences.audioCacheMaxSize.first())
                info.cacheKey?.let { info.sizeBytes > 0 && cache.isCached(it, 0, info.sizeBytes) } ?: false
            }.getOrDefault(false)
        }
          if (canUseCompleteAudio(info, full)) return info
        }
        return null
    }

    override suspend fun cacheKeyForSongUrl(songId: Long, url: String): String? {
        val info = weakCache?.readSaved("audio_latest:$songId", PlaybackAudioInfo.serializer())
        return info?.takeIf { it.url == url }?.cacheKey
    }

    private val sharedLyrics = com.lin0721.linmusic.core.network.SharedResultCache<Long, List<LyricLine>>()

    override fun getLyrics(songId: Long): Flow<Result<List<LyricLine>>> = kotlinx.coroutines.flow.flow {
        emit(sharedLyrics.get(songId) {
            // Parsed schema v2 excludes timestamps embedded in JSON metadata rows.
            // Do not reuse old parsed results which may already contain JSON suffixes.
            // Await the bounded refresh rather than freezing stale untranslated data.
            (weakCache?.cached("lyrics:v2:$songId") { getUncachedLyrics(songId) } ?: getUncachedLyrics(songId)).last()
        })
    }

    private fun getUncachedLyrics(songId: Long): Flow<Result<List<LyricLine>>> = apiFlow(
        request = {
            apiService.getLyrics(
                LyricRequest(id = songId, tv = -1, lv = -1, rv = -1, kv = -1, ytv = -1, yrv = -1)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            when {
                // 纯音乐返回带标识的单行；未收录返回空列表以隐藏卡片
                response.nolyric -> listOf(LyricLine(timeMs = 0, text = "纯音乐"))
                response.uncollected -> emptyList()
                else -> {
                    val yrcText = response.yrc?.lyric
                    val lrcText = response.lrc?.lyric
                    // 检测歌词文本中是否包含“纯音乐”或“Instrumental”标识
                    val isInstrumental = (!yrcText.isNullOrBlank() && (yrcText.contains("纯音乐") || yrcText.contains("Instrumental", ignoreCase = true))) ||
                            (!lrcText.isNullOrBlank() && (lrcText.contains("纯音乐") || lrcText.contains("Instrumental", ignoreCase = true)))
                    if (isInstrumental) {
                        listOf(LyricLine(timeMs = 0, text = "纯音乐"))
                    } else {
                        val lines = if (!yrcText.isNullOrBlank()) {
                            val parsedYrc = LyricParser.parseYrc(yrcText)
                            if (parsedYrc.isNotEmpty()) parsedYrc else LyricParser.parseLrc(lrcText ?: "")
                        } else {
                            LyricParser.parseLrc(lrcText ?: "")
                        }
                        if (lines.isEmpty()) {
                            emptyList()
                        } else {
                            // 解析翻译歌词列表（优先使用 ytlrc，其次使用 tlyric）
                            val translationLines = LyricParser.parsePreferredTranslation(response.ytlrc?.lyric, response.tlyric?.lyric)
                            // 解析罗马音歌词列表
                            val romaLines = LyricParser.parseLrc(response.romalrc?.lyric ?: "")
                            lines.map { line ->
                                // 寻找在 150ms 内与原词时间戳最接近的翻译行
                                val matchedTranslation = translationLines
                                    .filter { kotlin.math.abs(it.timeMs - line.timeMs) < 150 }
                                    .minByOrNull { kotlin.math.abs(it.timeMs - line.timeMs) }
                                    ?.text
                                // 寻找在 150ms 内与原词时间戳最接近的罗马音行
                                val matchedRoma = romaLines
                                    .filter { kotlin.math.abs(it.timeMs - line.timeMs) < 150 }
                                    .minByOrNull { kotlin.math.abs(it.timeMs - line.timeMs) }
                                    ?.text
                                line.copy(translation = matchedTranslation, roma = matchedRoma)
                            }
                        }
                    }
                }
            }
        }
    )

    override fun getSimilarSongs(songId: Long): Flow<Result<List<Track>>> = apiFlow(
        request = { apiService.getSimiSongs(SimiSongRequest(songid = songId.toString())) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { contentFilter.filterBlockedArtists(it.songs) { song -> song.ar.map { a -> a.id } } }
    )

    // 未指定歌单上下文时（如首页心动模式入口），取当前歌单列表首位
    override fun getIntelligenceSongs(songId: Long, playlistId: Long): Flow<Result<List<Track>>> = apiFlow(
        request = {
            val finalPlaylistId = if (playlistId != 0L) {
                playlistId
            } else {
                val uid = userPreferences.userProfile.first()?.uid
                    ?: throw AppError.BizError(-1, null)
                userPlaylistRepository.getUserPlaylists(uid, limit = 1).first().getOrThrow()
                    .firstOrNull()?.id
                    ?: throw AppError.BizError(-1, null)
            }
            apiService.getIntelligenceSongs(
                IntelligenceSongsRequest(
                    songId = songId.toString(),
                    playlistId = finalPlaylistId.toString(),
                    startMusicId = songId.toString(),
                    count = 20
                )
            )
        },
        isSuccess = { response -> response.isSuccess && response.data.any { it.songInfo != null } },
        code = { it.code },
        transform = { response ->
            contentFilter.filterBlockedArtists(response.data.mapNotNull { it.songInfo }) { it.ar.map { a -> a.id } }
        }
    )

    // 歌曲开始播放时立即上报，进「最近播放」；sourceId 暂用 songId 本身代替（缺少真实来源容器映射）
    override fun reportStartPlay(songId: Long): Flow<Result<Unit>> = flow {
        val startplayLogs = json.encodeToString(
            listOf(StartPlayLogEntry(json = StartPlayLogJson(id = songId, content = "id=$songId")))
        )
        val startRes = apiService.reportWeblog(WeblogRequest(logs = startplayLogs))
        AppLogger.i(TAG, "打卡上报 startplay 返回: songId=$songId code=${startRes.code} data=${startRes.data}")

        if (startRes.isSuccess) {
            emit(Result.success(Unit))
        } else {
            emit(Result.failure(AppError.BizError(startRes.code, startRes.data)))
        }
    }.catch { e ->
        AppLogger.e(TAG, "打卡上报 startplay 请求异常", e)
        emit(Result.failure(mapToAppError(e)))
    }

    // 离开歌曲（切歌/退出播放器）时上报实际播放时长，涨「听歌排行」计数
    override fun reportPlayEnd(songId: Long, playedSeconds: Long): Flow<Result<Unit>> = flow {
        val playLogs = json.encodeToString(
            listOf(
                PlayLogEntry(
                    json = PlayLogJson(
                        id = songId,
                        sourceId = songId,
                        time = playedSeconds,
                        content = "id=$songId"
                    )
                )
            )
        )
        val playRes = apiService.reportWeblog(WeblogRequest(logs = playLogs))
        AppLogger.i(TAG, "打卡上报 play 返回: songId=$songId time=$playedSeconds code=${playRes.code} data=${playRes.data}")

        if (playRes.isSuccess) {
            emit(Result.success(Unit))
        } else {
            emit(Result.failure(AppError.BizError(playRes.code, playRes.data)))
        }
    }.catch { e ->
        AppLogger.e(TAG, "打卡上报 play 请求异常", e)
        emit(Result.failure(mapToAppError(e)))
    }
}
