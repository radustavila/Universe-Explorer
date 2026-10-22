package com.radustavila.nasaexplorer.data.forecast.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.stargazing.CloudForecastRepository
import com.radustavila.nasaexplorer.core.model.CloudForecast
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.data.forecast.local.ForecastLocalDataSource
import com.radustavila.nasaexplorer.data.forecast.local.ForecastPlaceEntity
import com.radustavila.nasaexplorer.data.forecast.mapper.toStepEntities
import com.radustavila.nasaexplorer.data.forecast.mapper.updatedAtMillis
import com.radustavila.nasaexplorer.data.forecast.remote.ForecastDownload
import com.radustavila.nasaexplorer.data.forecast.remote.ForecastPlace
import com.radustavila.nasaexplorer.data.forecast.remote.ForecastRemoteDataSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first MET Norway forecast, one cached copy per [ForecastPlace]. Screens observe Room; a
 * refresh is the only path from the network into it and follows MET Norway's terms: nothing is
 * sent before the stored `Expires`, and a stored copy is revalidated with `If-Modified-Since`, so
 * an unchanged forecast costs a bodyless 304. A failed or empty download keeps the last forecast.
 */
@Singleton
internal class MetNorwayCloudForecastRepository @Inject constructor(
    private val localDataSource: ForecastLocalDataSource,
    private val remoteDataSource: ForecastRemoteDataSource,
    private val timeProvider: TimeProvider,
) : CloudForecastRepository {

    override fun observeForecast(observer: GeoCoordinate): Flow<CloudForecast?> =
        localDataSource.observeForecast(ForecastPlace.of(observer))

    override suspend fun refreshForecast(observer: GeoCoordinate): DataResult<Unit> {
        val place = ForecastPlace.of(observer)
        val stored = localDataSource.place(place)
        if (stored != null && timeProvider.now().toEpochMilli() < stored.expiresAt) return DataResult.Success(Unit)

        return remoteDataSource.fetchForecast(place, ifModifiedSince = stored?.lastModified).flatMap { download ->
            val now = timeProvider.now()
            val expiresAt = download.expiresAt.orDefault(now).toEpochMilli()
            when (download) {
                is ForecastDownload.NotModified -> localDataSource.markRevalidated(place, now.toEpochMilli(), expiresAt)
                is ForecastDownload.Updated -> {
                    val steps = download.forecast.toStepEntities(place)
                    if (steps.isEmpty()) {
                        DataResult.Failure(AppError.EmptyResponse)
                    } else {
                        localDataSource.storeForecast(
                            place = ForecastPlaceEntity(
                                latitudeE2 = place.latitudeE2,
                                longitudeE2 = place.longitudeE2,
                                fetchedAt = now.toEpochMilli(),
                                expiresAt = expiresAt,
                                lastModified = download.lastModified,
                                updatedAt = download.forecast.updatedAtMillis(),
                            ),
                            steps = steps,
                        )
                    }
                }
            }
        }
    }

    /** No `Expires` (or one already past by the device's clock): wait as long as MET Norway usually asks. */
    private fun Instant?.orDefault(now: Instant): Instant =
        this?.takeIf { it.isAfter(now) } ?: now.plus(DEFAULT_EXPIRY)

    companion object {
        val DEFAULT_EXPIRY: Duration = Duration.ofMinutes(30)
    }
}
