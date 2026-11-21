package com.lin0721.linmusic.feature.library.data

import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.network.apiFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepositoryImpl(
    private val apiService: LibraryApi,
    private val contentFilter: ContentFilter,
    private val weakCache: com.lin0721.linmusic.core.network.WeakNetworkCache? = null
) : LibraryRepository {

    override fun getUserRecord(uid: Long, type: Int): Flow<Result<List<UserRecordTrack>>> =
        (weakCache?.cached("user_record:$uid:$type") { getUncachedUserRecord(uid, type) } ?: getUncachedUserRecord(uid, type)).map { result ->
            result.map { contentFilter.filterBlockedArtists(it) { item -> item.track.ar.map { a -> a.id } } }
        }

    private fun getUncachedUserRecord(uid: Long, type: Int): Flow<Result<List<UserRecordTrack>>> = apiFlow(
        request = { apiService.getUserRecord(UserRecordRequest(uid = uid, type = type)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            val list = if (type == 1) {
                response.weekData ?: emptyList()
            } else {
                response.allData ?: emptyList()
            }
            val records = list.map { UserRecordTrack(track = it.song, playCount = it.playCount) }
            records
        }
    )

    override fun getCollectedAlbums(limit: Int): Flow<Result<List<AlbumSubItem>>> =
        weakCache?.cached("collected_albums:$limit") { getUncachedCollectedAlbums(limit) } ?: getUncachedCollectedAlbums(limit)

    private fun getUncachedCollectedAlbums(limit: Int): Flow<Result<List<AlbumSubItem>>> = apiFlow(
        request = { apiService.getAlbumSublist(AlbumSublistRequest(limit = limit)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.data }
    )

    override fun getUserSubcount(): Flow<Result<UserSubcountResponse>> =
        weakCache?.cached("user_subcount") { getUncachedUserSubcount() } ?: getUncachedUserSubcount()

    private fun getUncachedUserSubcount(): Flow<Result<UserSubcountResponse>> = apiFlow(
        request = { apiService.getUserSubcount() },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it }
    )
}
