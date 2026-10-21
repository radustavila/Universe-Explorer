package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.domain.storage.StorageEntry
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Measures every contributed [StorageUsageSource]: one entry per kind, in [StorageKind]
 * declaration order. A source that fails to measure is left out rather than failing the rest.
 *
 * @see com.radustavila.nasaexplorer.core.domain.storage.StorageKind
 */
class MeasureStorageUseCase @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards StorageUsageSource>,
) {
    suspend operator fun invoke(): List<StorageEntry> =
        sources.flatMap { source -> source.measureOrEmpty() }
            .groupBy(StorageEntry::kind)
            .map { (kind, entries) -> StorageEntry(kind, entries.sumOf(StorageEntry::bytes)) }
            .sortedBy { it.kind.ordinal }

    private suspend fun StorageUsageSource.measureOrEmpty(): List<StorageEntry> = try {
        measure()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * "Clear cache": runs every [CacheCleaner] (images, HTTP responses, the feeds' local copies), then
 * makes every source due for a refresh, so emptied screens fill again the next time they ask
 * instead of waiting out their refresh interval. Favourites are never touched.
 *
 * @return false when a cleaner failed; the others still ran.
 */
class ClearCacheUseCase @Inject constructor(
    private val cleaners: Set<@JvmSuppressWildcards CacheCleaner>,
    private val syncMetadataRepository: SyncMetadataRepository,
) {
    suspend operator fun invoke(): Boolean {
        var allCleared = true
        cleaners.forEach { cleaner ->
            try {
                cleaner.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                allCleared = false
            }
        }
        syncMetadataRepository.allowRefreshNow()
        return allCleared
    }
}
