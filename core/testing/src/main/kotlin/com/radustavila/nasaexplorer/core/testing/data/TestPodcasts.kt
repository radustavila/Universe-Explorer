package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastShow
import java.time.Duration
import java.time.Instant

/** Podcast episodes shaped like the real feeds' (recorded 2026-10-23). */
object TestPodcasts {

    val NOW: Instant = Instant.parse("2026-10-23T12:00:00Z")

    fun houston(
        id: String = "04ba5ab8-c8db-11f1-896a-63bcf4cc8ae7",
        title: String = "Reflections After Artemis II with Victor and Jeremy",
        publishedAt: Instant = Instant.parse("2026-10-16T13:00:00Z"),
    ): PodcastEpisode = PodcastEpisode(
        id = id,
        show = PodcastShow.HOUSTON_WE_HAVE_A_PODCAST,
        title = title,
        publishedAt = publishedAt,
        audioUrl = "https://traffic.megaphone.fm/NATIONALAERONAUTICSANDSPACEADMINISTRATION6740428946.mp3",
        duration = Duration.parse("PT1H17S"),
        summary = "Artemis II astronauts Victor Glover and Jeremy Hansen reflect on memorable mission moments, new " +
            "perspectives, and what comes next after their historic journey around the Moon. Episode 441.",
        imageUrl = "https://megaphone.imgix.net/podcasts/04ba5ab8-c8db-11f1-896a-63bcf4cc8ae7/image/" +
            "86a30829c1f0b6d8e21350bfca8562f4.png?ixlib=rails-4.3.1&fit=crop&auto=format,compress&w=600&h=600",
        pageUrl = "https://www.nasa.gov/podcasts/houston-we-have-a-podcast/reflections-after-artemis-ii",
    )

    fun curiousUniverse(
        id: String = "cf450786-c99b-11f1-afe5-c3118dc95724",
        title: String = "Encore: A Year In Mars Dune Alpha",
        publishedAt: Instant = Instant.parse("2026-10-20T13:40:00Z"),
    ): PodcastEpisode = PodcastEpisode(
        id = id,
        show = PodcastShow.CURIOUS_UNIVERSE,
        title = title,
        publishedAt = publishedAt,
        audioUrl = "https://chrt.fm/track/477F33/traffic.megaphone.fm/" +
            "NATIONALAERONAUTICSANDSPACEADMINISTRATION6420899881.mp3",
        duration = Duration.parse("PT26M47S"),
        summary = "To prepare for the day when humans travel to Mars, NASA is conducting a one-year experiment in a " +
            "Mars simulation environment.",
        imageUrl = "https://megaphone.imgix.net/podcasts/3c25fbae-6a1b-11ef-b25e-33943a7bea28/image/" +
            "40d679c7583b1f4010567b2d6bf1412e.png?ixlib=rails-4.3.1&fit=crop&auto=format,compress&w=600&h=600",
        pageUrl = "https://www.nasa.gov/podcasts/curious-universe/encore-a-year-in-mars-dune-alpha/",
    )

    fun smallSteps(
        id: String = "6cf7c0c2-c7fd-11f1-82ad-2765dd59474d",
        title: String = "State Hubs: Opening Doors for Technical Students",
        publishedAt: Instant = Instant.parse("2026-10-14T18:31:00Z"),
    ): PodcastEpisode = PodcastEpisode(
        id = id,
        show = PodcastShow.SMALL_STEPS_GIANT_LEAPS,
        title = title,
        publishedAt = publishedAt,
        audioUrl = "https://traffic.megaphone.fm/NATIONALAERONAUTICSANDSPACEADMINISTRATION5643462700.mp3",
        duration = Duration.parse("PT17M59S"),
        summary = "NASA, through the Office of STEM Engagement, has launched the State Hubs Initiative, awarding " +
            "\$10.5 million to seven institutions.",
        imageUrl = "https://megaphone.imgix.net/podcasts/6cf7c0c2-c7fd-11f1-82ad-2765dd59474d/image/" +
            "c46a75caa92d3eacc6dc088792eb098b.jpg?ixlib=rails-4.3.1&fit=crop&auto=format,compress&w=600&h=600",
    )
}
