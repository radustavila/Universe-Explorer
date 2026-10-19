package com.radustavila.nasaexplorer.core.network.ratelimit

import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HostRateLimiterTest {

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-19T12:00:00Z"))

    private fun limiter(vararg budgets: HostRateBudget) =
        HostRateLimiter(budgets = budgets.toSet(), timeProvider = clock)

    @Test
    fun `a host without a budget is never refused while it has not complained`() {
        val limiter = limiter()

        repeat(100) { limiter.acquire("example.org") }
    }

    @Test
    fun `the budget refuses the call that would exceed it until the oldest request ages out`() {
        val limiter = limiter(HostRateBudget("ll.thespacedevs.com", maxRequests = 2, window = Duration.ofHours(1)))
        limiter.acquire("ll.thespacedevs.com")
        clock.currentInstant = clock.currentInstant.plusSeconds(600)
        limiter.acquire("ll.thespacedevs.com")

        val refused = assertThrows(RateLimitedException::class.java) {
            limiter.acquire("ll.thespacedevs.com")
        }
        // The first request was 10 minutes ago, so a slot frees up in 50 minutes.
        assertEquals(3_000L, refused.retryAfterSeconds)

        clock.currentInstant = clock.currentInstant.plusSeconds(3_001)
        limiter.acquire("ll.thespacedevs.com")
    }

    @Test
    fun `budgets are per host`() {
        val limiter = limiter(HostRateBudget("a.example", maxRequests = 1, window = Duration.ofHours(1)))
        limiter.acquire("a.example")

        limiter.acquire("b.example")
        assertThrows(RateLimitedException::class.java) { limiter.acquire("a.example") }
    }

    @Test
    fun `a 429 cools the host down for its Retry-After`() {
        val limiter = limiter()
        limiter.acquire("example.org")

        limiter.onResponse("example.org", code = 429, retryAfter = "120", remaining = null)

        val refused = assertThrows(RateLimitedException::class.java) { limiter.acquire("example.org") }
        assertEquals(120L, refused.retryAfterSeconds)
        clock.currentInstant = clock.currentInstant.plusSeconds(120)
        limiter.acquire("example.org")
        assertNull(limiter.cooldownUntil("example.org"))
    }

    @Test
    fun `an exhausted X-RateLimit-Remaining cools the host down even on success`() {
        val limiter = limiter(HostRateBudget("api.nasa.gov", maxRequests = 1_000, window = Duration.ofHours(1)))

        limiter.onResponse("api.nasa.gov", code = 200, retryAfter = null, remaining = "0")

        // No Retry-After: the host's own window is the best guess.
        assertEquals(clock.currentInstant.plus(Duration.ofHours(1)), limiter.cooldownUntil("api.nasa.gov"))
    }

    @Test
    fun `a 429 without any hint falls back to the default cooldown`() {
        val limiter = limiter()

        limiter.onResponse("example.org", code = 429, retryAfter = null, remaining = null)

        assertEquals(
            clock.currentInstant.plus(HostRateLimiter.DEFAULT_COOLDOWN),
            limiter.cooldownUntil("example.org"),
        )
    }

    @Test
    fun `remaining quota does not start a cooldown`() {
        val limiter = limiter()

        limiter.onResponse("example.org", code = 200, retryAfter = null, remaining = "9")

        assertNull(limiter.cooldownUntil("example.org"))
    }

    @Test
    fun `the strictest budget wins when a host is registered twice`() {
        val limiter = limiter(
            HostRateBudget("example.org", maxRequests = 100, window = Duration.ofHours(1)),
            HostRateBudget("example.org", maxRequests = 1, window = Duration.ofHours(1)),
        )
        limiter.acquire("example.org")

        assertThrows(RateLimitedException::class.java) { limiter.acquire("example.org") }
    }

    @Test
    fun `Retry-After accepts both delta seconds and an HTTP date`() {
        val now = Instant.parse("2026-10-19T12:00:00Z")

        assertEquals(Duration.ofSeconds(30), HostRateLimiter.parseRetryAfter("30", now))
        assertEquals(
            Duration.ofMinutes(5),
            HostRateLimiter.parseRetryAfter("Mon, 19 Oct 2026 12:05:00 GMT", now),
        )
        assertNull(HostRateLimiter.parseRetryAfter("soon", now))
        assertNull(HostRateLimiter.parseRetryAfter(null, now))
    }
}
