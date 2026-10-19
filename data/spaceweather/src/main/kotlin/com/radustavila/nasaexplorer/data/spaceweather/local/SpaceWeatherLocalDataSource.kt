package com.radustavila.nasaexplorer.data.spaceweather.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Local boundary of the space weather module: the source of truth the screen observes. */
interface SpaceWeatherLocalDataSource {
    fun observeKpReadings(): Flow<List<KpReadingEntity>>
    fun observeCurrentKp(): Flow<CurrentKpEntity?>
    fun observeAuroraForecast(): Flow<AuroraForecastEntity?>
    fun observeLatestFlare(): Flow<SolarFlareEntity?>
    fun observeLatestCme(): Flow<CoronalMassEjectionEntity?>
    suspend fun auroraForecastFetchedAt(): DataResult<Long?>
    suspend fun saveKp(readings: List<KpReadingEntity>, current: CurrentKpEntity?): DataResult<Unit>
    suspend fun saveAuroraForecast(entity: AuroraForecastEntity): DataResult<Unit>
    suspend fun saveSolarActivity(flares: List<SolarFlareEntity>, cmes: List<CoronalMassEjectionEntity>): DataResult<Unit>
}

@Singleton
class RoomSpaceWeatherLocalDataSource @Inject constructor(
    private val dao: SpaceWeatherDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : SpaceWeatherLocalDataSource {

    override fun observeKpReadings(): Flow<List<KpReadingEntity>> = dao.observeKpReadings().flowOn(ioDispatcher)

    override fun observeCurrentKp(): Flow<CurrentKpEntity?> = dao.observeCurrentKp().flowOn(ioDispatcher)

    override fun observeAuroraForecast(): Flow<AuroraForecastEntity?> = dao.observeAuroraForecast().flowOn(ioDispatcher)

    override fun observeLatestFlare(): Flow<SolarFlareEntity?> = dao.observeLatestFlare().flowOn(ioDispatcher)

    override fun observeLatestCme(): Flow<CoronalMassEjectionEntity?> = dao.observeLatestCme().flowOn(ioDispatcher)

    override suspend fun auroraForecastFetchedAt(): DataResult<Long?> = databaseCall { dao.auroraForecastFetchedAt() }

    override suspend fun saveKp(readings: List<KpReadingEntity>, current: CurrentKpEntity?): DataResult<Unit> =
        databaseCall {
            dao.upsertKpReadings(readings)
            current?.let { dao.upsertCurrentKp(it) }
        }

    override suspend fun saveAuroraForecast(entity: AuroraForecastEntity): DataResult<Unit> =
        databaseCall { dao.upsertAuroraForecast(entity) }

    override suspend fun saveSolarActivity(
        flares: List<SolarFlareEntity>,
        cmes: List<CoronalMassEjectionEntity>,
    ): DataResult<Unit> = databaseCall { dao.upsertSolarActivity(flares, cmes) }

    private suspend fun <T> databaseCall(block: suspend () -> T): DataResult<T> = withContext(ioDispatcher) {
        try {
            DataResult.Success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            DataResult.Failure(AppError.Database(throwable.message))
        }
    }
}
