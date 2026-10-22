package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.NewsRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.model.NewsItem
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/** The cached news of every feed, newest first. */
class ObserveNewsUseCase @Inject constructor(
    private val repository: NewsRepository,
) {
    operator fun invoke(): Flow<List<NewsItem>> = repository.observeNews()
}

class ObserveNewsItemUseCase @Inject constructor(
    private val repository: NewsRepository,
) {
    operator fun invoke(id: String): Flow<NewsItem?> = repository.observeItem(id)
}

/**
 * Refreshes every news feed at once, each through [RefreshSourceUseCase] as its own [SyncSource],
 * at most once per [MIN_REFRESH_INTERVAL] unless [force]d (pull-to-refresh). Runs while the app is
 * used (the News screen or the Today card is shown); there is no background job, so news costs no
 * data for someone who never looks at it.
 */
class RefreshNewsUseCase @Inject constructor(
    private val repository: NewsRepository,
    private val refreshSource: RefreshSourceUseCase,
) {
    /**
     * @return the first failure when every feed that was due failed (e.g. offline), else success,
     * also when no feed was due. One failing feed does not hide the others' news.
     */
    suspend operator fun invoke(force: Boolean = false): DataResult<Unit> = coroutineScope {
        val outcomes = NewsFeed.entries.map { feed ->
            async {
                refreshSource(feed.syncSource, minInterval = MIN_REFRESH_INTERVAL, force = force) {
                    repository.refresh(feed)
                }
            }
        }.awaitAll()
        val results = outcomes.filterIsInstance<SourceRefreshResult.Completed<Int>>().map { it.result }
        val failures = results.filterIsInstance<DataResult.Failure>()
        if (results.isNotEmpty() && failures.size == results.size) failures.first() else DataResult.Success(Unit)
    }

    companion object {
        /** The feeds change a few times a day; NASA's servers ask clients to cache for minutes. */
        val MIN_REFRESH_INTERVAL: Duration = Duration.ofHours(3)

        /** Every source the News screen is fed by, for its freshness line. */
        val SOURCES: Array<SyncSource> = NewsFeed.entries.map(NewsFeed::syncSource).toTypedArray()
    }
}

/**
 * Saves an Image of the Day to Favourites with its largest picture as the snapshot, so the offline
 * copy is the picture the item's screen shows.
 */
class SaveNewsImageFavoriteUseCase @Inject constructor(
    private val saveFavorite: SaveFavoriteUseCase,
) {
    suspend operator fun invoke(item: NewsItem): DataResult<Unit> = saveFavorite(
        source = FavoriteSource.NASA_IMAGE_OF_THE_DAY,
        itemId = item.id,
        title = item.title,
        imageUrl = item.fullImageUrl ?: item.imageUrl,
    )
}
