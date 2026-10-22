package com.radustavila.nasaexplorer.data.news.cache

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.data.news.local.NewsDao
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Each refresh replaces its feed's rows, so the cache stays at about a hundred items while the app
 * is used. This only matters when it is not: news nobody has refreshed for [RETENTION] is no longer
 * news. Favourite Images of the Day always stay, so they open offline; "Clear cache" empties the
 * rest.
 */
class NewsCacheEvictor @Inject constructor(
    private val dao: NewsDao,
    private val favoritesRepository: FavoritesRepository,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CacheEvictor {

    override val name: String = "nasa_news"

    override suspend fun evict(now: Instant): Int = withContext(ioDispatcher) {
        val favoriteIds = favoritesRepository.observeFavoriteIds(FavoriteSource.NASA_IMAGE_OF_THE_DAY).first()
        dao.deleteCachedBefore(now.minus(RETENTION).toEpochMilli(), keepIds = favoriteIds.toList())
    }

    companion object {
        val RETENTION: Duration = Duration.ofDays(30)
    }
}
