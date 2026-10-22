package com.radustavila.nasaexplorer.data.news.mapper

import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.model.NewsItem
import com.radustavila.nasaexplorer.data.news.local.NewsItemEntity
import java.time.Instant

internal fun NewsItem.toEntity(feed: NewsFeed, cachedAt: Instant) = NewsItemEntity(
    feed = feed.key,
    id = id,
    title = title,
    url = url,
    publishedAtMillis = publishedAt.toEpochMilli(),
    summary = summary,
    imageUrl = imageUrl,
    fullImageUrl = fullImageUrl,
    imageCredit = imageCredit,
    isLive = isLive,
    cachedAtMillis = cachedAt.toEpochMilli(),
)

/**
 * One item per id, in the rows' order. Feeds list the same story with the same text, so the rows
 * differ only in their feed, and the item takes the most specific kind among them (a story NASA's
 * main feed and the science feed both list is a science story). Rows of a feed the app no longer
 * knows are skipped.
 */
internal fun mergeFeeds(rows: List<NewsItemEntity>): List<NewsItem> =
    rows.mapNotNull { row -> NewsFeed.fromKey(row.feed)?.let { feed -> row to feed } }
        .groupBy { (row, _) -> row.id }
        .values
        .map { copies ->
            val (row, feed) = copies.maxBy { (_, feed) -> feed.kind.ordinal }
            NewsItem(
                id = row.id,
                kind = feed.kind,
                title = row.title,
                url = row.url,
                publishedAt = Instant.ofEpochMilli(row.publishedAtMillis),
                summary = row.summary,
                imageUrl = row.imageUrl,
                fullImageUrl = row.fullImageUrl,
                imageCredit = row.imageCredit,
                isLive = row.isLive,
            )
        }
