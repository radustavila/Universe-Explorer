package com.radustavila.nasaexplorer.data.sky.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.sky.remote.dto.GpElementsDto
import com.radustavila.nasaexplorer.data.sky.remote.dto.SatellitePositionDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface SkyRemoteDataSource {
    suspend fun fetchElements(noradId: Int): DataResult<List<GpElementsDto>>
    suspend fun fetchLivePosition(noradId: Int): DataResult<SatellitePositionDto>
}

@Singleton
class RetrofitSkyRemoteDataSource @Inject constructor(
    private val celesTrak: CelesTrakService,
    private val whereTheIss: WhereTheIssService,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : SkyRemoteDataSource {

    override suspend fun fetchElements(noradId: Int): DataResult<List<GpElementsDto>> = withContext(ioDispatcher) {
        safeNetworkCall { celesTrak.getElements(noradId) }
    }

    override suspend fun fetchLivePosition(noradId: Int): DataResult<SatellitePositionDto> =
        withContext(ioDispatcher) { safeNetworkCall { whereTheIss.getPosition(noradId) } }
}
