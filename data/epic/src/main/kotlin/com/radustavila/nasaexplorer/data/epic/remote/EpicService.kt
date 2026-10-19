package com.radustavila.nasaexplorer.data.epic.remote

import com.radustavila.nasaexplorer.data.epic.remote.dto.EpicDateDto
import com.radustavila.nasaexplorer.data.epic.remote.dto.EpicImageDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * EPIC metadata endpoints on epic.gsfc.nasa.gov. `collection` is `natural` or `enhanced`; both
 * answer with the same shapes. A day without images is an empty array, not an error.
 */
interface EpicService {

    /** Every image of the most recent day EPIC has published. */
    @GET("api/{collection}")
    suspend fun getLatest(@Path("collection") collection: String): List<EpicImageDto>

    /** Every image of one day, `yyyy-MM-dd`. */
    @GET("api/{collection}/date/{date}")
    suspend fun getDay(
        @Path("collection") collection: String,
        @Path("date") date: String,
    ): List<EpicImageDto>

    /** Every day that has images, newest first. The enhanced listing repeats some days. */
    @GET("api/{collection}/all")
    suspend fun getAvailableDates(@Path("collection") collection: String): List<EpicDateDto>
}
