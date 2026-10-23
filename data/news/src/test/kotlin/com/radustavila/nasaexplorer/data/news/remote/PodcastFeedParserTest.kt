package com.radustavila.nasaexplorer.data.news.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** NASA's podcast feeds as Megaphone serves them: RSS 2.0 with the iTunes extension. */
@RunWith(RobolectricTestRunner::class)
class PodcastFeedParserTest {

    @Test
    fun `every episode of every show is read with its audio and iTunes fields`() {
        PodcastFixtures.BY_SHOW.values.forEach { path ->
            val document = parsePodcastFixture(path)

            assertTrue(path, document.entries.size >= 5)
            assertTrue(path, document.imageUrl!!.startsWith("https://megaphone.imgix.net/podcasts/"))
            document.entries.forEach { entry ->
                assertTrue(path, entry.title!!.isNotBlank())
                assertTrue(path, entry.guid!!.isNotBlank())
                assertTrue(path, entry.published!!.endsWith("-0000"))
                assertEquals(path, "audio/mpeg", entry.enclosureType)
                assertTrue(path, entry.enclosureUrl!!.substringBefore('?').endsWith(".mp3"))
                assertTrue(path, entry.duration!!.toLong() > 0)
            }
        }
    }

    @Test
    fun `an episode keeps its guid, duration, artwork and show notes`() {
        val reflections = parsePodcastFixture(PodcastFixtures.HOUSTON).entries.first()

        assertEquals("Reflections After Artemis II with Victor and Jeremy", reflections.title)
        assertEquals("04ba5ab8-c8db-11f1-896a-63bcf4cc8ae7", reflections.guid)
        assertEquals("Fri, 16 Oct 2026 13:00:00 -0000", reflections.published)
        assertEquals("3617", reflections.duration)
        assertEquals("full", reflections.episodeType)
        assertEquals(
            "https://traffic.megaphone.fm/NATIONALAERONAUTICSANDSPACEADMINISTRATION6740428946.mp3",
            reflections.enclosureUrl,
        )
        assertTrue(reflections.itunesImageUrl!!.contains("86a30829c1f0b6d8e21350bfca8562f4.png"))
        assertTrue(reflections.contentHtml!!.trim().startsWith("<p>Artemis II astronauts"))
        assertEquals(
            "https://www.nasa.gov/podcasts/houston-we-have-a-podcast/reflections-after-artemis-ii",
            reflections.link,
        )
    }

    @Test
    fun `Curious Universe's enclosures keep their chrt_fm redirect and the show's artwork is the channel's`() {
        val document = parsePodcastFixture(PodcastFixtures.CURIOUS_UNIVERSE)
        val newest = document.entries.first()

        assertEquals(PodcastFixtures.CURIOUS_UNIVERSE_SHOW_IMAGE, document.imageUrl)
        assertEquals(
            "https://chrt.fm/track/477F33/traffic.megaphone.fm/NATIONALAERONAUTICSANDSPACEADMINISTRATION6420899881.mp3",
            newest.enclosureUrl,
        )
        assertNull(newest.itunesImageUrl)
        assertTrue(document.entries.all { it.enclosureUrl!!.startsWith("https://chrt.fm/track/") })
        assertEquals("trailer", document.entries.last().episodeType)
    }

    @Test
    fun `the news feeds still parse without podcast fields`() {
        val document = FeedParser.parseDocument(
            com.radustavila.nasaexplorer.core.testing.http.Fixtures.read(NewsFixtures.STORIES).byteInputStream(),
            "UTF-8",
        )

        assertEquals(10, document.entries.size)
        assertNull(document.imageUrl)
        assertTrue(document.entries.all { it.duration == null && it.itunesImageUrl == null })
    }
}
