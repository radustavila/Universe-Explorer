package com.radustavila.nasaexplorer.data.nasa.remote

import com.radustavila.nasaexplorer.data.nasa.remote.dto.ApodDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * NASA APOD endpoints.
 *
 * The feed uses the *date range* variant: one request returns a whole window, instead of one request
 * per day. `thumbs=true` asks NASA for still thumbnails of video entries, which is what makes a
 * mixed image/video feed renderable.
 */
interface NasaApodService {

    @GET("planetary/apod")
    suspend fun getApodRange(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("thumbs") includeThumbnails: Boolean = true,
    ): List<ApodDto>

    @GET("planetary/apod")
    suspend fun getApod(
        @Query("date") date: String,
        @Query("thumbs") includeThumbnails: Boolean = true,
    ): ApodDto
}
