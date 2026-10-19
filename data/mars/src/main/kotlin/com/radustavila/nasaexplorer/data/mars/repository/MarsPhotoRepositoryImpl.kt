package com.radustavila.nasaexplorer.data.mars.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.errorOrNull
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.common.result.getOrNull
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.MarsPhotoRepository
import com.radustavila.nasaexplorer.core.model.MarsPhoto
import com.radustavila.nasaexplorer.core.model.MarsRover
import com.radustavila.nasaexplorer.data.mars.local.MarsLocalDataSource
import com.radustavila.nasaexplorer.data.mars.mapper.toDomainOrNull
import com.radustavila.nasaexplorer.data.mars.remote.MarsRemoteDataSource
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Room is the source of truth; [refresh] downloads both rovers at once (Curiosity answers in under
 * a second, Perseverance can take a minute and a half) and stores each one as soon as it answers.
 */
@Singleton
class MarsPhotoRepositoryImpl @Inject constructor(
    private val localDataSource: MarsLocalDataSource,
    private val remoteDataSource: MarsRemoteDataSource,
    private val timeProvider: TimeProvider,
) : MarsPhotoRepository {

    override fun observeRoverPhotos(rover: MarsRover): Flow<List<MarsPhoto>> =
        localDataSource.observeRover(rover)

    override fun observeLatest(perRover: Int): Flow<List<MarsPhoto>> =
        combine(MarsRover.entries.map { localDataSource.observeRover(it, limit = perRover) }) { perRoverLists ->
            perRoverLists.toList().flatten().sortedWith(NEWEST_FIRST)
        }

    override fun observePhoto(id: String): Flow<MarsPhoto?> = localDataSource.observePhoto(id)

    override suspend fun refresh(): DataResult<Int> = coroutineScope {
        // Each rover is stored as soon as it answers, so Curiosity's photos reach the rail while
        // Perseverance's feed is still thinking.
        val outcomes = listOf(
            async {
                remoteDataSource.fetchCuriosity()
                    .map { page -> page.items.mapNotNull { it.toDomainOrNull() } }
                    .flatMap { photos -> store(photos) }
            },
            async {
                remoteDataSource.fetchPerseverance()
                    .map { page -> page.images.mapNotNull { it.toDomainOrNull() } }
                    .flatMap { photos -> store(photos) }
            },
        ).awaitAll()

        outcomes.firstNotNullOfOrNull { it.errorOrNull() }?.let { error -> DataResult.Failure(error) }
            ?: DataResult.Success(outcomes.sumOf { it.getOrNull() ?: 0 })
    }

    /** A feed that answered with nothing usable is as good as one that failed. */
    private suspend fun store(photos: List<MarsPhoto>): DataResult<Int> =
        if (photos.isEmpty()) {
            DataResult.Failure(AppError.EmptyResponse)
        } else {
            localDataSource.store(photos, timeProvider.now()).map { photos.size }
        }

    private companion object {
        val NEWEST_FIRST: Comparator<MarsPhoto> =
            compareByDescending<MarsPhoto> { it.takenAt ?: Instant.MIN }.thenBy { it.id }
    }
}
