package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.SmallBodiesRepository
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.sync.SmallBodiesSyncScheduler
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.CloseApproach
import com.radustavila.nasaexplorer.core.model.Fireball
import com.radustavila.nasaexplorer.core.model.SmallBodyDetails
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveCloseApproachesUseCase @Inject constructor(
    private val repository: SmallBodiesRepository,
) {
    operator fun invoke(): Flow<List<CloseApproach>> = repository.observeCloseApproaches()
}

class ObserveCloseApproachUseCase @Inject constructor(
    private val repository: SmallBodiesRepository,
) {
    operator fun invoke(designation: String): Flow<CloseApproach?> = repository.observeCloseApproach(designation)
}

class ObserveRecentFireballsUseCase @Inject constructor(
    private val repository: SmallBodiesRepository,
) {
    operator fun invoke(): Flow<List<Fireball>> = repository.observeRecentFireballs()
}

class ObserveSmallBodyDetailsUseCase @Inject constructor(
    private val repository: SmallBodiesRepository,
) {
    operator fun invoke(designation: String): Flow<SmallBodyDetails?> = repository.observeDetails(designation)
}

class FetchSmallBodyDetailsUseCase @Inject constructor(
    private val repository: SmallBodiesRepository,
) {
    suspend operator fun invoke(designation: String): DataResult<SmallBodyDetails> =
        repository.fetchDetails(designation)
}

/**
 * Refreshes close approaches, the Sentry list and fireballs through [RefreshSourceUseCase], at most
 * once per [REFRESH_INTERVAL] unless [force]d. The 30-day window moves by a day a day.
 */
class SynchronizeSmallBodiesUseCase @Inject constructor(
    private val repository: SmallBodiesRepository,
    private val refreshSource: RefreshSourceUseCase,
) {
    suspend operator fun invoke(force: Boolean = false): SourceRefreshResult<Unit> =
        refreshSource(SyncSource.ASTEROIDS, minInterval = REFRESH_INTERVAL, force = force) {
            repository.refresh()
        }

    companion object {
        /** A little under the daily job's period, so a run that starts early is not skipped. */
        val REFRESH_INTERVAL: Duration = Duration.ofHours(20)
    }
}

/** What the neighbourhood screen shows about its last refresh. */
data class SmallBodiesSyncStatus(
    val isSyncing: Boolean = false,
    val lastSuccessAt: Instant? = null,
    val lastAttemptFailed: Boolean = false,
)

class ObserveSmallBodiesSyncStatusUseCase @Inject constructor(
    private val scheduler: SmallBodiesSyncScheduler,
    private val syncMetadataRepository: SyncMetadataRepository,
) {
    operator fun invoke(): Flow<SmallBodiesSyncStatus> = combine(
        scheduler.isSyncing,
        syncMetadataRepository.observeSourceStatus(SyncSource.ASTEROIDS),
    ) { isSyncing, status ->
        SmallBodiesSyncStatus(
            isSyncing = isSyncing,
            lastSuccessAt = status.lastSuccessAt,
            lastAttemptFailed = status.isFailing,
        )
    }
}

/** Asks for a refresh (the job skips while fresh) and keeps the daily one registered from then on. */
class RequestSmallBodiesSyncUseCase @Inject constructor(
    private val scheduler: SmallBodiesSyncScheduler,
) {
    operator fun invoke(force: Boolean = false) {
        scheduler.ensurePeriodicSync()
        scheduler.requestSync(force)
    }
}
