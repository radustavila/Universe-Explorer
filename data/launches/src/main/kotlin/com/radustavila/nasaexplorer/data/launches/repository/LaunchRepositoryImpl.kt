package com.radustavila.nasaexplorer.data.launches.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.data.launches.local.LaunchLocalDataSource
import com.radustavila.nasaexplorer.data.launches.mapper.toEntityOrNull
import com.radustavila.nasaexplorer.data.launches.remote.LaunchRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first launches: the card observes Room, and [refreshUpcomingLaunches] is the only path
 * from Launch Library 2 into it. Every failure leaves the cached launches untouched.
 *
 * How often it runs is decided above it (`SynchronizeUpcomingLaunchesUseCase`), and the shared
 * HTTP client's rate limiter keeps every caller inside LL2's hourly budget.
 */
@Singleton
class LaunchRepositoryImpl @Inject constructor(
    private val localDataSource: LaunchLocalDataSource,
    private val remoteDataSource: LaunchRemoteDataSource,
) : LaunchRepository {

    override fun observeUpcomingLaunches(): Flow<List<Launch>> = localDataSource.observeUpcoming()

    override suspend fun refreshUpcomingLaunches(): DataResult<Int> =
        when (val result = remoteDataSource.fetchUpcoming()) {
            is DataResult.Success -> {
                val entities = result.data.mapNotNull { dto -> dto.toEntityOrNull() }
                if (result.data.isNotEmpty() && entities.isEmpty()) {
                    // Every entry was unusable: keep showing the old launches instead of none.
                    DataResult.Failure(AppError.EmptyResponse)
                } else {
                    localDataSource.replaceAll(entities).map { entities.size }
                }
            }

            is DataResult.Failure -> result
        }
}
