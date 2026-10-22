package com.radustavila.nasaexplorer.data.news.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.domain.repository.NewsRepository
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.model.NewsItem
import com.radustavila.nasaexplorer.data.news.local.NewsLocalDataSource
import com.radustavila.nasaexplorer.data.news.mapper.toNewsItemOrNull
import com.radustavila.nasaexplorer.data.news.remote.NewsRemoteDataSource
import com.radustavila.nasaexplorer.data.news.remote.dto.FeedEntryDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * NASA's feeds, offline-first: screens read Room, and a refresh replaces one feed's rows in a
 * transaction. The feeds are public pages rather than documented APIs, so a refresh that cannot
 * read anything usable changes nothing: the last good copy stays.
 */
@Singleton
class NewsRepositoryImpl @Inject constructor(
    private val localDataSource: NewsLocalDataSource,
    private val remoteDataSource: NewsRemoteDataSource,
    private val favoritesRepository: FavoritesRepository,
    private val timeProvider: TimeProvider,
) : NewsRepository {

    override fun observeNews(): Flow<List<NewsItem>> = localDataSource.observeNews()

    override fun observeItem(id: String): Flow<NewsItem?> = localDataSource.observeItem(id)

    override suspend fun refresh(feed: NewsFeed): DataResult<Int> =
        remoteDataSource.fetch(feed).flatMap { entries -> store(feed, entries) }

    private suspend fun store(feed: NewsFeed, entries: List<FeedEntryDto>): DataResult<Int> {
        val items = entries.mapNotNull { it.toNewsItemOrNull(feed) }.distinctBy(NewsItem::id)
        // NASA's feeds are never empty: nothing usable means the format changed, not a quiet day.
        if (items.isEmpty()) return DataResult.Failure(AppError.EmptyResponse)

        val favorites = favoritesRepository.observeFavoriteIds(FavoriteSource.NASA_IMAGE_OF_THE_DAY).first()
        return localDataSource.replaceFeed(feed, items, keepIds = favorites, at = timeProvider.now())
            .map { items.size }
    }
}
