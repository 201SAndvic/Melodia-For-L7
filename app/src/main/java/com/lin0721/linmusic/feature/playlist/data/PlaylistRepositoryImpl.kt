package com.lin0721.linmusic.feature.playlist.data

import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.feature.player.data.PlayerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaylistRepositoryImpl(
    private val apiService: PlaylistApi,
    private val contentFilter: ContentFilter,
    private val playerRepository: PlayerRepository,
    private val weakCache: com.lin0721.linmusic.core.network.WeakNetworkCache? = null
) : PlaylistRepository {

    override fun getPlaylistDetail(id: Long): Flow<Result<PlaylistDetail>> =
        (weakCache?.cached("playlist:$id") { getUncachedPlaylistDetail(id) } ?: getUncachedPlaylistDetail(id)).map { result ->
            result.map { it.copy(tracks = contentFilter.filterBlockedArtists(it.tracks) { track -> track.ar.map { a -> a.id } }) }
        }

    private fun getUncachedPlaylistDetail(id: Long): Flow<Result<PlaylistDetail>> = apiFlow(
        request = { apiService.getPlaylistDetail(PlaylistDetailRequest(id = id)) },
        isSuccess = { it.isSuccess && it.playlist != null },
        code = { it.code },
        transform = { it.playlist!! }
    )

    override fun loadMoreTracks(trackIds: List<Long>): Flow<Result<List<Track>>> =
        playerRepository.getSongDetails(trackIds).map { result ->
            result.map { tracks -> contentFilter.filterBlockedArtists(tracks) { it.ar.map { a -> a.id } } }
        }

    override fun getAlbumDetail(id: Long): Flow<Result<PlaylistDetail>> =
        (weakCache?.cached("album:$id") { getUncachedAlbumDetail(id) } ?: getUncachedAlbumDetail(id)).map { result ->
            result.map { it.copy(tracks = contentFilter.filterBlockedArtists(it.tracks) { track -> track.ar.map { a -> a.id } }) }
        }

    private fun getUncachedAlbumDetail(id: Long): Flow<Result<PlaylistDetail>> = apiFlow(
        // 专辑 ID 需作为 URL 路径参数传入，不使用 AlbumDetailRequest 请求体
        request = { apiService.getAlbumDetail(id = id) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            val album = response.album
            PlaylistDetail(
                id = album.id,
                name = album.name,
                coverImgUrl = album.picUrl,
                description = album.description,
                playCount = 0L,
                tracks = response.songs
            )
        }
    )

    override fun subscribePlaylist(playlistId: Long, subscribe: Boolean): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.subscribePlaylist(
                op = if (subscribe) "subscribe" else "unsubscribe",
                body = PlaylistSubscribeRequest(id = playlistId)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { weakCache?.invalidateMetadata(); Unit }
    )

    override fun subscribeAlbum(albumId: Long, subscribe: Boolean): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.subscribeAlbum(
                op = if (subscribe) "sub" else "unsub",
                body = AlbumSubscribeRequest(id = albumId)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { weakCache?.invalidateMetadata(); Unit }
    )

    override fun manipulatePlaylistTracks(op: String, playlistId: Long, trackIds: List<Long>): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.manipulatePlaylistTracks(
                PlaylistTracksManipulateRequest(
                    op = op,
                    pid = playlistId,
                    trackIds = trackIds.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                )
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { weakCache?.invalidateMetadata(); Unit }
    )

    override fun renamePlaylist(playlistId: Long, name: String): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.updatePlaylistName(
                PlaylistUpdateNameRequest(id = playlistId, name = name)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { weakCache?.invalidateMetadata(); Unit }
    )

    override fun updateDescription(playlistId: Long, desc: String): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.updatePlaylistDesc(
                PlaylistUpdateDescRequest(id = playlistId, desc = desc)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { weakCache?.invalidateMetadata(); Unit }
    )

    override fun deletePlaylist(playlistId: Long): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.deletePlaylist(
                PlaylistDeleteRequest(ids = "[$playlistId]")
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { weakCache?.invalidateMetadata(); Unit }
    )

    override fun updatePlaylistCover(playlistId: Long, coverImgId: Long): Flow<Result<String>> = apiFlow(
        request = {
            apiService.updatePlaylistCover(
                PlaylistUpdateCoverRequest(id = playlistId, coverImgId = coverImgId)
            )
        },
        isSuccess = { it.isSuccess && !it.url.isNullOrBlank() },
        code = { it.code },
        msg = { it.message },
        transform = { weakCache?.invalidateMetadata(); it.url.orEmpty() }
    )

}
