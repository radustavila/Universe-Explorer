package com.radustavila.nasaexplorer.data.nasa.remote

import com.radustavila.nasaexplorer.data.nasa.remote.dto.ApodBasicDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * APOD endpoints on science.nasa.gov. Dates are in APOD's own `yyMMdd` form.
 *
 * The feed uses the *date range* variant: one request returns a whole window (newest first),
 * instead of one request per day. The server caps a page at [MAX_PAGE_SIZE] entries.
 */
interface NasaApodService {

    @GET("wp-json/wp/v2/apod-basic")
    suspend fun getApodRange(
        @Query("date_from") startDate: String,
        @Query("date_to") endDate: String,
        @Query("page") page: Int = 1,
        @Query("per_page") pageSize: Int = MAX_PAGE_SIZE,
    ): List<ApodBasicDto>

    @GET("wp-json/wp/v2/apod-basic/{date}")
    suspend fun getApod(@Path("date") date: String): ApodBasicDto

    companion object {
        const val MAX_PAGE_SIZE = 25
    }
}
