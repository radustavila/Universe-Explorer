package com.radustavila.nasaexplorer.data.nasa.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.nasa.remote.dto.ApodDto
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class RetrofitApodRemoteDataSource @Inject constructor(
    private val service: NasaApodService,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ApodRemoteDataSource {

    override suspend fun fetchRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): DataResult<List<ApodDto>> = withContext(ioDispatcher) {
        safeNetworkCall {
            service.getApodRange(startDate = startDate.toString(), endDate = endDate.toString())
        }
    }

    override suspend fun fetchSingle(date: LocalDate): DataResult<ApodDto> =
        withContext(ioDispatcher) {
            safeNetworkCall { service.getApod(date = date.toString()) }
        }
}
