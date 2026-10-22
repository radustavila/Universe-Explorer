package com.radustavila.nasaexplorer.data.sky.remote

import com.radustavila.nasaexplorer.data.sky.remote.dto.GpElementsDto
import com.radustavila.nasaexplorer.data.sky.remote.dto.SatellitePositionDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CelesTrak general perturbations data (no key). CelesTrak updates it about every two hours and
 * blocks clients that download the same set over and over, so the app asks for the ISS at most
 * twice a day and for every other set at most once.
 */
interface CelesTrakService {

    @GET("NORAD/elements/gp.php")
    suspend fun getElements(
        @Query("CATNR") catalogNumber: Int,
        @Query("FORMAT") format: String = "json",
    ): List<GpElementsDto>

    /** A CelesTrak group, e.g. `visual` or `last-30-days`. */
    @GET("NORAD/elements/gp.php")
    suspend fun getGroup(
        @Query("GROUP") group: String,
        @Query("FORMAT") format: String = "json",
    ): List<GpElementsDto>
}

/** wheretheiss.at (no key, 350 calls per 5 minutes): where the ISS is right now. */
interface WhereTheIssService {

    @GET("v1/satellites/{id}")
    suspend fun getPosition(@Path("id") noradId: Int): SatellitePositionDto
}
