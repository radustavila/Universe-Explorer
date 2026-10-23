package com.radustavila.nasaexplorer.data.earthevents.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.EarthquakeFeedDto
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * The USGS feeds the app reads. [key] is the feed's name in the URL and in the cache; never rename
 * one. `all_month` (7 MB) is never used: too big for a phone.
 */
enum class EarthquakeFeed(val key: String) {
    /** ~370 quakes, ~40 KB gzipped: the maps and the list. */
    MAGNITUDE_2_5_WEEK("2.5_week"),

    /** A dozen or two: the "significant" filter reaches back a month. */
    SIGNIFICANT_MONTH("significant_month"),
}

/** What a feed request brought back. */
sealed interface EarthquakeFeedDownload {
    data class Updated(
        val feed: EarthquakeFeedDto,
        /** Sent back verbatim as `If-Modified-Since` next time. */
        val lastModified: String?,
    ) : EarthquakeFeedDownload

    /** 304: the cached copy is still current. */
    data object NotModified : EarthquakeFeedDownload
}

/** Remote boundary of the quake feeds. Returns [DataResult] instead of throwing. */
interface UsgsRemoteDataSource {
    suspend fun fetchFeed(feed: EarthquakeFeed, ifModifiedSince: String?): DataResult<EarthquakeFeedDownload>
}

class RetrofitUsgsRemoteDataSource @Inject constructor(
    private val service: UsgsService,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : UsgsRemoteDataSource {

    override suspend fun fetchFeed(feed: EarthquakeFeed, ifModifiedSince: String?): DataResult<EarthquakeFeedDownload> =
        withContext(ioDispatcher) {
            safeNetworkCall {
                val response = service.summary(feed.key, ifModifiedSince)
                when {
                    response.code() == HTTP_NOT_MODIFIED -> EarthquakeFeedDownload.NotModified
                    !response.isSuccessful -> throw HttpException(response)
                    else -> response.body()?.let { feed ->
                        EarthquakeFeedDownload.Updated(feed, lastModified = response.headers()[LAST_MODIFIED])
                    }
                }
            }.flatMap { download ->
                download?.let { DataResult.Success(it) } ?: DataResult.Failure(AppError.EmptyResponse)
            }
        }

    private companion object {
        const val HTTP_NOT_MODIFIED = 304
        const val LAST_MODIFIED = "Last-Modified"
    }
}
