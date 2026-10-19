package com.radustavila.nasaexplorer.data.epic.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.common.result.getOrNull
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.database.entity.EpicImageEntity
import com.radustavila.nasaexplorer.core.domain.repository.EpicRepository
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.data.epic.di.EpicArchiveBaseUrl
import com.radustavila.nasaexplorer.data.epic.local.EpicLocalDataSource
import com.radustavila.nasaexplorer.data.epic.mapper.toDates
import com.radustavila.nasaexplorer.data.epic.mapper.toEntities
import com.radustavila.nasaexplorer.data.epic.prefetch.ImagePrefetcher
import com.radustavila.nasaexplorer.data.epic.remote.EpicRemoteDataSource
import com.radustavila.nasaexplorer.data.epic.remote.dto.EpicImageDto
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first EPIC repository: screens read Room, the network only writes into it.
 *
 * The cache holds the newest [RETENTION_DAYS] days of photo metadata per collection, measured from
 * the newest published day rather than from today: EPIC publishes with a lag of a day or more and
 * pauses for calibration, so "today" may have nothing yet.
 */
@Singleton
class EpicRepositoryImpl @Inject constructor(
    private val localDataSource: EpicLocalDataSource,
    private val remoteDataSource: EpicRemoteDataSource,
    private val imagePrefetcher: ImagePrefetcher,
    private val timeProvider: TimeProvider,
    @param:EpicArchiveBaseUrl private val archiveBaseUrl: String,
) : EpicRepository {

    override fun observeLatestDay(collection: EpicCollection): Flow<List<EpicImage>> =
        localDataSource.observeLatestDay(collection)

    override fun observeDay(collection: EpicCollection, date: LocalDate): Flow<List<EpicImage>> =
        localDataSource.observeDay(collection, date)

    override fun observeAvailableDates(collection: EpicCollection): Flow<List<LocalDate>> =
        localDataSource.observeAvailableDates(collection)

    override suspend fun refreshLatest(collection: EpicCollection): DataResult<Int> =
        remoteDataSource.fetchLatest(collection)
            .flatMap { dtos -> toEntitiesOrEmpty(collection, dtos) }
            .flatMap { entities ->
                val newestDay = LocalDate.parse(entities.maxOf(EpicImageEntity::date))
                localDataSource.replaceDays(
                    collection = collection,
                    images = entities,
                    keepFrom = newestDay.minusDays((RETENTION_DAYS - 1).toLong()),
                )
                    .flatMap { refreshAvailableDatesIfMissing(collection, newestDay) }
                    .map { entities.count { it.date == newestDay.toString() } }
            }

    override suspend fun fetchDay(collection: EpicCollection, date: LocalDate): DataResult<Int> =
        remoteDataSource.fetchDay(collection, date)
            .flatMap { dtos -> toEntitiesOrEmpty(collection, dtos) }
            .flatMap { entities ->
                // Never trims: an archive day is outside the window by definition, and the next
                // sync is what drops it again.
                localDataSource.replaceDays(collection = collection, images = entities, keepFrom = null)
                    .map { entities.size }
            }

    override suspend fun prefetchLatestThumbnails(collection: EpicCollection) {
        val images = localDataSource.latestDay(collection).getOrNull().orEmpty()
        imagePrefetcher.prefetch(images.map(EpicImage::thumbnailUrl))
    }

    /**
     * The archive listing only changes when a new day is published, so it is fetched when the
     * newest day is not in it yet (which includes the very first sync) instead of on every run.
     */
    private suspend fun refreshAvailableDatesIfMissing(
        collection: EpicCollection,
        newestDay: LocalDate,
    ): DataResult<Unit> {
        val isKnown = localDataSource.isDateAvailable(collection, newestDay)
        if (isKnown is DataResult.Failure) return isKnown
        if ((isKnown as DataResult.Success).data) return DataResult.Success(Unit)

        return remoteDataSource.fetchAvailableDates(collection).flatMap { dtos ->
            val dates = dtos.toDates()
            if (dates.isEmpty()) {
                DataResult.Failure(AppError.EmptyResponse)
            } else {
                localDataSource.replaceAvailableDates(collection, dates)
            }
        }
    }

    private fun toEntitiesOrEmpty(
        collection: EpicCollection,
        dtos: List<EpicImageDto>,
    ): DataResult<List<EpicImageEntity>> {
        val entities = dtos.toEntities(collection, timeProvider.now(), archiveBaseUrl)
        return if (entities.isEmpty()) DataResult.Failure(AppError.EmptyResponse) else DataResult.Success(entities)
    }

    companion object {
        /** A week of days: enough to step back a few days offline, small enough to never matter. */
        const val RETENTION_DAYS = 7
    }
}
