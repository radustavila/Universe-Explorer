package com.radustavila.nasaexplorer.data.news.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One entry of one feed. An item several feeds list (a science story is in NASA's main feed too)
 * has a row per feed, so each feed's refresh replaces only its own rows; reads merge them.
 */
@Entity(
    tableName = "news_item",
    primaryKeys = ["feed", "id"],
    indices = [Index("published_at"), Index("id")],
)
data class NewsItemEntity(
    /** `NewsFeed.key`. */
    @ColumnInfo(name = "feed")
    val feed: String,
    /** The item's page URL. */
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "url")
    val url: String,
    @ColumnInfo(name = "published_at")
    val publishedAtMillis: Long,
    @ColumnInfo(name = "summary")
    val summary: String?,
    @ColumnInfo(name = "image_url")
    val imageUrl: String?,
    @ColumnInfo(name = "full_image_url")
    val fullImageUrl: String?,
    @ColumnInfo(name = "image_credit")
    val imageCredit: String?,
    @ColumnInfo(name = "is_live")
    val isLive: Boolean,
    /** When the feed last listed it, for eviction. */
    @ColumnInfo(name = "cached_at")
    val cachedAtMillis: Long,
)
