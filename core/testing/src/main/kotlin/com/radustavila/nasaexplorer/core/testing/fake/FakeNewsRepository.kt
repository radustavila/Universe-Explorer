package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.NewsRepository
import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.model.NewsItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [NewsRepository]: the cache is whatever the test seeds, newest first; refreshes are scripted. */
class FakeNewsRepository(initialItems: List<NewsItem> = emptyList()) : NewsRepository {

    private val items = MutableStateFlow(initialItems.sortedByDescending(NewsItem::publishedAt))

    /** What [refresh] answers per feed; feeds without an entry succeed with 0 items. */
    var refreshResults: Map<NewsFeed, DataResult<Int>> = emptyMap()

    /** The feeds [refresh] was called for, in order. */
    val refreshedFeeds = mutableListOf<NewsFeed>()

    fun setItems(value: List<NewsItem>) {
        items.value = value.sortedByDescending(NewsItem::publishedAt)
    }

    override fun observeNews(): Flow<List<NewsItem>> = items

    override fun observeItem(id: String): Flow<NewsItem?> = items.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun refresh(feed: NewsFeed): DataResult<Int> {
        synchronized(refreshedFeeds) { refreshedFeeds += feed }
        return refreshResults[feed] ?: DataResult.Success(0)
    }
}
