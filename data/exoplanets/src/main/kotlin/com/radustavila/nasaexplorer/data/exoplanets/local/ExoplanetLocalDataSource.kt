package com.radustavila.nasaexplorer.data.exoplanets.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.Exoplanet
import com.radustavila.nasaexplorer.core.model.ExoplanetCatalogStats
import com.radustavila.nasaexplorer.core.model.ExoplanetList
import com.radustavila.nasaexplorer.core.model.ExoplanetUnits
import com.radustavila.nasaexplorer.data.exoplanets.mapper.toDomain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local boundary of the exoplanets module: the source of truth every screen observes. */
interface ExoplanetLocalDataSource {
    fun observeList(list: ExoplanetList, newestSinceYear: Int): Flow<List<Exoplanet>>
    fun observeExoplanet(name: String): Flow<Exoplanet?>
    fun observeStats(): Flow<ExoplanetCatalogStats?>
    suspend fun storeRefresh(
        planets: List<ExoplanetEntity>,
        stats: ExoplanetCatalogStatsEntity?,
    ): DataResult<Unit>
}

@Singleton
class RoomExoplanetLocalDataSource @Inject constructor(
    private val dao: ExoplanetDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ExoplanetLocalDataSource {

    override fun observeList(list: ExoplanetList, newestSinceYear: Int): Flow<List<Exoplanet>> =
        when (list) {
            ExoplanetList.NEWEST -> dao.observeNewest(newestSinceYear)
            ExoplanetList.NEAREST -> dao.observeNearest(NEAREST_LIMIT)
            ExoplanetList.EARTH_SIZED -> dao.observeByRadius(
                minRadius = ExoplanetUnits.EARTH_SIZED_RADII.start,
                maxRadius = ExoplanetUnits.EARTH_SIZED_RADII.endInclusive,
            )
        }.map { entities -> entities.map(ExoplanetEntity::toDomain) }
            .flowOn(ioDispatcher)

    override fun observeExoplanet(name: String): Flow<Exoplanet?> =
        dao.observe(name).map { it?.toDomain() }.flowOn(ioDispatcher)

    override fun observeStats(): Flow<ExoplanetCatalogStats?> =
        dao.observeStats().map { it?.let { stats -> ExoplanetCatalogStats(stats.confirmedPlanets) } }
            .flowOn(ioDispatcher)

    override suspend fun storeRefresh(
        planets: List<ExoplanetEntity>,
        stats: ExoplanetCatalogStatsEntity?,
    ): DataResult<Unit> = withContext(ioDispatcher) {
        try {
            DataResult.Success(dao.storeRefresh(planets, stats))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            DataResult.Failure(AppError.Database(throwable.message))
        }
    }

    companion object {
        /** The nearest list stops here; the catalogue holds every planet within 15 parsecs. */
        const val NEAREST_LIMIT = 100
    }
}
