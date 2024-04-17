package me.him188.ani.datasources.core.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.paging.SingleShotPagedSource
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.useDirectoryEntries
import kotlin.io.path.writeText

private const val METADATA_FILE_EXTENSION = "metadata"

/**
 * 本地目录缓存
 */
class DirectoryMediaCacheStorage(
    override val mediaSourceId: String,
    private val dir: Path,
    private val engine: MediaCacheEngine,
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : MediaCacheStorage {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
        }
        private val logger = logger<DirectoryMediaCacheStorage>()
    }

    private val scope: CoroutineScope =
        CoroutineScope(parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]))

    @Serializable
    private class MediaCacheSave(
        val origin: Media,
        val metadata: MediaCacheMetadata,
    )

    init {
        if (!dir.exists()) {
            dir.createDirectories()
        }

        scope.launch {
            dir.useDirectoryEntries { files ->
                files.forEach { file ->
                    if (file.extension != METADATA_FILE_EXTENSION) return@forEach

                    val save = try {
                        json.decodeFromString(MediaCacheSave.serializer(), file.readText())
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to deserialize metadata file ${file.name}" }
                        return@forEach
                    }

                    try {
                        val cache = engine.restore(save.origin, save.metadata, scope.coroutineContext)?.also {
                            lock.withLock {
                                listFlow.value += it
                            }
                        }
                        logger.info { "Cache restored: ${save.origin.mediaId}, result=${cache}" }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to restore cache for ${save.origin.mediaId}" }
                    }

                }
            }
        }
    }

    override val listFlow: MutableStateFlow<List<MediaCache>> = MutableStateFlow(emptyList())

    override suspend fun findCache(media: Media, resume: Boolean): MediaCache? {
        return lock.withLock {
            listFlow.first().firstOrNull { it.origin.mediaId == media.mediaId }
        }?.also {
            if (resume) {
                it.resume()
            }
        }
    }

    override val cacheMediaSource: MediaSource by lazy { MediaCacheStorageSource(this) }

    override val count = listFlow.map {
        it.size
    }
    override val totalSize: Flow<FileSize> = listFlow.flatMapLatest { caches ->
        combine(caches.map { it.totalSize }) { sizes ->
            sizes.sumOf { it.inBytes }.bytes
        }
    }

    /**
     * Locks accesses to [listFlow]
     */
    private val lock = Mutex()

    override suspend fun cache(media: Media, metadata: MediaCacheMetadata, resume: Boolean): MediaCache {
        return lock.withLock {
            listFlow.value.firstOrNull { it.origin.mediaId == media.mediaId }?.let { return@withLock it }

            val cache = engine.createCache(
                media, metadata,
                scope.coroutineContext
            )
            withContext(Dispatchers.IO) {
                dir.resolve("${media.mediaId.hashCode()}.${METADATA_FILE_EXTENSION}").writeText(
                    json.encodeToString(
                        MediaCacheSave.serializer(),
                        MediaCacheSave(media, cache.metadata)
                    )
                )
            }
            listFlow.value += cache
            cache
        }.also {
            if (resume) {
                it.resume()
            }
        }
    }

    override suspend fun delete(media: Media): Boolean {
        lock.withLock {
            val cache = listFlow.value.firstOrNull { it.origin.mediaId == media.mediaId } ?: return false
            cache.delete()
            withContext(Dispatchers.IO) {
                dir.resolve("${media.mediaId}.${METADATA_FILE_EXTENSION}").deleteIfExists()
            }
            listFlow.value -= cache
            return true
        }
    }

    override fun close() {
        scope.cancel()
    }
}

private class MediaCacheStorageSource(
    private val storage: MediaCacheStorage,
) : MediaSource {
    override val mediaSourceId: String get() = storage.mediaSourceId
    override val location: MediaSourceLocation get() = MediaSourceLocation.LOCAL

    override suspend fun checkConnection(): ConnectionStatus = ConnectionStatus.SUCCESS

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
        return SingleShotPagedSource {
            storage.listFlow.first().mapNotNull { cache ->
                val kind = query.matches(cache.metadata)
                if (kind == MatchKind.NONE) null
                else MediaMatch(cache.getCachedMedia(), kind)
            }.asFlow()
        }
    }
}