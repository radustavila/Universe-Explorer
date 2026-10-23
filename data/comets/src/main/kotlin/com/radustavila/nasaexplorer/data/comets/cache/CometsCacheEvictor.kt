package com.radustavila.nasaexplorer.data.comets.cache

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.data.comets.local.CometsDao
import com.radustavila.nasaexplorer.data.comets.repository.CometRepositoryImpl
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Trims ephemeris days more than [RETENTION] in the past. Each refresh already replaces the
 * candidates and their ±60 days, so this only catches comets whose Horizons requests kept failing.
 *
 * The cutoff never reaches into the window of the newest refresh, so a device that has been offline
 * for months keeps what it last downloaded.
 */
internal class CometsCacheEvictor @Inject constructor(
    private val dao: CometsDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheEvictor {

    override val name: String = "comets"

    override suspend fun evict(now: Instant): Int = withContext(ioDispatcher) {
        val newest = dao.newestEphemerisFetchedAt() ?: return@withContext 0
        val newestWindowStart = newest - Duration.ofDays(CometRepositoryImpl.EPHEMERIS_DAYS_EACH_SIDE + 1).toMillis()
        dao.deleteEphemerisBefore(minOf(now.minus(RETENTION).toEpochMilli(), newestWindowStart))
    }

    companion object {
        val RETENTION: Duration = Duration.ofDays(90)
    }
}

/**
 * "Clear cache" for the comets mirror: it holds no favourites or user data, so both tables are
 * emptied; the next visit downloads them again (Clear cache also makes every source due).
 */
internal class CometsCacheCleaner @Inject constructor(
    private val dao: CometsDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheCleaner {

    override suspend fun clear() {
        withContext(ioDispatcher) { dao.clearAll() }
    }
}
