package com.radustavila.nasaexplorer.data.climate.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.ClimateRepository
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.ClimateSeries
import com.radustavila.nasaexplorer.data.climate.local.ClimateLocalDataSource
import com.radustavila.nasaexplorer.data.climate.remote.ClimateRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first climate records: screens observe Room, and [refresh] is the only path from the
 * files into it. A refresh replaces one indicator's values; a failure keeps the last good ones.
 */
@Singleton
internal class ClimateRepositoryImpl @Inject constructor(
    private val localDataSource: ClimateLocalDataSource,
    private val remoteDataSource: ClimateRemoteDataSource,
    private val timeProvider: TimeProvider,
) : ClimateRepository {

    override fun observeSeries(): Flow<List<ClimateSeries>> = localDataSource.observeSeries()

    override suspend fun refresh(indicator: ClimateIndicator): DataResult<Int> =
        remoteDataSource.fetch(indicator).flatMap { points ->
            localDataSource.replace(indicator, points, timeProvider.now().toEpochMilli()).map { points.size }
        }
}
