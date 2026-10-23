package com.radustavila.nasaexplorer.data.earthevents.cache

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.data.earthevents.local.EarthEventDao
import com.radustavila.nasaexplorer.data.earthevents.local.EarthquakeDao
import com.radustavila.nasaexplorer.data.earthevents.remote.EonetRemoteDataSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Each refresh replaces the whole events list, so this only matters while refreshes fail (offline
 * for weeks): events with no report in the active window are no longer "right now" and go, as the
 * next refresh would drop them too. Imagery layers are re-fetched once a month in case EONET's
 * suggestions changed. Quakes older than the longest feed window (a month) go too. Events and
 * quakes cannot be favourites, so nothing else needs keeping.
 */
class EarthEventsCacheEvictor @Inject constructor(
    private val dao: EarthEventDao,
    private val earthquakeDao: EarthquakeDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheEvictor {

    override val name: String = "earth_events"

    override suspend fun evict(now: Instant): Int = withContext(ioDispatcher) {
        dao.deleteEventsUpdatedBefore(now.minus(EVENT_RETENTION).toEpochMilli()) +
            dao.deleteLayersCachedBefore(now.minus(LAYER_RETENTION).toEpochMilli()) +
            earthquakeDao.deleteEarthquakesBefore(now.minus(EARTHQUAKE_RETENTION).toEpochMilli())
    }

    companion object {
        val EVENT_RETENTION: Duration = Duration.ofDays(EonetRemoteDataSource.ACTIVE_WINDOW_DAYS.toLong())
        val LAYER_RETENTION: Duration = Duration.ofDays(30)
        val EARTHQUAKE_RETENTION: Duration = Duration.ofDays(31)
    }
}
