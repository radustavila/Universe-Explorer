package com.radustavila.nasaexplorer.data.earthevents.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.EarthEventRepository
import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EventImageryLayer
import com.radustavila.nasaexplorer.data.earthevents.local.EarthEventsLocalDataSource
import com.radustavila.nasaexplorer.data.earthevents.mapper.chooseImageryLayer
import com.radustavila.nasaexplorer.data.earthevents.mapper.toDomainOrNull
import com.radustavila.nasaexplorer.data.earthevents.mapper.toImageryLayers
import com.radustavila.nasaexplorer.data.earthevents.remote.EonetRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * EONET events, offline-first: the map reads Room, a refresh replaces the cached list in one
 * transaction (or changes nothing when it fails), and imagery layers are fetched once per category
 * and then served from the cache.
 */
@Singleton
class EarthEventRepositoryImpl @Inject constructor(
    private val localDataSource: EarthEventsLocalDataSource,
    private val remoteDataSource: EonetRemoteDataSource,
    private val timeProvider: TimeProvider,
) : EarthEventRepository {

    override fun observeOpenEvents(): Flow<List<EarthEvent>> = localDataSource.observeEvents()

    override suspend fun refreshOpenEvents(): DataResult<Int> {
        val response = when (val result = remoteDataSource.fetchOpenEvents()) {
            is DataResult.Success -> result.data
            is DataResult.Failure -> return result
        }
        val events = response.events.mapNotNull { it.toDomainOrNull() }.distinctBy(EarthEvent::id)
        // A non-empty answer of which nothing parses is a format change, not a quiet planet: keep
        // showing the old events rather than an empty map.
        if (events.isEmpty() && response.events.isNotEmpty()) return DataResult.Failure(AppError.EmptyResponse)

        return localDataSource.replaceEvents(events, timeProvider.now()).map { events.size }
    }

    override suspend fun imageryLayerFor(category: EarthEventCategory): DataResult<EventImageryLayer?> {
        val cached = localDataSource.layersFor(category)
        if (cached is DataResult.Success && cached.data.isNotEmpty()) {
            return DataResult.Success(chooseImageryLayer(category, cached.data))
        }

        val layers = when (val result = remoteDataSource.fetchLayers(category)) {
            is DataResult.Success -> result.data.toImageryLayers()
            is DataResult.Failure -> return result
        }
        // Caching is a nicety: the layer is still usable when the write fails.
        localDataSource.replaceLayers(category, layers, timeProvider.now())
        return DataResult.Success(chooseImageryLayer(category, layers))
    }
}
