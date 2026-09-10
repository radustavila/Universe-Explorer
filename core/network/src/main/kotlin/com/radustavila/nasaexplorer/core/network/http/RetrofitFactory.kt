package com.radustavila.nasaexplorer.core.network.http

import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Builds Retrofit instances on top of the shared OkHttp call factory and JSON configuration.
 *
 * Data modules provide their own base URL and interceptors, so the generic HTTP stack stays in
 * `:core:network` while API-specific concerns (like NASA's `api_key` query parameter) stay in
 * `:data:nasa`.
 */
class RetrofitFactory(
    private val json: Json,
    private val callFactory: Call.Factory,
) {
    fun create(baseUrl: String, callFactory: Call.Factory = this.callFactory): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .callFactory(callFactory)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()

    private companion object {
        const val JSON_MEDIA_TYPE = "application/json"
    }
}
