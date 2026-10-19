package com.radustavila.nasaexplorer.data.earthevents.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.EventsResponseDto
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.LayersResponseDto
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Remote boundary of the events module: every call returns a [DataResult], never throws. */
interface EonetRemoteDataSource {
    suspend fun fetchOpenEvents(): DataResult<EventsResponseDto>
    suspend fun fetchLayers(category: EarthEventCategory): DataResult<LayersResponseDto>

    companion object {
        /**
         * What "open right now" means: open events with a report in this many days. EONET never
         * closes many wildfires, so without a window the list reaches back to 2002. 60 days gave
         * about 150 events on 2026-10-19 (129 wildfires, 13 sea-ice, 7 storms), ~220 KB.
         */
        const val ACTIVE_WINDOW_DAYS = 60
    }
}

class RetrofitEonetRemoteDataSource @Inject constructor(
    private val service: EonetService,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : EonetRemoteDataSource {

    override suspend fun fetchOpenEvents(): DataResult<EventsResponseDto> = call {
        service.events(status = "open", days = EonetRemoteDataSource.ACTIVE_WINDOW_DAYS)
    }

    override suspend fun fetchLayers(category: EarthEventCategory): DataResult<LayersResponseDto> =
        call { service.layers(category.key) }

    private suspend fun <T> call(block: suspend () -> T): DataResult<T> =
        withContext(ioDispatcher) { safeNetworkCall(block) }
}
