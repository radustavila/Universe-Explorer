package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.NewsItem
import com.radustavila.nasaexplorer.core.model.NewsKind
import java.time.Instant

/** News items shaped like the real feeds' (recorded 2026-10-22). */
object TestNews {

    val NOW: Instant = Instant.parse("2026-10-22T20:00:00Z")

    fun story(
        id: String = "https://www.nasa.gov/blogs/spacestation/2026/10/22/busy-station-day/",
        title: String = "Busy Station Day with Bioprinting, Spacesuit Check, Robotics, and Photography",
        publishedAt: Instant = Instant.parse("2026-10-22T17:37:14Z"),
        imageCredit: String? = "NASA/Josh Valcarcel",
    ): NewsItem = NewsItem(
        id = id,
        kind = NewsKind.STORY,
        title = title,
        url = id,
        publishedAt = publishedAt,
        summary = "The Expedition 75 crew had a busy day on Thursday bioprinting blood vessels and trying on a " +
            "spacesuit.",
        imageUrl = "https://images-assets.nasa.gov/image/jsc2026e037789/jsc2026e037789~large.jpg?w=600",
        imageCredit = imageCredit,
    )

    fun science(
        id: String = "https://science.nasa.gov/missions/hubble/nasas-hubble-charts-path-for-roman/",
        title: String = "NASA’s Hubble Charts Path for Roman with Gravitational Lenses",
        publishedAt: Instant = Instant.parse("2026-10-22T16:20:37Z"),
    ): NewsItem = NewsItem(
        id = id,
        kind = NewsKind.SCIENCE,
        title = title,
        url = id,
        publishedAt = publishedAt,
        summary = "This observation is one of many gravitational lensing images taken by Hubble.",
        imageUrl = "https://assets.science.nasa.gov/dynamicimage/assets/science/missions/hubble/lens.jpg?w=600",
        imageCredit = "NASA, ESA, and X. Huang (University of San Francisco)",
    )

    fun imageOfTheDay(
        id: String = "https://www.nasa.gov/image-detail/iss075e0190239/",
        title: String = "Science on the Space Station with NASA Astronaut Jessica Watkins",
        publishedAt: Instant = Instant.parse("2026-10-21T15:07:00Z"),
    ): NewsItem = NewsItem(
        id = id,
        kind = NewsKind.IMAGE_OF_THE_DAY,
        title = title,
        url = id,
        publishedAt = publishedAt,
        summary = "NASA astronaut and Expedition 75 flight engineer Jessica Watkins works inside the Kibo laboratory.",
        imageUrl = "https://www.nasa.gov/wp-content/uploads/2026/10/iss075e0190239orig.jpg?w=800",
        fullImageUrl = "https://www.nasa.gov/wp-content/uploads/2026/10/iss075e0190239orig.jpg?w=2048",
    )

    fun video(
        videoId: String = "ROboUHfPUrU",
        title: String = "Live High-Definition Views from the International Space Station (Official NASA Stream)",
        publishedAt: Instant = Instant.parse("2026-10-22T14:29:44Z"),
        isLive: Boolean = true,
    ): NewsItem = NewsItem(
        id = "https://www.youtube.com/watch?v=$videoId",
        kind = NewsKind.VIDEO,
        title = title,
        url = "https://www.youtube.com/watch?v=$videoId",
        publishedAt = publishedAt,
        summary = "Live views from the International Space Station are streaming from an external camera.",
        imageUrl = "https://i3.ytimg.com/vi/$videoId/hqdefault.jpg",
        isLive = isLive,
    )

    /** One item of every kind, newest first. */
    val ALL: List<NewsItem> = listOf(story(), science(), video(), imageOfTheDay())
}
