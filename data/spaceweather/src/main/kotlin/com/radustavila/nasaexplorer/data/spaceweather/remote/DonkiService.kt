package com.radustavila.nasaexplorer.data.spaceweather.remote

import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.CmeDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.FlareDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * DONKI, NASA's space weather database, on its new host. CME queries need both dates (HTTP 400
 * otherwise) and a range of at most 60 days; an empty range answers `[]`.
 */
interface DonkiService {

    @GET("get/FLR")
    suspend fun getFlares(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
    ): List<FlareDto>

    @GET("get/CME")
    suspend fun getCmes(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
    ): List<CmeDto>
}
