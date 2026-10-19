package com.radustavila.nasaexplorer.core.network.ratelimit

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Application interceptor on the shared OkHttp client: every Retrofit service (and Coil) goes
 * through it, so no data module can forget to respect a host's quota.
 *
 * Calls to a host that is cooling down or out of budget fail fast with [RateLimitedException]
 * without touching the network; responses are inspected for `429`, `Retry-After` and
 * `X-RateLimit-Remaining`. The 429 response itself is passed through unchanged, so the caller still
 * maps it to `AppError.RateLimited` with the server's `Retry-After`.
 */
class RateLimitInterceptor(
    private val limiter: HostRateLimiter,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        limiter.acquire(host)
        val response = chain.proceed(request)
        limiter.onResponse(
            host = host,
            code = response.code,
            retryAfter = response.header(HEADER_RETRY_AFTER),
            remaining = response.header(HEADER_RATE_LIMIT_REMAINING),
        )
        return response
    }

    private companion object {
        const val HEADER_RETRY_AFTER = "Retry-After"
        const val HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining"
    }
}
