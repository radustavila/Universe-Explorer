package com.radustavila.nasaexplorer.data.sky.cache

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.data.sky.local.OrbitalElementsDao
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Drops element sets whose epoch is more than [RETENTION] old. Predictions already refuse them
 * after two weeks (see `ORBITAL_ELEMENTS_MAX_AGE`); past a month they only describe an orbit the
 * station has left, so the screen shows "no orbit data" instead of a track that is wrong. Nothing
 * here can be a favourite.
 */
class OrbitalElementsCacheEvictor @Inject constructor(
    private val dao: OrbitalElementsDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheEvictor {

    override val name: String = "orbital_elements"

    override suspend fun evict(now: Instant): Int = withContext(ioDispatcher) {
        dao.deleteWithEpochBefore(now.minus(RETENTION).toEpochMilli())
    }

    companion object {
        val RETENTION: Duration = Duration.ofDays(30)
    }
}
