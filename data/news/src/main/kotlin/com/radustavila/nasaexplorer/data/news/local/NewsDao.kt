package com.radustavila.nasaexplorer.data.news.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NewsDao {

    @Query("SELECT * FROM news_item ORDER BY published_at DESC, id")
    abstract fun observeAll(): Flow<List<NewsItemEntity>>

    @Query("SELECT * FROM news_item WHERE id = :id")
    abstract fun observeById(id: String): Flow<List<NewsItemEntity>>

    @Query("DELETE FROM news_item WHERE feed = :feed AND id NOT IN (:keepIds)")
    protected abstract suspend fun deleteFeedExcept(feed: String, keepIds: List<String>)

    @Upsert
    protected abstract suspend fun upsert(items: List<NewsItemEntity>)

    /**
     * Installs a feed's fresh entries in one transaction: rows it no longer lists go, except
     * [keepIds] (favourites, which must keep opening offline).
     */
    @Transaction
    open suspend fun replaceFeed(feed: String, items: List<NewsItemEntity>, keepIds: Collection<String>) {
        deleteFeedExcept(feed, (keepIds + items.map(NewsItemEntity::id)).distinct())
        upsert(items)
    }

    @Query("DELETE FROM news_item WHERE cached_at < :beforeMillis AND id NOT IN (:keepIds)")
    abstract suspend fun deleteCachedBefore(beforeMillis: Long, keepIds: List<String>): Int
}
