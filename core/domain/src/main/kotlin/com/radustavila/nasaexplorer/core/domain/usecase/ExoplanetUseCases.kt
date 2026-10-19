package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.ExoplanetRepository
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.sync.ExoplanetSyncScheduler
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.Exoplanet
import com.radustavila.nasaexplorer.core.model.ExoplanetCatalogStats
import com.radustavila.nasaexplorer.core.model.ExoplanetList
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveExoplanetsUseCase @Inject constructor(
    private val repository: ExoplanetRepository,
) {
    operator fun invoke(list: ExoplanetList): Flow<List<Exoplanet>> = repository.observeList(list)
}

class ObserveExoplanetUseCase @Inject constructor(
    private val repository: ExoplanetRepository,
) {
    operator fun invoke(name: String): Flow<Exoplanet?> = repository.observeExoplanet(name)
}

class ObserveExoplanetCatalogStatsUseCase @Inject constructor(
    private val repository: ExoplanetRepository,
) {
    operator fun invoke(): Flow<ExoplanetCatalogStats?> = repository.observeCatalogStats()
}

/**
 * Refreshes the exoplanet catalogue through [RefreshSourceUseCase], at most once per
 * [REFRESH_INTERVAL] unless [force]d. The archive adds a few hundred planets a year, so a weekly
 * refresh keeps the "newest" list current without leaning on a service that publishes no quota.
 */
class SynchronizeExoplanetsUseCase @Inject constructor(
    private val repository: ExoplanetRepository,
    private val refreshSource: RefreshSourceUseCase,
) {
    suspend operator fun invoke(force: Boolean = false): SourceRefreshResult<Int> =
        refreshSource(SyncSource.EXOPLANETS, minInterval = REFRESH_INTERVAL, force = force) {
            repository.refresh()
        }

    companion object {
        val REFRESH_INTERVAL: Duration = Duration.ofDays(7)
    }
}

/** What the Exoplanets screen shows about its last refresh. */
data class ExoplanetSyncStatus(
    val isSyncing: Boolean = false,
    val lastSuccessAt: Instant? = null,
    /** Whether the most recent attempt failed; the cached catalogue is still shown. */
    val lastAttemptFailed: Boolean = false,
)

class ObserveExoplanetSyncStatusUseCase @Inject constructor(
    private val scheduler: ExoplanetSyncScheduler,
    private val syncMetadataRepository: SyncMetadataRepository,
) {
    operator fun invoke(): Flow<ExoplanetSyncStatus> = combine(
        scheduler.isSyncing,
        syncMetadataRepository.observeSourceStatus(SyncSource.EXOPLANETS),
    ) { isSyncing, status ->
        ExoplanetSyncStatus(
            isSyncing = isSyncing,
            lastSuccessAt = status.lastSuccessAt,
            lastAttemptFailed = status.isFailing,
        )
    }
}

/** Asks for a catalogue refresh; the background job decides whether it is due. */
class RequestExoplanetSyncUseCase @Inject constructor(
    private val scheduler: ExoplanetSyncScheduler,
) {
    operator fun invoke(force: Boolean = false) = scheduler.requestSync(force)
}
