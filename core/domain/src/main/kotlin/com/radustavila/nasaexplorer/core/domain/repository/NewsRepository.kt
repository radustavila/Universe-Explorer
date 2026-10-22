package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.model.NewsItem
import kotlinx.coroutines.flow.Flow

/**
 * NASA's stories, science articles, Images of the Day and videos (F14), offline-first: reads come
 * from the local cache, and [refresh] writes one feed into it.
 */
interface NewsRepository {

    /** Every cached item, newest first; an item several feeds list appears once. */
    fun observeNews(): Flow<List<NewsItem>>

    fun observeItem(id: String): Flow<NewsItem?>

    /**
     * Downloads [feed] and replaces its cached items (favourites stay). A feed that cannot be read
     * leaves the last good copy in place.
     *
     * @return how many items the feed listed.
     */
    suspend fun refresh(feed: NewsFeed): DataResult<Int>
}
