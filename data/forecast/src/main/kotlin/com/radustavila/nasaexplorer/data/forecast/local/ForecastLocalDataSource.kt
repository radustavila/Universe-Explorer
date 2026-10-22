package com.radustavila.nasaexplorer.data.forecast.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.CloudForecast
import com.radustavila.nasaexplorer.data.forecast.mapper.toDomain
import com.radustavila.nasaexplorer.data.forecast.remote.ForecastPlace
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Local boundary of the forecast module: the source of truth the screens observe. */
internal interface ForecastLocalDataSource {
    fun observeForecast(place: ForecastPlace): Flow<CloudForecast?>
    suspend fun place(place: ForecastPlace): ForecastPlaceEntity?
    suspend fun storeForecast(place: ForecastPlaceEntity, steps: List<ForecastStepEntity>): DataResult<Unit>
    suspend fun markRevalidated(place: ForecastPlace, fetchedAt: Long, expiresAt: Long): DataResult<Unit>
}

@Singleton
internal class RoomForecastLocalDataSource @Inject constructor(
    private val dao: ForecastDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ForecastLocalDataSource {

    override fun observeForecast(place: ForecastPlace): Flow<CloudForecast?> = combine(
        dao.observePlace(place.latitudeE2, place.longitudeE2),
        dao.observeSteps(place.latitudeE2, place.longitudeE2),
    ) { stored, steps -> stored?.toDomain(steps, place) }
        .distinctUntilChanged()
        .flowOn(ioDispatcher)

    override suspend fun place(place: ForecastPlace): ForecastPlaceEntity? = withContext(ioDispatcher) {
        dao.place(place.latitudeE2, place.longitudeE2)
    }

    override suspend fun storeForecast(place: ForecastPlaceEntity, steps: List<ForecastStepEntity>): DataResult<Unit> =
        write { dao.replaceForecast(place, steps) }

    override suspend fun markRevalidated(place: ForecastPlace, fetchedAt: Long, expiresAt: Long): DataResult<Unit> =
        write { dao.markRevalidated(place.latitudeE2, place.longitudeE2, fetchedAt, expiresAt) }

    @Suppress("TooGenericExceptionCaught") // Any SQLite failure becomes AppError.Database.
    private suspend fun write(block: suspend () -> Unit): DataResult<Unit> = withContext(ioDispatcher) {
        try {
            DataResult.Success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            DataResult.Failure(AppError.Database(throwable.message))
        }
    }
}
