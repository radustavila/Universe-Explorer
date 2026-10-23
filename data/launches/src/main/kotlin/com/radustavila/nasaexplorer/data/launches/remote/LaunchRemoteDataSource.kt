package com.radustavila.nasaexplorer.data.launches.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.launches.remote.dto.AstronautDto
import com.radustavila.nasaexplorer.data.launches.remote.dto.LaunchDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Remote boundary of the launches module. Returns [DataResult] instead of throwing. */
interface LaunchRemoteDataSource {
    suspend fun fetchUpcoming(): DataResult<List<LaunchDto>>
    suspend fun fetchPrevious(): DataResult<List<LaunchDto>>
    suspend fun fetchLaunch(id: String): DataResult<LaunchDto>
    suspend fun fetchAstronautsInSpace(): DataResult<List<AstronautDto>>
}

@Singleton
class RetrofitLaunchRemoteDataSource @Inject constructor(
    private val service: LaunchLibraryService,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : LaunchRemoteDataSource {

    override suspend fun fetchUpcoming(): DataResult<List<LaunchDto>> = withContext(ioDispatcher) {
        safeNetworkCall { service.getUpcomingLaunches().results }
    }

    override suspend fun fetchPrevious(): DataResult<List<LaunchDto>> = withContext(ioDispatcher) {
        safeNetworkCall { service.getPreviousLaunches().results }
    }

    override suspend fun fetchLaunch(id: String): DataResult<LaunchDto> = withContext(ioDispatcher) {
        safeNetworkCall { service.getLaunch(id) }
    }

    override suspend fun fetchAstronautsInSpace(): DataResult<List<AstronautDto>> = withContext(ioDispatcher) {
        safeNetworkCall { service.getAstronautsInSpace().results }
    }
}
