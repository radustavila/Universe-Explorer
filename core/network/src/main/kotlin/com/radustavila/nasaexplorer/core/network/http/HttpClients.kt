package com.radustavila.nasaexplorer.core.network.http

import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateLimiter
import com.radustavila.nasaexplorer.core.network.ratelimit.RateLimitInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * The shared client for API calls: bounded timeouts, per-host rate limits, and a small HTTP cache in
 * [cacheDirectory] that honours the servers' own caching headers (the data itself lives in Room).
 */
fun apiHttpClient(
    configuration: NetworkConfiguration,
    rateLimiter: HostRateLimiter,
    cacheDirectory: File?,
): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(configuration.connectTimeoutSeconds, TimeUnit.SECONDS)
    .readTimeout(configuration.readTimeoutSeconds, TimeUnit.SECONDS)
    .callTimeout(configuration.callTimeoutSeconds, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .apply { if (cacheDirectory != null) cache(Cache(cacheDirectory, HTTP_CACHE_BYTES)) }
    // First, so a refused call is never logged as if it had been sent.
    .addInterceptor(RateLimitInterceptor(rateLimiter))
    .apply {
        if (configuration.isLoggingEnabled) {
            addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
    }
    .build()

/**
 * A client for image downloads derived from the API client: same connection pool, interceptors and
 * connect/read timeouts, but no overall call timeout (a full-resolution image on a slow link can take
 * longer than any API call should) and no HTTP cache (the image loader keeps its own disk cache).
 */
fun OkHttpClient.forImages(): OkHttpClient = newBuilder()
    .callTimeout(0, TimeUnit.SECONDS)
    .cache(null)
    .build()

const val HTTP_CACHE_BYTES: Long = 16L * 1024 * 1024
