package com.radustavila.nasaexplorer.data.mars.remote

import com.radustavila.nasaexplorer.data.mars.remote.dto.CuriosityPageDto
import com.radustavila.nasaexplorer.data.mars.remote.dto.PerseverancePageDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * mars.nasa.gov raw images. No key, no published quota. The two rovers are served by two different
 * APIs on the same host.
 */
interface MarsRawImagesService {

    /** Curiosity's newest raw images; fast. */
    @GET("api/v1/raw_image_items/")
    suspend fun curiosity(
        @Query("order") order: String = "sol desc",
        @Query("per_page") perPage: Int,
        @Query("condition_1") mission: String = "msl:mission",
    ): CuriosityPageDto

    /**
     * Perseverance's newest raw images. Slow (15 s on a good day; past ~90 s its origin gives up
     * and the CDN answers 403), so it is only ever called from the background sync.
     */
    @GET("rss/api/")
    suspend fun perseverance(
        @Query("num") count: Int,
        @Query("feed") feed: String = "raw_images",
        @Query("category") category: String = "mars2020",
        @Query("feedtype") feedType: String = "json",
        @Query("order") order: String = "sol desc",
    ): PerseverancePageDto
}
