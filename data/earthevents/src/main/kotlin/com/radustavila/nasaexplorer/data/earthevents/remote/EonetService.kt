package com.radustavila.nasaexplorer.data.earthevents.remote

import com.radustavila.nasaexplorer.data.earthevents.remote.dto.EventsResponseDto
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.LayersResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * NASA EONET v3, `https://eonet.gsfc.nasa.gov/api/v3`. No key; answers carry
 * `X-RateLimit-Limit: 60`, which the shared rate limiter honours.
 *
 * Null query parameters are left out of the request.
 */
interface EonetService {

    /**
     * Events, newest first. [days] keeps those with a report in the last N days; without it,
     * `status=open` returns every event never closed (over 7,000, most of them years old).
     */
    @GET("events")
    suspend fun events(
        @Query("status") status: String,
        @Query("days") days: Int? = null,
        @Query("category") category: String? = null,
        @Query("limit") limit: Int? = null,
    ): EventsResponseDto

    /** The imagery layers EONET suggests for a category (27 for wildfires). */
    @GET("layers/{category}")
    suspend fun layers(@Path("category") category: String): LayersResponseDto
}
