package com.radustavila.nasaexplorer.core.model

import java.time.Instant

/**
 * What a news item is, for the News screen's chips. Declared from the least to the most specific:
 * an item several feeds list (a science story is in NASA's main feed too) takes the most specific
 * kind among them.
 */
enum class NewsKind {
    /** A story or news release from nasa.gov. */
    STORY,

    /** An article from science.nasa.gov. */
    SCIENCE,

    /** NASA's Image of the Day. */
    IMAGE_OF_THE_DAY,

    /** A video or live stream on NASA's YouTube channel. */
    VIDEO,
}

/**
 * One of the public feeds the News screen is built from. [key] is persisted with each cached item:
 * never rename or reuse one.
 */
enum class NewsFeed(val key: String, val kind: NewsKind, val syncSource: SyncSource) {
    /** `www.nasa.gov/feed/`: the 10 latest stories. */
    STORIES("stories", NewsKind.STORY, SyncSource.NASA_NEWS),

    /** `www.nasa.gov/news-release/feed/`: the 10 latest news releases. */
    NEWS_RELEASES("news_releases", NewsKind.STORY, SyncSource.NASA_NEWS_RELEASES),

    /** `science.nasa.gov/feed/`: the 10 latest science articles. */
    SCIENCE("science", NewsKind.SCIENCE, SyncSource.NASA_SCIENCE_NEWS),

    /** `www.nasa.gov/feeds/iotd-feed/`: the last 60 Images of the Day. */
    IMAGE_OF_THE_DAY("image_of_the_day", NewsKind.IMAGE_OF_THE_DAY, SyncSource.NASA_IMAGE_OF_THE_DAY),

    /** NASA's YouTube channel feed: the 15 latest videos, live streams included. */
    YOUTUBE("youtube", NewsKind.VIDEO, SyncSource.NASA_YOUTUBE),
    ;

    companion object {
        fun fromKey(key: String): NewsFeed? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A story, article, Image of the Day or video from NASA's public feeds.
 *
 * Texts are NASA's own (public domain); [imageCredit] names whoever else holds rights to the
 * picture and must be shown with it.
 */
data class NewsItem(
    /** The item's page, the same in every feed that lists it, so it identifies the item. */
    val id: String,
    val kind: NewsKind,
    val title: String,
    /** Where the item opens: its web page, or the video on YouTube. */
    val url: String,
    val publishedAt: Instant,
    /** A sentence or two of plain text. */
    val summary: String? = null,
    /** A picture sized for a list row. */
    val imageUrl: String? = null,
    /** The largest picture worth showing on its own (Images of the Day); else [imageUrl]. */
    val fullImageUrl: String? = imageUrl,
    val imageCredit: String? = null,
    /** A live stream (e.g. the 24/7 views from the space station), as far as the feed tells. */
    val isLive: Boolean = false,
)
