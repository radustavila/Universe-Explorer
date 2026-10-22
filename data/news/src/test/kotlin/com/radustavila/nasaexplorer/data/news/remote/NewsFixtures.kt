package com.radustavila.nasaexplorer.data.news.remote

import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import com.radustavila.nasaexplorer.data.news.remote.dto.FeedEntryDto
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.create

/** Recorded from the real feeds on 2026-10-22. */
internal object NewsFixtures {
    /** `www.nasa.gov/feed/`: 10 items, one of them that day's APOD. */
    const val STORIES = "news/stories.xml"

    /** `www.nasa.gov/news-release/feed/`: 10 items, two APODs, the rest also in [STORIES]. */
    const val NEWS_RELEASES = "news/releases.xml"

    /** `science.nasa.gov/feed/`: 10 items; the Hubble lens story is in [STORIES] too. */
    const val SCIENCE = "news/science.xml"

    /** `www.nasa.gov/feeds/iotd-feed/`, trimmed to its 12 newest of 60 items. */
    const val IMAGE_OF_THE_DAY = "news/image_of_the_day.xml"

    /** NASA's YouTube channel feed: 15 videos, the 24/7 space station stream first. */
    const val YOUTUBE = "news/youtube.xml"

    /** Hand-made from [STORIES]: one good item among items without link, title or date, and an APOD. */
    const val MALFORMED_ITEMS = "news/malformed_items.xml"

    /** [YOUTUBE] cut off halfway, as a dropped connection would leave it. */
    const val TRUNCATED = "news/truncated.xml"

    val BY_FEED = mapOf(
        NewsFeed.STORIES to STORIES,
        NewsFeed.NEWS_RELEASES to NEWS_RELEASES,
        NewsFeed.SCIENCE to SCIENCE,
        NewsFeed.IMAGE_OF_THE_DAY to IMAGE_OF_THE_DAY,
        NewsFeed.YOUTUBE to YOUTUBE,
    )
}

internal fun parseFixture(path: String): List<FeedEntryDto> =
    FeedParser.parse(Fixtures.read(path).byteInputStream(), "UTF-8")

internal fun newsFeedService(server: FixtureServer): NewsFeedService =
    RetrofitFactory(Json, OkHttpClient()).create(server.baseUrl).create()

/** Each feed at its own path on the fixture server, as the app has one URL per feed. */
internal fun newsFeedUrls(server: FixtureServer): NewsFeedUrls =
    NewsFeedUrls(NewsFeed.entries.associateWith { "${server.baseUrl}${it.key}" })
