package com.radustavila.nasaexplorer.data.forecast.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.forecast.remote.dto.LocationForecastDto
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/** What a forecast request brought back. */
sealed interface ForecastDownload {
    /** When MET Norway allows the next request for this place, if it said. */
    val expiresAt: Instant?

    data class Updated(
        val forecast: LocationForecastDto,
        /** Sent back verbatim as `If-Modified-Since` next time. */
        val lastModified: String?,
        override val expiresAt: Instant?,
    ) : ForecastDownload

    /** 304: the stored forecast is still current. */
    data class NotModified(override val expiresAt: Instant?) : ForecastDownload
}

/** Remote boundary of the forecast module. Returns [DataResult] instead of throwing. */
interface ForecastRemoteDataSource {
    suspend fun fetchForecast(place: ForecastPlace, ifModifiedSince: String?): DataResult<ForecastDownload>
}

@Singleton
class MetNorwayRemoteDataSource @Inject constructor(
    private val service: MetNorwayService,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ForecastRemoteDataSource {

    override suspend fun fetchForecast(place: ForecastPlace, ifModifiedSince: String?): DataResult<ForecastDownload> =
        withContext(ioDispatcher) {
            safeNetworkCall {
                val response = service.complete(place.latitude, place.longitude, ifModifiedSince)
                val expiresAt = response.headers().getDate(EXPIRES)?.toInstant()
                when {
                    response.code() == HTTP_NOT_MODIFIED -> ForecastDownload.NotModified(expiresAt)
                    !response.isSuccessful -> throw HttpException(response)
                    else -> response.body()?.let { forecast ->
                        ForecastDownload.Updated(forecast, response.headers()[LAST_MODIFIED], expiresAt)
                    }
                }
            }.flatMap { download ->
                download?.let { DataResult.Success(it) } ?: DataResult.Failure(AppError.EmptyResponse)
            }
        }

    private companion object {
        const val HTTP_NOT_MODIFIED = 304
        const val EXPIRES = "Expires"
        const val LAST_MODIFIED = "Last-Modified"
    }
}
