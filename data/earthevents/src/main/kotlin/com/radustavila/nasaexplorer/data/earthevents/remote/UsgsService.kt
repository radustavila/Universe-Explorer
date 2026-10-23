package com.radustavila.nasaexplorer.data.earthevents.remote

import com.radustavila.nasaexplorer.data.earthevents.remote.dto.EarthquakeFeedDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path

/**
 * USGS earthquake summary feeds, `https://earthquake.usgs.gov/earthquakes/feed/v1.0/`. Public
 * domain, no key, updated every minute; USGS publishes no rate limit.
 */
interface UsgsService {

    /**
     * One feed, e.g. `2.5_week`. The quakes live in Room with the feed's `Last-Modified`, so the HTTP
     * cache must not keep (or answer with) a second copy; a revalidation answers 304 with no body.
     */
    @Headers("Cache-Control: no-store")
    @GET("summary/{feed}.geojson")
    suspend fun summary(
        @Path("feed") feed: String,
        @Header("If-Modified-Since") ifModifiedSince: String?,
    ): Response<EarthquakeFeedDto>
}
