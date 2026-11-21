package com.lin0721.linmusic.feature.create.data

import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.core.model.PlaylistDetail
import kotlinx.coroutines.flow.Flow

class CreateRepositoryImpl(
    private val apiService: CreateApi,
    private val weakCache: com.lin0721.linmusic.core.network.WeakNetworkCache? = null
) : CreateRepository {

    override fun createPlaylist(name: String, privacy: Int): Flow<Result<PlaylistDetail>> = apiFlow(
        request = { apiService.createPlaylist(PlaylistCreateRequest(name = name, privacy = privacy)) },
        isSuccess = { it.isSuccess && it.playlist != null },
        code = { it.code },
        transform = { weakCache?.invalidateMetadata(); it.playlist!! }
    )
}
