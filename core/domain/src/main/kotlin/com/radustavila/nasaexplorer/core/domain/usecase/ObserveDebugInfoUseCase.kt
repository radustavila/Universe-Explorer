package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.model.DebugInfo
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combines every observable signal of the offline-first pipeline into one snapshot, so the debug
 * screen can stay a dumb renderer.
 */
class ObserveDebugInfoUseCase @Inject constructor(
    private val apodRepository: ApodRepository,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val syncScheduler: SyncScheduler,
    private val networkMonitor: NetworkMonitor,
) {
    operator fun invoke(): Flow<DebugInfo> {
        val cacheStats: Flow<CacheStats> = combine(
            apodRepository.observeCachedCount(),
            apodRepository.observeFavoritesCount(),
            apodRepository.observeOldestCachedDate(),
        ) { cached, favorites, oldestCached -> CacheStats(cached, favorites, oldestCached) }

        val syncStats: Flow<SyncStats> = combine(
            syncMetadataRepository.lastSuccessfulSync,
            syncMetadataRepository.oldestRequestedDate,
            syncScheduler.syncState,
        ) { lastSync, oldestRequested, syncState ->
            SyncStats(lastSync, oldestRequested, syncState)
        }

        return combine(networkMonitor.isOnline, cacheStats, syncStats) { isOnline, cache, sync ->
            DebugInfo(
                isOnline = isOnline,
                lastSuccessfulSync = sync.lastSuccessfulSync,
                cachedApodCount = cache.cachedApodCount,
                favoriteCount = cache.favoriteCount,
                oldestCachedDate = cache.oldestCachedDate,
                oldestRequestedDate = sync.oldestRequestedDate,
                syncState = sync.syncState,
            )
        }
    }

    private data class CacheStats(
        val cachedApodCount: Int,
        val favoriteCount: Int,
        val oldestCachedDate: LocalDate?,
    )

    private data class SyncStats(
        val lastSuccessfulSync: Instant?,
        val oldestRequestedDate: LocalDate?,
        val syncState: com.radustavila.nasaexplorer.core.model.SyncState,
    )
}
