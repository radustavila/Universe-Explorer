package com.radustavila.nasaexplorer.core.network.ratelimit

import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Per-host request bookkeeping behind [RateLimitInterceptor], kept free of OkHttp types so the
 * rules can be tested against a fake clock.
 *
 * Two independent brakes:
 *  - a **budget**: a sliding window of the requests sent to a host with a [HostRateBudget], so the
 *    app never spends more than the host allows;
 *  - a **cooldown**: once a host answers 429, or reports `X-RateLimit-Remaining: 0`, every further
 *    call to it is refused locally until its `Retry-After` has passed (or, without that header, the
 *    host's budget window, or [defaultCooldown]).
 *
 * State lives in memory: a process restart forgets it, which at worst costs one more 429.
 */
class HostRateLimiter(
    budgets: Set<HostRateBudget>,
    private val timeProvider: TimeProvider,
    private val defaultCooldown: Duration = DEFAULT_COOLDOWN,
) {
    /** The strictest budget wins when several modules register the same host. */
    private val budgets: Map<String, HostRateBudget> = budgets
        .groupBy { budget -> budget.host.lowercase() }
        .mapValues { (_, candidates) ->
            candidates.minBy { budget -> budget.maxRequests.toDouble() / budget.window.toMillis() }
        }

    private val states = HashMap<String, HostState>()

    /**
     * Reserves one request to [host].
     *
     * @throws RateLimitedException when the host is cooling down or its budget is spent.
     */
    @Synchronized
    fun acquire(host: String) {
        val key = host.lowercase()
        val now = timeProvider.now()
        val state = states.getOrPut(key) { HostState() }

        state.cooldownUntil?.let { until ->
            if (now.isBefore(until)) throw RateLimitedException(host, secondsBetween(now, until))
            state.cooldownUntil = null
        }

        val budget = budgets[key] ?: return
        val windowStart = now.minus(budget.window)
        while (state.sentAt.firstOrNull()?.isAfter(windowStart) == false) state.sentAt.removeFirst()

        if (state.sentAt.size >= budget.maxRequests) {
            val freedAt = state.sentAt.first().plus(budget.window)
            throw RateLimitedException(host, secondsBetween(now, freedAt))
        }
        state.sentAt.addLast(now)
    }

    /** Starts a cooldown when the response says the host's quota is used up. */
    @Synchronized
    fun onResponse(host: String, code: Int, retryAfter: String?, remaining: String?) {
        val isExhausted = code == HTTP_TOO_MANY_REQUESTS || remaining?.trim()?.toLongOrNull() == 0L
        if (!isExhausted) return

        val key = host.lowercase()
        val now = timeProvider.now()
        val wait = parseRetryAfter(retryAfter, now) ?: budgets[key]?.window ?: defaultCooldown
        val until = now.plus(wait)
        val state = states.getOrPut(key) { HostState() }
        state.cooldownUntil = state.cooldownUntil?.let { current -> maxOf(current, until) } ?: until
    }

    /** When [host] accepts calls again, or `null` when it is not cooling down. */
    @Synchronized
    fun cooldownUntil(host: String): Instant? =
        states[host.lowercase()]?.cooldownUntil?.takeIf { it.isAfter(timeProvider.now()) }

    private class HostState {
        val sentAt = ArrayDeque<Instant>()
        var cooldownUntil: Instant? = null
    }

    companion object {
        val DEFAULT_COOLDOWN: Duration = Duration.ofMinutes(1)
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MILLIS_PER_SECOND = 1_000L

        /** `Retry-After` is either delta-seconds or an HTTP date (RFC 9110 §10.2.3). */
        internal fun parseRetryAfter(value: String?, now: Instant): Duration? {
            val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
            raw.toLongOrNull()?.let { seconds -> return Duration.ofSeconds(seconds.coerceAtLeast(0)) }
            return try {
                val date = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
                Duration.between(now, date).takeUnless(Duration::isNegative) ?: Duration.ZERO
            } catch (_: DateTimeParseException) {
                null
            }
        }

        /** Rounded up, and never 0: "retry in 0 s" would invite an immediate, refused retry. */
        private fun secondsBetween(now: Instant, later: Instant): Long {
            val millis = Duration.between(now, later).toMillis()
            return ((millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).coerceAtLeast(1)
        }
    }
}

/**
 * A call refused locally because [host] is out of quota. An [IOException], so OkHttp delivers it
 * through the normal failure path; `toAppError()` turns it into `AppError.RateLimited`.
 */
class RateLimitedException(
    val host: String,
    val retryAfterSeconds: Long,
) : IOException("$host is rate limited; retry in $retryAfterSeconds s")
