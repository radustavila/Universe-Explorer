package com.radustavila.nasaexplorer.data.sky.remote

import com.radustavila.nasaexplorer.data.sky.remote.dto.GpElementsDto
import com.radustavila.nasaexplorer.data.sky.remote.dto.SatellitePositionDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CelesTrak general perturbations data (no key). CelesTrak updates it a few times a day and blocks
 * clients that download the same set over and over, so the app asks at most twice a day.
 */
interface CelesTrakService {

    @GET("NORAD/elements/gp.php")
    suspend fun getElements(
        @Query("CATNR") catalogNumber: Int,
        @Query("FORMAT") format: String = "json",
    ): List<GpElementsDto>
}

/** wheretheiss.at (no key, 350 calls per 5 minutes): where the ISS is right now. */
interface WhereTheIssService {

    @GET("v1/satellites/{id}")
    suspend fun getPosition(@Path("id") noradId: Int): SatellitePositionDto
}
