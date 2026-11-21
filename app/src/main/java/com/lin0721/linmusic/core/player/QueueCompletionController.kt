package com.lin0721.linmusic.core.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Application-owned work survives navigating away, but never mutates a newer/user-edited queue. */
internal class QueueCompletionController(
    private val scope: CoroutineScope,
    private val queue: PlaybackQueue,
    private val save: () -> Unit,
    private val onFailure: (Exception) -> Unit
) {
    private var job: Job? = null

    fun cancel() { job?.cancel(); job = null }

    fun start(expectedRevision: Long, batches: List<List<Long>>, load: suspend (List<Long>) -> List<QueueItem>) {
        cancel()
        job = scope.launch {
            for (batch in batches) {
                if (queue.revision != expectedRevision) return@launch
                val items = try {
                    withTimeout(6000) { load(batch) }
                } catch (cancelled: kotlinx.coroutines.TimeoutCancellationException) {
                    onFailure(cancelled)
                    return@launch
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    onFailure(failure)
                    return@launch
                }
                if (queue.revision != expectedRevision) return@launch
                if (queue.extend(items)) save()
            }
        }
    }
}
