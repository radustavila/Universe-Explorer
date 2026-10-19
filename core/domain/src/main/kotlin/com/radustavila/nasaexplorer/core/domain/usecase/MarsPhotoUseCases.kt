package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.MarsPhotoRepository
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.sync.MarsSyncScheduler
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.MarsPhoto
import com.radustavila.nasaexplorer.core.model.MarsRover
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The Mars rail: each rover's newest cached photos, newest first. */
class ObserveLatestMarsPhotosUseCase @Inject constructor(
    private val repository: MarsPhotoRepository,
) {
    operator fun invoke(perRover: Int = RAIL_PHOTOS_PER_ROVER): Flow<List<MarsPhoto>> =
        repository.observeLatest(perRover)

    companion object {
        const val RAIL_PHOTOS_PER_ROVER = 8
    }
}

class ObserveMarsRoverPhotosUseCase @Inject constructor(
    private val repository: MarsPhotoRepository,
) {
    operator fun invoke(rover: MarsRover): Flow<List<MarsPhoto>> = repository.observeRoverPhotos(rover)
}

class ObserveMarsPhotoUseCase @Inject constructor(
    private val repository: MarsPhotoRepository,
) {
    operator fun invoke(id: String): Flow<MarsPhoto?> = repository.observePhoto(id)
}

/**
 * Refreshes the rover photos through [RefreshSourceUseCase], at most once per [REFRESH_INTERVAL]
 * unless [force]d. The rovers downlink once or twice a sol, so a daily refresh keeps the rail
 * current. Run by the background job only.
 */
class SynchronizeMarsPhotosUseCase @Inject constructor(
    private val repository: MarsPhotoRepository,
    private val refreshSource: RefreshSourceUseCase,
) {
    suspend operator fun invoke(force: Boolean = false): SourceRefreshResult<Int> =
        refreshSource(SyncSource.MARS, minInterval = REFRESH_INTERVAL, force = force) {
            repository.refresh()
        }

    companion object {
        val REFRESH_INTERVAL: Duration = Duration.ofDays(1)
    }
}

/**
 * Asks for a refresh (the background job decides whether it is due) and makes sure the daily one
 * is registered, so Mars is only ever downloaded for someone who has seen the rail.
 */
class RequestMarsSyncUseCase @Inject constructor(
    private val scheduler: MarsSyncScheduler,
) {
    operator fun invoke(force: Boolean = false) {
        scheduler.ensurePeriodicSync()
        scheduler.requestSync(force)
    }
}

/** What the Mars screens show about the last refresh. */
data class MarsSyncStatus(
    val isSyncing: Boolean = false,
    val lastSuccessAt: Instant? = null,
    /** Whether the most recent attempt failed; the cached photos are still shown. */
    val lastAttemptFailed: Boolean = false,
)

class ObserveMarsSyncStatusUseCase @Inject constructor(
    private val scheduler: MarsSyncScheduler,
    private val syncMetadataRepository: SyncMetadataRepository,
) {
    operator fun invoke(): Flow<MarsSyncStatus> = combine(
        scheduler.isSyncing,
        syncMetadataRepository.observeSourceStatus(SyncSource.MARS),
    ) { isSyncing, status ->
        MarsSyncStatus(
            isSyncing = isSyncing,
            lastSuccessAt = status.lastSuccessAt,
            lastAttemptFailed = status.isFailing,
        )
    }
}

/** Saves a rover photo to Favourites; the photo stays cached while it is a favourite. */
class SaveMarsPhotoFavoriteUseCase @Inject constructor(
    private val saveFavorite: SaveFavoriteUseCase,
) {
    suspend operator fun invoke(photo: MarsPhoto): DataResult<Unit> = saveFavorite(
        source = FavoriteSource.MARS,
        itemId = photo.id,
        title = photo.title,
        imageUrl = photo.previewUrl,
    )
}
