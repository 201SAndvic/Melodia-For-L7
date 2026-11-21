package com.lin0721.linmusic.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import java.io.File
import java.security.MessageDigest

/** Bounded domain-result cache, not an HTTP cache of encrypted POST requests.
 * Account-scoped, read-only operations only. No credentials or mutation results.
 * The refresh job survives a first() consumer, and is single-flight across screens. */
class WeakNetworkCache(
    private val root: File,
    private val account: suspend () -> String,
    private val enabled: suspend () -> Boolean,
    private val online: () -> Boolean,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = System::currentTimeMillis,
    private val freshMs: Long = 30_000L,
    private val maxAgeMs: Long = 30L * 24 * 60 * 60 * 1000,
    private val maxBytes: Long = 32L * 1024 * 1024,
    private val requestTimeoutMs: Long = 6_000L,
    private val event: (String) -> Unit = {}
) {
    private data class Entry(val savedAt: Long, val value: JsonElement)
    private val diskLock = Mutex()
    private val requests = SharedResultCache<String, String>(24, 1_000L, scope, nowMs = now)
    private val revisions = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var writes = 0
    private var diskBytes: Long? = null

    fun <T> cached(key: String, serializer: KSerializer<T>, source: () -> Flow<Result<T>>): Flow<Result<T>> = flow {
        if (!enabled()) { emitAll(source()); return@flow }
        val owner = account()
        val revision = revisions[owner] ?: 0L
        val fileKey = "$owner:${serializer.descriptor.serialName}:$key"
        val entry = readEntry(fileKey)
        val hit = entry?.let { runCatching { json.decodeFromJsonElement(serializer, it.value) }.getOrNull() }
        if (hit != null && (now() - entry!!.savedAt <= freshMs || !online())) {
            event("event=weak_cache_hit kind=$key offline=${!online()}")
            emit(Result.success(hit))
            return@flow
        }
        if (!online()) { emit(Result.failure(AppError.NetworkError)); return@flow }
        val refresh = scope.async {
            requests.get("$revision:$fileKey") {
                val result = try {
                    withTimeoutOrNull(requestTimeoutMs) { source().first() } ?: Result.failure(AppError.NetworkError)
                } catch (e: CancellationException) { throw e }
                  catch (e: Exception) { Result.failure(e) }
                if (result.isSuccess) {
                    val value = result.getOrThrow()
                    val encoded = json.encodeToJsonElement(serializer, value)
                    writeEntry(fileKey, encoded) { (revisions[owner] ?: 0L) == revision }
                    Result.success(encoded.toString())
                } else {
                    val error = result.exceptionOrNull()!!
                    if (!isCacheFallbackError(error)) removeEntry(fileKey)
                    Result.failure(error)
                }
            }
        }
        // Start refresh before exposing stale data: a caller taking first() must
        // not leave its cache permanently stale or force a second screen's request.
        if (hit != null) {
            event("event=weak_cache_hit kind=$key offline=false refreshing=true")
            emit(Result.success(hit))
        }
        val result = refresh.await().mapCatching { json.decodeFromString(serializer, it) }
        if (account() != owner) return@flow
        if (hit != null && result.exceptionOrNull()?.let(::isCacheFallbackError) == true) {
            event("event=weak_cache_fallback kind=$key")
        } else emit(result)
    }.flowOn(Dispatchers.IO)

    inline fun <reified T> cached(key: String, noinline source: () -> Flow<Result<T>>): Flow<Result<T>> =
        cached(key, serializer<T>(), source)

    suspend fun <T> readSaved(key: String, serializer: KSerializer<T>): T? {
        if (!enabled()) return null
        val entry = readEntry("${account()}:${serializer.descriptor.serialName}:$key") ?: return null
        return runCatching { json.decodeFromJsonElement(serializer, entry.value) }.getOrNull()
    }

    suspend fun <T> save(key: String, serializer: KSerializer<T>, value: T) {
        if (enabled()) writeEntry("${account()}:${serializer.descriptor.serialName}:$key", json.encodeToJsonElement(serializer, value))
    }

    /** Mutations themselves are never cached. Invalidate only this account's
     * read-only metadata, retaining audio indexes and other accounts' data. */
    suspend fun invalidateMetadata() {
        val owner = account()
        revisions.merge(owner, 1L) { a, b -> a + b }
        withContext(Dispatchers.IO) {
            diskLock.withLock {
                val prefix = digest(owner) + "-"
                root.listFiles()?.filter { it.name.startsWith(prefix) && it.name.endsWith(".data.json") }?.forEach { it.delete() }
                diskBytes = null
            }
        }
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun file(key: String): File {
        val category = if (key.contains(":audio:") || key.contains(":audio_latest:")) "audio" else "data"
        return File(root, "${digest(key.substringBefore(':'))}-${digest(key)}.$category.json")
    }

    private suspend fun readEntry(key: String): Entry? = withContext(Dispatchers.IO) {
        diskLock.withLock {
            val target = file(key)
            if (!target.isFile || target.length() > 4L * 1024 * 1024) return@withLock null
            try {
                val data = json.parseToJsonElement(target.readText()).jsonObject
                val saved = data.getValue("savedAt").jsonPrimitive.long
                if (now() - saved > maxAgeMs) { target.delete(); null }
                else Entry(saved, data.getValue("value"))
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { target.delete(); null }
        }
    }

    private suspend fun writeEntry(key: String, value: JsonElement, stillCurrent: () -> Boolean = { true }) = withContext(Dispatchers.IO) {
        diskLock.withLock {
            if (!stillCurrent()) return@withLock
            val data = buildJsonObject { put("savedAt", now()); put("value", value) }.toString()
            if (data.toByteArray().size > 4 * 1024 * 1024) return@withLock
            val target = file(key)
            val temp = File(root, "${target.name}.tmp")
            try {
                root.mkdirs()
                val beforeBytes = diskBytes ?: root.listFiles()?.filter { it.extension == "json" }?.sumOf { it.length() } ?: 0L
                val oldLength = target.length()
                temp.writeText(data)
                java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                diskBytes = beforeBytes - oldLength + target.length()
                if (++writes == 1 || writes % 16 == 0 || diskBytes!! > maxBytes) trim()
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { temp.delete() } // Cache failures never break playback.
        }
    }

    private suspend fun removeEntry(key: String) = withContext(Dispatchers.IO) { diskLock.withLock { file(key).delete(); Unit } }

    private fun trim() {
        val files = root.listFiles()?.filter { it.isFile && it.extension == "json" }?.sortedBy { it.lastModified() }.orEmpty()
        var size = files.sumOf { it.length() }
        var count = files.size
        for (item in files) {
            if (size <= maxBytes && count <= 512) break
            val length = item.length()
            if (item.delete()) { size -= length; count-- }
        }
        diskBytes = size
    }
}

internal fun isCacheFallbackError(error: Throwable): Boolean =
    error is AppError.NetworkError || error is java.io.IOException ||
        (error is AppError.BizError && error.code in 500..599)
