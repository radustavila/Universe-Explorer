package com.radustavila.nasaexplorer.data.spaceweather.cache

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.data.spaceweather.local.SpaceWeatherDao
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Trims Kp history past [KP_RETENTION] (the screen shows the last day and the next three) and flares
 * and CMEs past [SOLAR_ACTIVITY_RETENTION]. The newest flare and CME are kept whatever their age, so
 * a long offline spell still shows the last known ones. Nothing here can be a favourite.
 */
class SpaceWeatherCacheEvictor @Inject constructor(
    private val dao: SpaceWeatherDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheEvictor {

    override val name: String = "space_weather"

    override suspend fun evict(now: Instant): Int = withContext(ioDispatcher) {
        dao.deleteKpReadingsBefore(now.minus(KP_RETENTION).toEpochMilli()) +
            dao.deleteFlaresBefore(now.minus(SOLAR_ACTIVITY_RETENTION).toEpochMilli()) +
            dao.deleteCmesBefore(now.minus(SOLAR_ACTIVITY_RETENTION).toEpochMilli())
    }

    companion object {
        val KP_RETENTION: Duration = Duration.ofDays(14)
        val SOLAR_ACTIVITY_RETENTION: Duration = Duration.ofDays(60)
    }
}
