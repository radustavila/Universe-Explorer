package com.radustavila.nasaexplorer.data.nasa.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.nasa.remote.dto.ApodBasicDto
import com.radustavila.nasaexplorer.data.nasa.remote.dto.ApodDto
import com.radustavila.nasaexplorer.data.nasa.remote.dto.toApodDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
            val entries = mutableListOf<ApodBasicDto>()
            for (page in 1..MAX_PAGES) {
                val batch = service.getApodRange(
                    startDate = APOD_DATE_FORMAT.format(startDate),
                    endDate = APOD_DATE_FORMAT.format(endDate),
                    page = page,
                )
                entries += batch
                if (batch.size < NasaApodService.MAX_PAGE_SIZE) break
            }
            entries.map(ApodBasicDto::toApodDto)
        }
    }

    override suspend fun fetchSingle(date: LocalDate): DataResult<ApodDto> =
        withContext(ioDispatcher) {
            safeNetworkCall { service.getApod(date = APOD_DATE_FORMAT.format(date)).toApodDto() }
        }

    private companion object {
        val APOD_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd")

        /** Safety stop against a server that keeps returning full pages: ~8 months of entries. */
        const val MAX_PAGES = 10
    }
}
