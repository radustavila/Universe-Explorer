package com.radustavila.nasaexplorer.data.news.mapper

import com.radustavila.nasaexplorer.core.model.PodcastShow
import com.radustavila.nasaexplorer.data.news.remote.PodcastFixtures
import com.radustavila.nasaexplorer.data.news.remote.dto.FeedEntryDto
import com.radustavila.nasaexplorer.data.news.remote.parsePodcastFixture
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PodcastMappersTest {

    private fun episodes(show: PodcastShow, path: String) = parsePodcastFixture(path).let { document ->
        document.entries.map { requireNotNull(it.toPodcastEpisodeOrNull(show, document.imageUrl)) }
    }

    @Test
    fun `a recorded episode maps with its id, date, duration, artwork and plain-text notes`() {
        val episode = episodes(PodcastShow.HOUSTON_WE_HAVE_A_PODCAST, PodcastFixtures.HOUSTON).first()

        assertEquals("04ba5ab8-c8db-11f1-896a-63bcf4cc8ae7", episode.id)
        assertEquals(PodcastShow.HOUSTON_WE_HAVE_A_PODCAST, episode.show)
        assertEquals(Instant.parse("2026-10-16T13:00:00Z"), episode.publishedAt)
        assertEquals(Duration.ofSeconds(3_617), episode.duration)
        assertTrue(episode.summary!!.startsWith("Artemis II astronauts Victor Glover and Jeremy Hansen"))
        assertTrue('<' !in episode.summary!!)
        assertEquals(
            "https://megaphone.imgix.net/podcasts/04ba5ab8-c8db-11f1-896a-63bcf4cc8ae7/image/" +
                "86a30829c1f0b6d8e21350bfca8562f4.png?ixlib=rails-4.3.1&fit=crop&auto=format,compress&w=600&h=600",
            episode.imageUrl,
        )
    }

    @Test
    fun `the chrt_fm enclosure is streamed as given and an episode without artwork takes the show's`() {
        val newest = episodes(PodcastShow.CURIOUS_UNIVERSE, PodcastFixtures.CURIOUS_UNIVERSE).first()

        assertEquals(
            "https://chrt.fm/track/477F33/traffic.megaphone.fm/NATIONALAERONAUTICSANDSPACEADMINISTRATION6420899881.mp3",
            newest.audioUrl,
        )
        assertEquals(artworkUrl(PodcastFixtures.CURIOUS_UNIVERSE_SHOW_IMAGE), newest.imageUrl)
        assertTrue(newest.summary!!.contains("\n\nThis episode was originally published in 2023."))
    }

    @Test
    fun `an episode without a page keeps none`() {
        val (newest, older) = episodes(PodcastShow.SMALL_STEPS_GIANT_LEAPS, PodcastFixtures.SMALL_STEPS).let { it[0] to it[1] }

        assertNull(newest.pageUrl)
        assertEquals(
            "https://www.nasa.gov/podcasts/small-steps-giant-leaps/human-factors-designing-systems-with-people-in-mind-part-2-of-2/",
            older.pageUrl,
        )
    }

    @Test
    fun `entries without audio, title or date are dropped`() {
        val good = FeedEntryDto(
            title = "Episode",
            published = "Fri, 16 Oct 2026 13:00:00 -0000",
            enclosureUrl = "https://traffic.megaphone.fm/a.mp3",
            enclosureType = "audio/mpeg",
        )
        val show = PodcastShow.HOUSTON_WE_HAVE_A_PODCAST

        assertEquals("https://traffic.megaphone.fm/a.mp3", good.toPodcastEpisodeOrNull(show, null)?.id)
        assertNull(good.copy(enclosureUrl = null).toPodcastEpisodeOrNull(show, null))
        assertNull(good.copy(enclosureType = "video/mp4").toPodcastEpisodeOrNull(show, null))
        assertNull(good.copy(title = " ").toPodcastEpisodeOrNull(show, null))
        assertNull(good.copy(published = "yesterday").toPodcastEpisodeOrNull(show, null))
    }

    @Test
    fun `durations are seconds or clock-style`() {
        assertEquals(Duration.ofSeconds(3_617), parseItunesDuration("3617"))
        assertEquals(Duration.ofSeconds(3_617), parseItunesDuration("1:00:17"))
        assertEquals(Duration.ofSeconds(754), parseItunesDuration("12:34"))
        assertNull(parseItunesDuration("0"))
        assertNull(parseItunesDuration("an hour"))
        assertNull(parseItunesDuration("1:2:3:4"))
    }

    @Test
    fun `only Megaphone artwork is resized`() {
        assertEquals("https://example.com/a.png", artworkUrl("https://example.com/a.png"))
        assertEquals(
            "https://megaphone.imgix.net/p/a.png?fit=crop&w=600&h=600",
            artworkUrl("https://megaphone.imgix.net/p/a.png?max-w=3000&max-h=3000&fit=crop"),
        )
    }
}
