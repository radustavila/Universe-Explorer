package com.radustavila.nasaexplorer.data.spaceweather.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.CmeDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.FlareDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.KpForecastDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.KpOneMinuteDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.OvationDto
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Remote boundary of the space weather module. Returns [DataResult] instead of throwing. */
interface SpaceWeatherRemoteDataSource {
    suspend fun fetchKpForecast(): DataResult<List<KpForecastDto>>
    suspend fun fetchKpOneMinute(): DataResult<List<KpOneMinuteDto>>
    suspend fun fetchAuroraForecast(): DataResult<OvationDto>
    suspend fun fetchFlares(from: LocalDate, to: LocalDate): DataResult<List<FlareDto>>
    suspend fun fetchCmes(from: LocalDate, to: LocalDate): DataResult<List<CmeDto>>
}

@Singleton
class RetrofitSpaceWeatherRemoteDataSource @Inject constructor(
    private val swpc: SwpcService,
    private val donki: DonkiService,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : SpaceWeatherRemoteDataSource {

    override suspend fun fetchKpForecast(): DataResult<List<KpForecastDto>> = call { swpc.getKpForecast() }

    override suspend fun fetchKpOneMinute(): DataResult<List<KpOneMinuteDto>> = call { swpc.getKpOneMinute() }

    override suspend fun fetchAuroraForecast(): DataResult<OvationDto> = call { swpc.getAuroraForecast() }

    override suspend fun fetchFlares(from: LocalDate, to: LocalDate): DataResult<List<FlareDto>> =
        call { donki.getFlares(startDate = from.toString(), endDate = to.toString()) }

    override suspend fun fetchCmes(from: LocalDate, to: LocalDate): DataResult<List<CmeDto>> =
        call { donki.getCmes(startDate = from.toString(), endDate = to.toString()) }

    private suspend fun <T> call(block: suspend () -> T): DataResult<T> =
        withContext(ioDispatcher) { safeNetworkCall(block) }
}
