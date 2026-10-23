package com.radustavila.nasaexplorer.data.climate.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.ClimatePoint
import com.radustavila.nasaexplorer.core.model.ClimateSeries
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local boundary of the climate module: the source of truth the Earth tab observes. */
internal interface ClimateLocalDataSource {
    fun observeSeries(): Flow<List<ClimateSeries>>
    suspend fun replace(
        indicator: ClimateIndicator,
        points: List<ClimatePoint>,
        fetchedAtMillis: Long,
    ): DataResult<Unit>
}

@Singleton
internal class RoomClimateLocalDataSource @Inject constructor(
    private val dao: ClimateDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ClimateLocalDataSource {

    override fun observeSeries(): Flow<List<ClimateSeries>> = dao.observePoints()
        .map { rows ->
            val byIndicator = rows.groupBy { it.indicator }
            ClimateIndicator.entries.mapNotNull { indicator ->
                val points = byIndicator[indicator.key]?.mapNotNull { it.toPoint() }.orEmpty()
                if (points.isEmpty()) null else ClimateSeries(indicator, points)
            }
        }
        .flowOn(ioDispatcher)

    @Suppress("TooGenericExceptionCaught") // Any SQLite failure becomes AppError.Database.
    override suspend fun replace(
        indicator: ClimateIndicator,
        points: List<ClimatePoint>,
        fetchedAtMillis: Long,
    ): DataResult<Unit> = withContext(ioDispatcher) {
        try {
            val rows = points.map { ClimatePointEntity(indicator.key, it.date.toString(), it.value, fetchedAtMillis) }
            DataResult.Success(dao.replaceIndicator(indicator.key, rows))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            DataResult.Failure(AppError.Database(throwable.message))
        }
    }

    private fun ClimatePointEntity.toPoint(): ClimatePoint? =
        runCatching { LocalDate.parse(date) }.getOrNull()?.let { ClimatePoint(it, value) }
}
