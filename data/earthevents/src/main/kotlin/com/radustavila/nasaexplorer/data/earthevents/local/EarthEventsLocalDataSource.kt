package com.radustavila.nasaexplorer.data.earthevents.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EventImageryLayer
import com.radustavila.nasaexplorer.data.earthevents.mapper.toDomain
import com.radustavila.nasaexplorer.data.earthevents.mapper.toDomainOrNull
import com.radustavila.nasaexplorer.data.earthevents.mapper.toEntity
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Local boundary of the events module: the cached open events and imagery layers. */
interface EarthEventsLocalDataSource {
    fun observeEvents(): Flow<List<EarthEvent>>
    suspend fun replaceEvents(events: List<EarthEvent>, at: Instant): DataResult<Unit>
    suspend fun layersFor(category: EarthEventCategory): DataResult<List<EventImageryLayer>>
    suspend fun replaceLayers(category: EarthEventCategory, layers: List<EventImageryLayer>, at: Instant): DataResult<Unit>
}

class RoomEarthEventsLocalDataSource @Inject constructor(
    private val dao: EarthEventDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : EarthEventsLocalDataSource {

    override fun observeEvents(): Flow<List<EarthEvent>> =
        dao.observeEvents()
            .map { rows -> rows.mapNotNull { it.toDomainOrNull(json) } }
            .flowOn(ioDispatcher)

    override suspend fun replaceEvents(events: List<EarthEvent>, at: Instant): DataResult<Unit> =
        io { dao.replaceEvents(events.map { it.toEntity(json, at) }) }

    override suspend fun layersFor(category: EarthEventCategory): DataResult<List<EventImageryLayer>> =
        io { dao.layersFor(category.key).map { it.toDomain() } }

    override suspend fun replaceLayers(
        category: EarthEventCategory,
        layers: List<EventImageryLayer>,
        at: Instant,
    ): DataResult<Unit> = io {
        dao.replaceLayers(
            category = category.key,
            layers = layers.mapIndexed { index, layer -> layer.toEntity(category, index, at) },
        )
    }

    private suspend fun <T> io(block: suspend () -> T): DataResult<T> = withContext(ioDispatcher) {
        try {
            DataResult.Success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            DataResult.Failure(AppError.Database(throwable.message))
        }
    }

    private companion object {
        /** Only reads what it wrote itself, so the strict defaults are right. */
        val json = Json
    }
}
