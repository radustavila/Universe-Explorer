package com.radustavila.nasaexplorer.data.forecast.cache

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.data.forecast.local.ForecastDao
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Drops forecast steps that are a day in the past and places not fetched for [RETENTION] (a city
 * the user looked at once, an old device fix). The cutoff is never later than the newest fetch, so
 * a device that has been offline for weeks keeps its last forecast and its freshness line.
 */
internal class ForecastCacheEvictor @Inject constructor(
    private val dao: ForecastDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheEvictor {

    override val name: String = "met_norway"

    override suspend fun evict(now: Instant): Int = withContext(ioDispatcher) {
        val newest = dao.newestFetchedAt() ?: return@withContext 0
        val placeCutoff = minOf(newest, now.minus(RETENTION).toEpochMilli())
        dao.deletePlacesFetchedBefore(placeCutoff) + dao.deleteStepsBefore(now.minus(PAST_STEPS).toEpochMilli())
    }

    companion object {
        val RETENTION: Duration = Duration.ofDays(7)
        val PAST_STEPS: Duration = Duration.ofDays(1)
    }
}

/** "Clear cache": the forecast holds no user data; the next visit downloads it again. */
internal class ForecastCacheCleaner @Inject constructor(
    private val dao: ForecastDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheCleaner {

    override suspend fun clear() {
        withContext(ioDispatcher) { dao.clearAll() }
    }
}
