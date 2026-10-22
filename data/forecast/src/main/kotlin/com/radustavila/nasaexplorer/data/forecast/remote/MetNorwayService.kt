package com.radustavila.nasaexplorer.data.forecast.remote

import com.radustavila.nasaexplorer.data.forecast.remote.dto.LocationForecastDto
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * MET Norway Locationforecast 2.0 (api.met.no), CC BY 4.0. Its terms: a User-Agent naming the app
 * ([MetNorwayUserAgentInterceptor]), at most 4 decimals in coordinates, no request before the
 * previous response's `Expires`, and `If-Modified-Since` on every repeat.
 */
interface MetNorwayService {

    /**
     * The forecast lives in Room with its `Expires` and `Last-Modified`, so the HTTP cache must not
     * keep (or answer with) a second copy; a revalidation answers 304 with no body.
     */
    @Headers("Cache-Control: no-store")
    @GET("weatherapi/locationforecast/2.0/complete")
    suspend fun complete(
        @Query("lat") latitude: String,
        @Query("lon") longitude: String,
        @Header("If-Modified-Since") ifModifiedSince: String?,
    ): retrofit2.Response<LocationForecastDto>
}

/** Names the app on every MET Norway request; a missing or generic User-Agent gets 403 and a ban. */
class MetNorwayUserAgentInterceptor(private val userAgent: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent()).build())
}
