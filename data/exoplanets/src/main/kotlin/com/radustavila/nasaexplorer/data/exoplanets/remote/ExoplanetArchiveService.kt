package com.radustavila.nasaexplorer.data.exoplanets.remote

import com.radustavila.nasaexplorer.data.exoplanets.remote.dto.ExoplanetCountDto
import com.radustavila.nasaexplorer.data.exoplanets.remote.dto.ExoplanetDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The archive's synchronous TAP endpoint. `query` arrives already encoded by
 * [ExoplanetQueries.encode], so Retrofit must not encode it a second time.
 */
interface ExoplanetArchiveService {

    @GET("TAP/sync")
    suspend fun queryPlanets(
        @Query("query", encoded = true) encodedQuery: String,
        @Query("format") format: String = FORMAT_JSON,
    ): List<ExoplanetDto>

    @GET("TAP/sync")
    suspend fun queryCount(
        @Query("query", encoded = true) encodedQuery: String,
        @Query("format") format: String = FORMAT_JSON,
    ): List<ExoplanetCountDto>

    companion object {
        const val FORMAT_JSON = "json"
    }
}
