package com.radustavila.nasaexplorer.data.nasa.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.data.nasa.local.ApodLocalDataSource
import com.radustavila.nasaexplorer.data.nasa.mapper.toEntities
import com.radustavila.nasaexplorer.data.nasa.remote.ApodRemoteDataSource
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Offline-first APOD repository.
 *
 * Reads always come from the database, so the UI renders cached content immediately and keeps
 * rendering it without a network. Remote calls only ever *write* into the database; the resulting
 * Room [Flow] emission is what updates the UI.
 *
 * Pagination is expressed as date windows because the APOD API has no page cursor: the newest window
 * is `[today - 6, today]`, and every "load more" walks one window further into the archive, bounded
 * by the first APOD ever published.
 *
 * Favourites are not stored on the entries: every read is combined with the [FavoriteSource.APOD]
 * ids of the shared favourites table, so a heart toggled anywhere shows up everywhere.
 */
@Singleton
class ApodRepositoryImpl @Inject constructor(
    private val localDataSource: ApodLocalDataSource,
    private val remoteDataSource: ApodRemoteDataSource,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val favoritesRepository: FavoritesRepository,
    private val timeProvider: TimeProvider,
) : ApodRepository {

    private val favoriteDates: Flow<Set<String>> =
        favoritesRepository.observeFavoriteIds(FavoriteSource.APOD)

    override fun observeFeed(): Flow<List<Apod>> =
        combine(localDataSource.observeFeed(), favoriteDates) { feed, favorites ->
            feed.map { apod -> apod.markFavorite(favorites) }
        }

    override fun observeLatestApod(): Flow<Apod?> =
        combine(localDataSource.observeLatest(), favoriteDates) { apod, favorites ->
            apod?.markFavorite(favorites)
        }

    override fun observeApod(date: LocalDate): Flow<Apod?> =
        combine(localDataSource.observeApod(date), favoriteDates) { apod, favorites ->
            apod?.markFavorite(favorites)
        }

    override fun observeCachedCount(): Flow<Int> = localDataSource.observeCount()

    override fun observeOldestCachedDate(): Flow<LocalDate?> = localDataSource.observeOldestDate()

    private fun Apod.markFavorite(favoriteDates: Set<String>): Apod =
        copy(isFavorite = date.toString() in favoriteDates)

    override suspend fun refreshRecent(): DataResult<Int> {
        val today = timeProvider.today()
        val startDate = today.minusDays((WINDOW_SIZE_DAYS - 1).toLong())

        return fetchWindowTolerantOfUnpublishedToday(startDate, today)
            .flatMap { dtos -> persist(dtos) }
            .also { result ->
                if (result is DataResult.Success && syncMetadataRepository.oldestRequestedDate.first() == null) {
                    syncMetadataRepository.setOldestRequestedDate(startDate)
                }
            }
    }

    override suspend fun fetchApod(date: LocalDate): DataResult<Unit> =
        remoteDataSource.fetchSingle(date)
            // Kept out of the feed: an isolated day from the far side of the archive would otherwise
            // appear directly below the current window. Paging absorbs it when it reaches that date.
            .flatMap { dto -> persist(listOf(dto), isInFeed = false) }
            .map { }

    override suspend fun loadOlderWindow(): DataResult<Int> {
        val oldestRequested = syncMetadataRepository.oldestRequestedDate.first()
            ?: timeProvider.today().minusDays((WINDOW_SIZE_DAYS - 1).toLong())

        val endDate = oldestRequested.minusDays(1)
        if (endDate.isBefore(Apod.FIRST_PUBLICATION_DATE)) {
            return DataResult.Success(0)
        }

        val startDate = maxOf(
            endDate.minusDays((WINDOW_SIZE_DAYS - 1).toLong()),
            Apod.FIRST_PUBLICATION_DATE,
        )

        return remoteDataSource.fetchRange(startDate = startDate, endDate = endDate)
            .flatMap { dtos -> persist(dtos) }
            .also { result ->
                if (result is DataResult.Success) {
                    syncMetadataRepository.setOldestRequestedDate(startDate)
                }
            }
    }

    /**
     * NASA rejects a range whose `end_date` is beyond the most recently published entry, which
     * happens every day between UTC midnight and publication time. One retry against yesterday keeps
     * the morning refresh working instead of surfacing a spurious HTTP 400.
     */
    private suspend fun fetchWindowTolerantOfUnpublishedToday(
        startDate: LocalDate,
        endDate: LocalDate,
    ): DataResult<List<com.radustavila.nasaexplorer.data.nasa.remote.dto.ApodDto>> {
        val result = remoteDataSource.fetchRange(startDate = startDate, endDate = endDate)
        val shouldRetryWithoutToday = result is DataResult.Failure &&
            (result.error as? AppError.Http)?.code == HTTP_BAD_REQUEST
        if (!shouldRetryWithoutToday) return result

        return remoteDataSource.fetchRange(
            startDate = startDate.minusDays(1),
            endDate = endDate.minusDays(1),
        )
    }

    private suspend fun persist(
        dtos: List<com.radustavila.nasaexplorer.data.nasa.remote.dto.ApodDto>,
        isInFeed: Boolean = true,
    ): DataResult<Int> {
        val entities = dtos.toEntities(cachedAt = timeProvider.now(), isInFeed = isInFeed)
        if (entities.isEmpty()) return DataResult.Failure(AppError.EmptyResponse)

        return localDataSource.upsertPreservingLocalState(entities).map { entities.size }
    }

    private companion object {
        /** One week per request: few round-trips, small payloads, matches the feed's rhythm. */
        const val WINDOW_SIZE_DAYS = 7
        const val HTTP_BAD_REQUEST = 400
    }
}
