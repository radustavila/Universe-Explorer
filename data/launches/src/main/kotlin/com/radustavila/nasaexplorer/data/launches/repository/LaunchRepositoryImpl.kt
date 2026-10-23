package com.radustavila.nasaexplorer.data.launches.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.data.launches.local.LaunchEntity
import com.radustavila.nasaexplorer.data.launches.local.LaunchLocalDataSource
import com.radustavila.nasaexplorer.data.launches.mapper.toEntityOrNull
import com.radustavila.nasaexplorer.data.launches.remote.LaunchRemoteDataSource
import com.radustavila.nasaexplorer.data.launches.remote.dto.LaunchDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first launches: the card and the Launches screen observe Room, and the refreshes are the
 * only paths from Launch Library 2 into it, one call each. Every failure leaves the cached launches
 * untouched.
 *
 * How often they run is decided above it (`SynchronizeUpcomingLaunchesUseCase`,
 * `RefreshPastLaunchesUseCase`, `RemindOfLaunchesUseCase`), and the shared HTTP client's rate limiter
 * keeps every caller inside LL2's hourly budget.
 */
@Singleton
class LaunchRepositoryImpl @Inject constructor(
    private val localDataSource: LaunchLocalDataSource,
    private val remoteDataSource: LaunchRemoteDataSource,
) : LaunchRepository {

    override fun observeUpcomingLaunches(): Flow<List<Launch>> = localDataSource.observeUpcoming()

    override fun observePastLaunches(): Flow<List<Launch>> = localDataSource.observePast()

    override fun observeLaunch(id: String): Flow<Launch?> = localDataSource.observeLaunch(id)

    override suspend fun refreshUpcomingLaunches(): DataResult<Int> =
        replace(LaunchEntity.TIMELINE_UPCOMING, remoteDataSource.fetchUpcoming())

    override suspend fun refreshPastLaunches(): DataResult<Int> =
        replace(LaunchEntity.TIMELINE_PAST, remoteDataSource.fetchPrevious())

    override suspend fun refreshLaunch(id: String): DataResult<Launch> =
        when (val result = remoteDataSource.fetchLaunch(id)) {
            is DataResult.Success -> {
                val entity = result.data.toEntityOrNull()
                if (entity == null) {
                    DataResult.Failure(AppError.EmptyResponse)
                } else {
                    localDataSource.updateLaunch(entity, fallbackTimeline = LaunchEntity.TIMELINE_UPCOMING)
                }
            }

            is DataResult.Failure -> result
        }

    private suspend fun replace(timeline: String, result: DataResult<List<LaunchDto>>): DataResult<Int> =
        when (result) {
            is DataResult.Success -> {
                val entities = result.data.mapNotNull { dto -> dto.toEntityOrNull(timeline) }
                if (result.data.isNotEmpty() && entities.isEmpty()) {
                    // Every entry was unusable: keep showing the old launches instead of none.
                    DataResult.Failure(AppError.EmptyResponse)
                } else {
                    localDataSource.replaceTimeline(timeline, entities).map { entities.size }
                }
            }

            is DataResult.Failure -> result
        }
}
