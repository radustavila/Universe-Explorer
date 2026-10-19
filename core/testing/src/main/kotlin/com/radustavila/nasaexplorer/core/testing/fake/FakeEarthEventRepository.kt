package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.EarthEventRepository
import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EventImageryLayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [EarthEventRepository]: the cache is whatever the test seeds, newest update first. */
class FakeEarthEventRepository : EarthEventRepository {

    private val events = MutableStateFlow<List<EarthEvent>>(emptyList())

    var refreshResult: DataResult<Int> = DataResult.Success(0)

    var refreshCallCount: Int = 0
        private set

    /** Answers [imageryLayerFor]; categories without an entry have no layer. */
    var imageryLayers: Map<EarthEventCategory, EventImageryLayer> = emptyMap()

    var imageryFailure: DataResult.Failure? = null

    fun setEvents(entries: List<EarthEvent>) {
        events.value = entries.sortedByDescending(EarthEvent::lastUpdate)
    }

    override fun observeOpenEvents(): Flow<List<EarthEvent>> = events

    override suspend fun refreshOpenEvents(): DataResult<Int> {
        refreshCallCount++
        return refreshResult
    }

    override suspend fun imageryLayerFor(category: EarthEventCategory): DataResult<EventImageryLayer?> =
        imageryFailure ?: DataResult.Success(imageryLayers[category])
}
