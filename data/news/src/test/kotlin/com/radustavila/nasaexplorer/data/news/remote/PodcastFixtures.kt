package com.radustavila.nasaexplorer.data.news.remote

import com.radustavila.nasaexplorer.core.model.PodcastShow
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import com.radustavila.nasaexplorer.data.news.remote.dto.FeedDocumentDto
import okhttp3.mockwebserver.MockResponse

/** Recorded from the real feeds (served by Megaphone) on 2026-10-23, trimmed to a few items each. */
internal object PodcastFixtures {
    /** Houston We Have a Podcast: the 5 newest of 444 episodes and the show's one trailer. */
    const val HOUSTON = "podcasts/houston_we_have_a_podcast.xml"

    /**
     * NASA's Curious Universe: the 5 newest of 110 episodes and a trailer. Its enclosures go through
     * a `chrt.fm` tracking redirect, and the newest has no artwork of its own.
     */
    const val CURIOUS_UNIVERSE = "podcasts/curious_universe.xml"

    /** Small Steps, Giant Leaps: the 5 newest of 182 episodes; the newest has no `<link>`. */
    const val SMALL_STEPS = "podcasts/small_steps_giant_leaps.xml"

    val BY_SHOW = mapOf(
        PodcastShow.HOUSTON_WE_HAVE_A_PODCAST to HOUSTON,
        PodcastShow.CURIOUS_UNIVERSE to CURIOUS_UNIVERSE,
        PodcastShow.SMALL_STEPS_GIANT_LEAPS to SMALL_STEPS,
    )

    /** The show artwork of Curious Universe's channel, as the feed writes it. */
    const val CURIOUS_UNIVERSE_SHOW_IMAGE =
        "https://megaphone.imgix.net/podcasts/3c25fbae-6a1b-11ef-b25e-33943a7bea28/image/" +
            "40d679c7583b1f4010567b2d6bf1412e.png?ixlib=rails-4.3.1&max-w=3000&max-h=3000&fit=crop&auto=format,compress"
}

internal fun parsePodcastFixture(path: String): FeedDocumentDto =
    FeedParser.parseDocument(Fixtures.read(path).byteInputStream(), "UTF-8")

/** Each show at nasa.gov's path on the fixture server, which redirects to the Megaphone feed as the real one does. */
internal fun podcastFeedUrls(server: FixtureServer): PodcastFeedUrls =
    PodcastFeedUrls(PodcastShow.entries.associateWith { "${server.baseUrl}feeds/podcasts/${it.key}" })

/** Serves every show: nasa.gov's path answers 302, Megaphone's the recorded feed (or [megaphone]'s answer). */
internal fun FixtureServer.servePodcasts(megaphone: (PodcastShow) -> MockResponse = ::feedResponse) {
    route { request ->
        val path = request.requestUrl!!.encodedPath
        val show = PodcastShow.entries.first { path.endsWith("/${it.key}") }
        if (path.startsWith("/feeds/podcasts/")) {
            MockResponse().setResponseCode(HTTP_FOUND).setHeader("Location", "/megaphone/${show.key}")
        } else {
            megaphone(show)
        }
    }
}

internal fun feedResponse(show: PodcastShow): MockResponse = MockResponse()
    .setHeader("Content-Type", "application/xml; charset=utf-8")
    .setBody(Fixtures.read(PodcastFixtures.BY_SHOW.getValue(show)))

private const val HTTP_FOUND = 302
