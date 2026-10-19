package com.radustavila.nasaexplorer.data.exoplanets.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.getOrNull
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.ExoplanetRepository
import com.radustavila.nasaexplorer.core.model.Exoplanet
import com.radustavila.nasaexplorer.core.model.ExoplanetCatalogStats
import com.radustavila.nasaexplorer.core.model.ExoplanetList
import com.radustavila.nasaexplorer.data.exoplanets.local.ExoplanetCatalogStatsEntity
import com.radustavila.nasaexplorer.data.exoplanets.local.ExoplanetLocalDataSource
import com.radustavila.nasaexplorer.data.exoplanets.mapper.toEntityOrNull
import com.radustavila.nasaexplorer.data.exoplanets.remote.ExoplanetRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first exoplanets: screens observe Room, and [refresh] is the only path from the archive
 * into it. A failed or unusable download leaves the cached catalogue untouched.
 *
 * How often [refresh] runs is decided above it (`SynchronizeExoplanetsUseCase`, weekly).
 */
@Singleton
class ExoplanetRepositoryImpl @Inject constructor(
    private val localDataSource: ExoplanetLocalDataSource,
    private val remoteDataSource: ExoplanetRemoteDataSource,
    private val timeProvider: TimeProvider,
) : ExoplanetRepository {

    override fun observeList(list: ExoplanetList): Flow<List<Exoplanet>> =
        localDataSource.observeList(list, newestSinceYear = newestSinceYear())

    override fun observeExoplanet(name: String): Flow<Exoplanet?> = localDataSource.observeExoplanet(name)

    override fun observeCatalogStats(): Flow<ExoplanetCatalogStats?> = localDataSource.observeStats()

    override suspend fun refresh(): DataResult<Int> {
        val catalog = when (val result = remoteDataSource.fetchCatalog(newestSinceYear())) {
            is DataResult.Success -> result.data
            is DataResult.Failure -> return result
        }
        val fetchedAt = timeProvider.now().toEpochMilli()
        val entities = catalog.mapNotNull { dto -> dto.toEntityOrNull(fetchedAt) }
        if (entities.isEmpty()) return DataResult.Failure(AppError.EmptyResponse)

        // The total is decoration: a failed count keeps the previous one rather than failing the
        // refresh that already brought the planets.
        val stats = remoteDataSource.fetchConfirmedCount().getOrNull()?.let { count ->
            ExoplanetCatalogStatsEntity(confirmedPlanets = count, fetchedAtMillis = fetchedAt)
        }
        return localDataSource.storeRefresh(entities, stats).map { entities.size }
    }

    /**
     * "Newest" covers this year and the previous one, so the list is not nearly empty in January.
     */
    private fun newestSinceYear(): Int = timeProvider.today().year - 1
}
