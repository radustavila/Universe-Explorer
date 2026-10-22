package com.radustavila.nasaexplorer.data.news.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParserException

/** The XML pull parser is Android's; Robolectric provides it. */
@RunWith(RobolectricTestRunner::class)
class FeedParserTest {

    @Test
    fun `every entry of NASA's RSS feeds is read with its fields`() {
        listOf(NewsFixtures.STORIES, NewsFixtures.NEWS_RELEASES, NewsFixtures.SCIENCE).forEach { path ->
            val entries = parseFixture(path)

            assertEquals(path, 10, entries.size)
            entries.forEach { entry ->
                assertTrue(path, entry.title!!.isNotBlank())
                assertTrue(path, entry.link!!.startsWith("https://"))
                assertTrue(path, entry.published!!.endsWith("+0000"))
                assertTrue(path, entry.description != null && entry.contentHtml != null)
            }
        }
    }

    @Test
    fun `a story keeps its link, date, description and article html`() {
        val hubble = parseFixture(NewsFixtures.STORIES).first()

        assertEquals("NASA’s Hubble Charts Path for Roman with Gravitational Lenses\u00A0", hubble.title)
        assertEquals(
            "https://science.nasa.gov/missions/hubble/nasas-hubble-charts-path-for-roman-with-gravitational-lenses/",
            hubble.link,
        )
        assertEquals("Thu, 22 Oct 2026 16:20:37 +0000", hubble.published)
        assertTrue(hubble.description!!.startsWith("This observation is one of many gravitational lensing images"))
        assertTrue(hubble.contentHtml!!.contains("<img"))
    }

    @Test
    fun `an Image of the Day keeps its enclosure`() {
        val entries = parseFixture(NewsFixtures.IMAGE_OF_THE_DAY)

        assertEquals(12, entries.size)
        val first = entries.first()
        assertEquals("Science on the Space Station with NASA Astronaut Jessica Watkins", first.title)
        assertEquals("https://www.nasa.gov/image-detail/iss075e0190239/", first.link)
        assertEquals("Wed, 21 Oct 2026 15:07 GMT", first.published)
        assertEquals("https://www.nasa.gov/wp-content/uploads/2026/10/iss075e0190239orig.jpg", first.enclosureUrl)
        assertEquals("image/jpeg", first.enclosureType)
        assertTrue(first.description!!.contains("Kibo laboratory module's Life Science Glovebox"))
    }

    @Test
    fun `a YouTube entry is read from Atom and Media RSS inside its group`() {
        val entries = parseFixture(NewsFixtures.YOUTUBE)

        assertEquals(15, entries.size)
        val stream = entries.first()
        assertEquals(
            "Live High-Definition Views from the International Space Station (Official NASA Stream)",
            stream.title,
        )
        assertEquals("https://www.youtube.com/watch?v=ROboUHfPUrU", stream.link)
        assertEquals("ROboUHfPUrU", stream.videoId)
        assertEquals("2026-10-22T14:29:44+00:00", stream.published)
        assertEquals("https://i3.ytimg.com/vi/ROboUHfPUrU/hqdefault.jpg", stream.thumbnailUrl)
        assertTrue(stream.mediaDescription!!.startsWith("Live views from the International Space Station"))
        // Atom's own entry fields are not RSS's.
        assertNull(stream.description)
        assertNull(stream.enclosureUrl)
        assertEquals("NASA's SpaceX Crew-12 Re-Entry & Splashdown", entries[9].title)
    }

    @Test
    fun `malformed items are still returned, as found`() {
        val entries = parseFixture(NewsFixtures.MALFORMED_ITEMS)

        assertEquals(6, entries.size)
        assertNull(entries[0].link)
        assertEquals("   ", entries[1].title)
        assertEquals("sometime last week", entries[2].published)
        val good = entries[4]
        assertEquals("NASA’s Roman Space Telescope Enters Final Commissioning Phase", good.title)
        assertEquals("Thu, 22 Oct 2026 19:43:51 +0000", good.published)
    }

    @Test(expected = XmlPullParserException::class)
    fun `a document cut off halfway fails as a whole`() {
        parseFixture(NewsFixtures.TRUNCATED)
    }
}
