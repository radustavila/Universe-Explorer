package com.radustavila.nasaexplorer.data.news.mapper

import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.model.NewsKind
import com.radustavila.nasaexplorer.data.news.remote.NewsFixtures
import com.radustavila.nasaexplorer.data.news.remote.dto.FeedEntryDto
import com.radustavila.nasaexplorer.data.news.remote.parseFixture
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NewsMappersTest {

    private fun items(path: String, feed: NewsFeed) = parseFixture(path).mapNotNull { it.toNewsItemOrNull(feed) }

    @Test
    fun `a story becomes plain text with its first picture and that picture's credit`() {
        val hubble = items(NewsFixtures.STORIES, NewsFeed.STORIES).first()

        assertEquals(hubble.url, hubble.id)
        assertEquals(NewsKind.STORY, hubble.kind)
        assertEquals("NASA’s Hubble Charts Path for Roman with Gravitational Lenses", hubble.title)
        assertEquals(Instant.parse("2026-10-22T16:20:37Z"), hubble.publishedAt)
        assertEquals(
            "This observation is one of many gravitational lensing images taken by Hubble in preparation for " +
                "science with the new Nancy Grace Roman Space Telescope.",
            hubble.summary,
        )
        // The smallest srcset candidate wide enough for a row, the full picture for its own screen.
        assertTrue(hubble.imageUrl!!.endsWith("Hubble_DESI194.5345+03.5358_74_flat_FINALok.tif?w=900"))
        assertTrue(hubble.fullImageUrl!!.endsWith("Hubble_DESI194.5345+03.5358_74_flat_FINALok.tif?w=2048"))
        assertEquals(
            "NASA, ESA, and X. Huang (University of San Francisco); Processing: Gladys Kober " +
                "(NASA/Catholic University of America)",
            hubble.imageCredit,
        )
        assertFalse(hubble.isLive)
    }

    @Test
    fun `the day's APOD article is left out of the news`() {
        val stories = items(NewsFixtures.STORIES, NewsFeed.STORIES)
        val releases = items(NewsFixtures.NEWS_RELEASES, NewsFeed.NEWS_RELEASES)

        assertEquals(9, stories.size)
        assertEquals(8, releases.size)
        assertTrue((stories + releases).none { "apod" in it.id })
    }

    @Test
    fun `a science post loses WordPress's footer`() {
        val science = items(NewsFixtures.SCIENCE, NewsFeed.SCIENCE)
        val roman = science.first()
        val r5 = science[1]

        assertEquals(NewsKind.SCIENCE, roman.kind)
        assertTrue(roman.summary!!.startsWith("NASA’s Nancy Grace Roman Space Telescope mission has now entered"))
        assertTrue(roman.summary!!, roman.summary!!.endsWith("…"))
        assertFalse(roman.summary!!.contains("appeared first on"))
        assertNull(roman.imageUrl)
        // A hero picture without a caption has no credit; the station photo's caption has one.
        assertNull(r5.imageCredit)
        assertEquals("NASA/Josh Valcarcel", science[2].imageCredit)
        // "?resize=300,225": commas inside a candidate URL do not split it.
        assertEquals("https://www.nasa.gov/wp-content/uploads/2026/09/jsc2026e390449.jpg?resize=900,675", r5.imageUrl)
    }

    @Test
    fun `an Image of the Day asks nasa gov for resized copies, never the original upload`() {
        val images = items(NewsFixtures.IMAGE_OF_THE_DAY, NewsFeed.IMAGE_OF_THE_DAY)

        assertEquals(12, images.size)
        val watkins = images.first()
        assertEquals(NewsKind.IMAGE_OF_THE_DAY, watkins.kind)
        assertEquals(Instant.parse("2026-10-21T15:07:00Z"), watkins.publishedAt)
        val uploads = "https://www.nasa.gov/wp-content/uploads/2026/10"
        assertEquals("$uploads/iss075e0190239orig.jpg?w=800", watkins.imageUrl)
        assertEquals("$uploads/iss075e0190239orig.jpg?w=2048", watkins.fullImageUrl)
        // The 208 MB PNG of 2026-10-20.
        assertEquals("$uploads/55572978759-545472774e-o.png?w=2048", images[1].fullImageUrl)
        assertTrue(watkins.summary!!.contains("Kibo laboratory module's Life Science Glovebox"))
    }

    @Test
    fun `a video links to YouTube and only the stream is live`() {
        val videos = items(NewsFixtures.YOUTUBE, NewsFeed.YOUTUBE)

        assertEquals(15, videos.size)
        val stream = videos.first()
        assertEquals(NewsKind.VIDEO, stream.kind)
        assertEquals("https://www.youtube.com/watch?v=ROboUHfPUrU", stream.url)
        assertEquals(Instant.parse("2026-10-22T14:29:44Z"), stream.publishedAt)
        assertEquals("https://i3.ytimg.com/vi/ROboUHfPUrU/hqdefault.jpg", stream.imageUrl)
        assertTrue(stream.isLive)
        assertEquals(listOf(stream), videos.filter { it.isLive })
        // The first paragraph of the description, not the links and boilerplate after it.
        assertTrue(stream.summary!!.startsWith("Live views from the International Space Station"))
        assertFalse(stream.summary!!.contains("go.nasa.gov"))
    }

    @Test
    fun `malformed items are dropped and the good one survives`() {
        val items = items(NewsFixtures.MALFORMED_ITEMS, NewsFeed.SCIENCE)

        val good = items.single()
        assertEquals("NASA’s Roman Space Telescope Enters Final Commissioning Phase", good.title)
        assertEquals("The mission has entered the science commissioning phase …", good.summary)
        assertNull(good.imageUrl)
    }

    @Test
    fun `a credit is the caption right after the picture, without its label`() {
        val html = """
            <figure><img src="https://www.nasa.gov/a.jpg" alt=""></figure>
            <figcaption><div class="hds-credits">Credit: NASA/Josh Valcarcel</div></figcaption>
            <img src="https://www.nasa.gov/b.jpg"><div class="hds-credits">Somebody else</div>
        """

        val a = "https://www.nasa.gov/a.jpg"
        assertEquals(FeedImage(a, a, "NASA/Josh Valcarcel"), articleImage(html))
        val placeholders = """<img src="data:image/gif;base64,R0lGOD"><img src="https://x.org/i.svg">"""
        assertEquals(
            "https://www.nasa.gov/b.jpg",
            articleImage("""$placeholders<img src="https://www.nasa.gov/b.jpg">""")?.fullUrl,
        )
    }

    @Test
    fun `a video without a link is opened by its id`() {
        val entry = FeedEntryDto(title = "Spacewalk", videoId = "abc123", published = "2026-10-20T16:54:06+00:00")

        assertEquals("https://www.youtube.com/watch?v=abc123", entry.toNewsItemOrNull(NewsFeed.YOUTUBE)?.url)
    }

    @Test
    fun `feed dates parse in both formats and garbage does not`() {
        assertEquals(Instant.parse("2026-10-22T16:20:37Z"), parseFeedDate("Thu, 22 Oct 2026 16:20:37 +0000"))
        assertEquals(Instant.parse("2026-10-21T15:07:00Z"), parseFeedDate(" Wed, 21 Oct 2026 15:07 GMT "))
        assertEquals(Instant.parse("2026-10-22T14:29:44Z"), parseFeedDate("2026-10-22T14:29:44+00:00"))
        assertNull(parseFeedDate("sometime last week"))
    }

    @Test
    fun `html entities and tags become plain text`() {
        assertEquals(
            "NASA’s “Roman” & co. – 3° …",
            Html.toPlainText("<p>NASA&#8217;s &ldquo;Roman&rdquo; &amp; co.</p> &ndash; 3&deg; &#x2026;"),
        )
        assertEquals("&unknown; stays", Html.decodeEntities("&unknown; stays"))
    }
}
