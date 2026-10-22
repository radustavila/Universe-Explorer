package com.radustavila.nasaexplorer.data.dsn.cache

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.data.dsn.local.DsnDao
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Trims spacecraft names, dish sizes and Horizons distances that no refresh has returned for
 * [RETENTION] (a spacecraft dropped from the list, a dish retired). The live snapshot needs no
 * trimming: every poll replaces it whole, and the last one is kept as the "last good state".
 *
 * The cutoff is never later than the newest refresh of each table, so a device that has been
 * offline for months keeps what it last downloaded.
 */
internal class DsnCacheEvictor @Inject constructor(
    private val dao: DsnDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheEvictor {

    override val name: String = "dsn"

    override suspend fun evict(now: Instant): Int = withContext(ioDispatcher) {
        val retentionCutoff = now.minus(RETENTION).toEpochMilli()
        fun cutoff(newest: Long?): Long? = newest?.let { minOf(it, retentionCutoff) }
        listOfNotNull(
            cutoff(dao.newestConfigFetchedAt())?.let {
                dao.deleteSpacecraftFetchedBefore(it) + dao.deleteDishInfoFetchedBefore(it)
            },
            cutoff(dao.newestDistanceFetchedAt())?.let { dao.deleteDistancesFetchedBefore(it) },
        ).sum()
    }

    companion object {
        val RETENTION: Duration = Duration.ofDays(60)
    }
}

/**
 * "Clear cache" for the DSN mirror: it holds no favourites or user data, so every table is emptied;
 * the next visit downloads it again (Clear cache also makes every source due).
 */
internal class DsnCacheCleaner @Inject constructor(
    private val dao: DsnDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheCleaner {

    override suspend fun clear() {
        withContext(ioDispatcher) { dao.clearAll() }
    }
}
