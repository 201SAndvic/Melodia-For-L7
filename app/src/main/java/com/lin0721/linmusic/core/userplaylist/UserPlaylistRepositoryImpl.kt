package com.lin0721.linmusic.core.userplaylist

import com.lin0721.linmusic.core.network.apiFlow
import kotlinx.coroutines.flow.Flow

class UserPlaylistRepositoryImpl(
    private val apiService: UserPlaylistApi,
    private val weakCache: com.lin0721.linmusic.core.network.WeakNetworkCache? = null
) : UserPlaylistRepository {

    override fun getUserPlaylists(uid: Long, limit: Int): Flow<Result<List<UserPlaylist>>> =
        weakCache?.cached("user_playlists:$uid:$limit") { getUncachedUserPlaylists(uid, limit) } ?: getUncachedUserPlaylists(uid, limit)

    private fun getUncachedUserPlaylists(uid: Long, limit: Int): Flow<Result<List<UserPlaylist>>> = apiFlow(
        request = { apiService.getUserPlaylists(UserPlaylistRequest(uid = uid, limit = limit)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.playlist }
    )
}
