package com.radustavila.nasaexplorer.core.network.ratelimit

import java.time.Duration

/**
 * How many requests one host accepts per [window] before it starts answering HTTP 429.
 *
 * Budgets are contributed to a Hilt set, so a data module that talks to a metered API declares its
 * own budget next to its Retrofit service:
 *
 * ```
 * @Provides @IntoSet
 * fun provideBudget() = HostRateBudget(host = "example.org", maxRequests = 60, window = Duration.ofMinutes(1))
 * ```
 *
 * Hosts without a budget are unlimited, but still cool down after a 429 or an exhausted
 * `X-RateLimit-Remaining`.
 */
data class HostRateBudget(
    val host: String,
    val maxRequests: Int,
    val window: Duration,
) {
    init {
        require(maxRequests > 0) { "maxRequests must be positive" }
        require(!window.isNegative && !window.isZero) { "window must be positive" }
    }

    companion object {
        /**
         * Launch Library 2 allows 15 requests per hour per IP without a key; the next-launches card
         * and the crew list share it.
         */
        val LAUNCH_LIBRARY = HostRateBudget(
            host = "ll.thespacedevs.com",
            maxRequests = 15,
            window = Duration.ofHours(1),
        )

        /** api.nasa.gov with a personal key (DEMO_KEY is far lower and should not ship). */
        val NASA_API = HostRateBudget(
            host = "api.nasa.gov",
            maxRequests = 1_000,
            window = Duration.ofHours(1),
        )

        /** Budgets known from the roadmap's API survey; registered by `:core:network` itself. */
        val KNOWN: Set<HostRateBudget> = setOf(LAUNCH_LIBRARY, NASA_API)
    }
}
