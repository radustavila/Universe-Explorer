package com.radustavila.nasaexplorer.data.dsn.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnConfigDto
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnLiveDto
import com.radustavila.nasaexplorer.data.dsn.remote.dto.HorizonsRangeDto
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Remote boundary of the DSN module. Returns [DataResult] instead of throwing. */
interface DsnRemoteDataSource {
    suspend fun fetchLive(): DataResult<DsnLiveDto>
    suspend fun fetchConfig(): DataResult<DsnConfigDto>

    /** Horizons' range of [horizonsId] at 00:00 UTC on [date]. */
    suspend fun fetchRange(horizonsId: Int, date: LocalDate): DataResult<HorizonsRangeDto>
}

@Singleton
class RetrofitDsnRemoteDataSource @Inject constructor(
    private val dsnService: DsnNowService,
    private val horizonsService: HorizonsService,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : DsnRemoteDataSource {

    override suspend fun fetchLive(): DataResult<DsnLiveDto> = withContext(ioDispatcher) {
        val bucket = timeProvider.now().toEpochMilli() / CACHE_BUCKET_MILLIS
        safeNetworkCall { dsnService.live(bucket).use { body -> DsnXmlParser.parseLive(body.byteStream()) } }
    }

    override suspend fun fetchConfig(): DataResult<DsnConfigDto> = withContext(ioDispatcher) {
        safeNetworkCall { dsnService.config().use { body -> DsnXmlParser.parseConfig(body.byteStream()) } }
    }

    override suspend fun fetchRange(horizonsId: Int, date: LocalDate): DataResult<HorizonsRangeDto> =
        withContext(ioDispatcher) {
            safeNetworkCall {
                horizonsService.observerRange(
                    command = quoted(horizonsId.toString()),
                    startTime = quoted(date.toString()),
                    stopTime = quoted(date.plusDays(1).toString()),
                )
            }.flatMap { response ->
                // Horizons answers 200 with an "error" text for an unknown id or a bad request.
                val range = response.takeIf { it.error == null }?.result
                    ?.let { HorizonsEphemerisParser.parseRange(horizonsId, it) }
                range?.let { DataResult.Success(it) } ?: DataResult.Failure(AppError.Serialization(response.error))
            }
        }

    private fun quoted(value: String) = "'$value'"

    private companion object {
        /** NASA's page asks for `dsn.xml?r=` + the current 5-second bucket. */
        const val CACHE_BUCKET_MILLIS = 5_000L
    }
}
