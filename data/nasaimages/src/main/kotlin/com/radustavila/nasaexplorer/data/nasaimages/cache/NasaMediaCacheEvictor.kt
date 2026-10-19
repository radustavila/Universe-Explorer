package com.radustavila.nasaexplorer.data.nasaimages.cache

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.data.nasaimages.local.NasaMediaDao
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Trims the library cache to what is still referenced: items on a rail stay (they are what the tab
 * opens on, however old), favourites stay (they must open offline), and anything else - an item
 * that dropped off a rail, or one whose favourite was removed - goes once it has not been written
 * for [RETENTION].
 *
 * Favourites live in `NasaDatabase`, so their ids are read through the repository rather than
 * joined.
 */
class NasaMediaCacheEvictor @Inject constructor(
    private val dao: NasaMediaDao,
    private val favoritesRepository: FavoritesRepository,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheEvictor {

    override val name: String = "nasa_library"

    override suspend fun evict(now: Instant): Int = withContext(ioDispatcher) {
        val favoriteIds = favoritesRepository.observeFavoriteIds(FavoriteSource.NASA_LIBRARY).first()
        dao.evictUnreferenced(
            beforeMillis = now.minus(RETENTION).toEpochMilli(),
            keepIds = favoriteIds.toList(),
        )
    }

    companion object {
        /** Grace period, so an item un-saved by mistake can still be re-saved with its details. */
        val RETENTION: Duration = Duration.ofDays(7)
    }
}
