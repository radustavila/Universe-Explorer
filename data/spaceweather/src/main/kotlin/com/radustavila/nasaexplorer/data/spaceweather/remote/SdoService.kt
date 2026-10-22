package com.radustavila.nasaexplorer.data.spaceweather.remote

import com.radustavila.nasaexplorer.data.spaceweather.sun.SdoImagery
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Streaming

/** SDO's latest pictures, fetched as raw JPEG bytes. */
interface SdoService {

    /**
     * One of SDO's `latest_{size}_{channel}.jpg` files. With [ifModifiedSince] the server answers
     * 304 and no body while it still has that picture.
     */
    @Streaming
    @GET("${SdoImagery.LATEST_PATH}{file}")
    suspend fun latestImage(
        @Path("file") file: String,
        @Header("If-Modified-Since") ifModifiedSince: String?,
    ): Response<ResponseBody>
}
