package com.radustavila.nasaexplorer.data.nasaimages.remote

import com.radustavila.nasaexplorer.data.nasaimages.remote.dto.AssetResponseDto
import com.radustavila.nasaexplorer.data.nasaimages.remote.dto.SearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * NASA Image and Video Library, `https://images-api.nasa.gov`. No key; no published quota.
 *
 * Null query parameters are left out of the request.
 */
interface NasaImagesService {

    /**
     * Full-text search. [mediaType] is a comma-separated subset of `image,video,audio`; [keywords]
     * narrows to items tagged with every listed keyword. The library serves at most 10,000 hits
     * per query and answers HTTP 400 past that.
     */
    @GET("search")
    suspend fun search(
        @Query("q") query: String? = null,
        @Query("keywords") keywords: String? = null,
        @Query("media_type") mediaType: String? = null,
        @Query("year_start") yearStart: Int? = null,
        @Query("year_end") yearEnd: Int? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
    ): SearchResponseDto

    /** One item by id, in the same shape as a search result. */
    @GET("search")
    suspend fun findById(@Query("nasa_id") nasaId: String): SearchResponseDto

    /** Every file of an item; ids may contain spaces, which Retrofit percent-encodes. */
    @GET("asset/{nasa_id}")
    suspend fun asset(@Path("nasa_id") nasaId: String): AssetResponseDto
}
