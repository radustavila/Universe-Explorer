package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EventImageryLayer
import kotlinx.coroutines.flow.Flow

/**
 * Natural events happening on Earth now (NASA EONET). Reads come from the local cache; the network
 * only ever writes into it.
 */
interface EarthEventRepository {

    /** Cached open events, most recently updated first. Emits whenever the cache changes. */
    fun observeOpenEvents(): Flow<List<EarthEvent>>

    /**
     * Replaces the cache with the events open right now, in one network call. On failure the cached
     * events are kept.
     *
     * @return the number of events now cached.
     */
    suspend fun refreshOpenEvents(): DataResult<Int>

    /**
     * The GIBS layer that best shows [category], from the cache or, the first time, from EONET.
     * `null` when EONET lists none the map can draw.
     */
    suspend fun imageryLayerFor(category: EarthEventCategory): DataResult<EventImageryLayer?>
}
