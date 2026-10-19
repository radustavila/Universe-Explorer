package com.radustavila.nasaexplorer.data.mars.cache

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.data.mars.local.MarsPhotoDao
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Every daily sync adds a sol or two of photos per rover; this keeps the cache to the last
 * [RETENTION] of downloads. Favourites stay (they must open offline), and so does each rover's
 * newest sol, so a long time offline never empties the rail.
 *
 * Favourites live in `NasaDatabase`, so their ids are read through the repository rather than
 * joined.
 */
class MarsPhotoCacheEvictor @Inject constructor(
    private val dao: MarsPhotoDao,
    private val favoritesRepository: FavoritesRepository,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheEvictor {

    override val name: String = "mars_photos"

    override suspend fun evict(now: Instant): Int = withContext(ioDispatcher) {
        val favoriteIds = favoritesRepository.observeFavoriteIds(FavoriteSource.MARS).first()
        dao.evictStale(
            beforeMillis = now.minus(RETENTION).toEpochMilli(),
            keepIds = favoriteIds.toList(),
        )
    }

    companion object {
        val RETENTION: Duration = Duration.ofDays(14)
    }
}
