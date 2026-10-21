package com.radustavila.nasaexplorer.core.cache

import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import java.time.Duration
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * "Clear cache" for the feeds' local copies: runs every [CacheEvictor] as if its retention period
 * had passed for everything it holds, so each cache empties down to what its evictor always keeps
 * (favourites, the rows paging still points at, the newest exoplanet list). Going through the
 * evictors, rather than deleting tables, keeps their favourite and paging rules in one place.
 */
class EvictingCacheCleaner @Inject constructor(
    private val evictors: Set<@JvmSuppressWildcards CacheEvictor>,
    private val timeProvider: TimeProvider,
) : CacheCleaner {

    /** Runs every evictor even when one fails, then reports the first failure. */
    override suspend fun clear() {
        val everythingExpired = timeProvider.now().plus(BEYOND_EVERY_RETENTION)
        var failure: Exception? = null
        evictors.forEach { evictor ->
            try {
                evictor.evict(everythingExpired)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = failure ?: e
            }
        }
        failure?.let { throw it }
    }

    internal companion object {
        /** Longer than any evictor's retention period; small enough for epoch milliseconds. */
        val BEYOND_EVERY_RETENTION: Duration = Duration.ofDays(3_650)
    }
}
