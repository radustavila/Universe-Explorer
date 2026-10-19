package com.radustavila.nasaexplorer.data.exoplanets.cache

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.data.exoplanets.local.ExoplanetDao
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Trims planets that no refresh has returned for [RETENTION]: discoveries that aged out of the
 * "newest" window, or rows the archive renamed or retracted.
 *
 * - The cutoff is never later than the newest refresh, so a device that has been offline for
 *   months keeps its whole last catalogue rather than losing everything.
 * - Favourites are never deleted, so a saved planet keeps opening offline.
 */
class ExoplanetCacheEvictor @Inject constructor(
    private val dao: ExoplanetDao,
    private val favoritesRepository: FavoritesRepository,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheEvictor {

    override val name: String = "exoplanets"

    override suspend fun evict(now: Instant): Int = withContext(ioDispatcher) {
        val newestRefresh = dao.newestFetchedAt() ?: return@withContext 0
        val cutoff = minOf(newestRefresh, now.minus(RETENTION).toEpochMilli())
        val favorites = favoritesRepository.observeFavoriteIds(FavoriteSource.EXOPLANET).first()
        dao.deleteFetchedBefore(cutoffMillis = cutoff, keep = favorites.toList())
    }

    companion object {
        val RETENTION: Duration = Duration.ofDays(30)
    }
}
