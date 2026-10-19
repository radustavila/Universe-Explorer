package com.radustavila.nasaexplorer.data.launches.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRefreshOutcome
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.data.launches.local.LaunchLocalDataSource
import com.radustavila.nasaexplorer.data.launches.local.LaunchRefreshThrottle
import com.radustavila.nasaexplorer.data.launches.mapper.toEntityOrNull
import com.radustavila.nasaexplorer.data.launches.remote.LaunchRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first launches: the card observes Room, and [refreshUpcomingLaunches] is the only path
 * from Launch Library 2 into it. Every failure leaves the cached launches untouched.
 */
@Singleton
class LaunchRepositoryImpl @Inject constructor(
    private val localDataSource: LaunchLocalDataSource,
    private val remoteDataSource: LaunchRemoteDataSource,
    private val throttle: LaunchRefreshThrottle,
    private val timeProvider: TimeProvider,
) : LaunchRepository {

    override fun observeUpcomingLaunches(): Flow<List<Launch>> = localDataSource.observeUpcoming()

    override suspend fun refreshUpcomingLaunches(): DataResult<LaunchRefreshOutcome> {
        if (!throttle.tryAcquire()) return DataResult.Success(LaunchRefreshOutcome.SKIPPED)

        return when (val result = remoteDataSource.fetchUpcoming()) {
            is DataResult.Success -> {
                val entities = result.data.mapNotNull { dto -> dto.toEntityOrNull() }
                if (result.data.isNotEmpty() && entities.isEmpty()) {
                    // Every entry was unusable: keep showing the old launches instead of none.
                    DataResult.Failure(AppError.EmptyResponse)
                } else {
                    localDataSource.replaceAll(entities).map { LaunchRefreshOutcome.REFRESHED }
                }
            }

            is DataResult.Failure -> {
                (result.error as? AppError.RateLimited)?.retryAfterSeconds?.let { seconds ->
                    throttle.deferUntil(timeProvider.now().plusSeconds(seconds))
                }
                result
            }
        }
    }
}
