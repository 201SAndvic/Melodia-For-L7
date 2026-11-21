package com.lin0721.linmusic.core.songlike

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared by mini and full players, including both displays. Read latest state
 * inside the lock so concurrent UI owners do not send contradictory toggles. */
class ToggleSongLikeUseCase(private val repository: SongLikeRepository) {
    private val mutex = Mutex()
    suspend operator fun invoke(songId: Long): Result<Unit> = mutex.withLock {
        if (songId <= 0) return@withLock Result.failure(IllegalArgumentException("Not an online song"))
        repository.likeSong(songId, songId !in repository.likedSongIds.value).first()
    }
}
