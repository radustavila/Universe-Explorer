package com.radustavila.nasaexplorer.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApodTest {

    @Test
    fun `image entries preview with the standard url and open in hd`() {
        val apod = apod(mediaType = MediaType.IMAGE, url = URL, hdUrl = HD_URL, thumbnailUrl = null)

        assertEquals(URL, apod.previewImageUrl)
        assertEquals(HD_URL, apod.fullImageUrl)
        assertFalse(apod.isPlayable)
    }

    @Test
    fun `image entries fall back to the standard url when no hd url exists`() {
        val apod = apod(mediaType = MediaType.IMAGE, url = URL, hdUrl = null, thumbnailUrl = null)

        assertEquals(URL, apod.fullImageUrl)
    }

    @Test
    fun `video entries render through the thumbnail and stay playable`() {
        val apod = apod(
            mediaType = MediaType.VIDEO,
            url = "https://youtube.com/embed/x",
            hdUrl = null,
            thumbnailUrl = THUMBNAIL_URL,
        )

        assertEquals(THUMBNAIL_URL, apod.previewImageUrl)
        assertEquals(THUMBNAIL_URL, apod.fullImageUrl)
        assertTrue(apod.isPlayable)
    }

    @Test
    fun `a youtube video without a thumbnail falls back to the poster frame youtube serves`() {
        val embeds = listOf(
            "https://www.youtube.com/embed/$YOUTUBE_ID?rel=0",
            "https://youtube.com/embed/$YOUTUBE_ID",
            "https://www.youtube-nocookie.com/embed/$YOUTUBE_ID",
            "https://youtu.be/$YOUTUBE_ID",
            "https://www.youtube.com/watch?v=$YOUTUBE_ID&feature=share",
        )

        embeds.forEach { url ->
            val apod = apod(
                mediaType = MediaType.VIDEO,
                url = url,
                hdUrl = null,
                thumbnailUrl = null,
            )

            assertEquals(
                "https://img.youtube.com/vi/$YOUTUBE_ID/hqdefault.jpg",
                apod.previewImageUrl,
            )
        }
    }

    @Test
    fun `a streamable video without a thumbnail is previewed through its own first frame`() {
        val url = "https://apod.nasa.gov/apod/video/clip.mp4"
        val apod = apod(mediaType = MediaType.VIDEO, url = url, hdUrl = null, thumbnailUrl = null)

        assertEquals(url, apod.previewImageUrl)
    }

    @Test
    fun `a thumbnail NASA publishes is preferred over any derived preview`() {
        val apod = apod(
            mediaType = MediaType.VIDEO,
            url = "https://www.youtube.com/embed/$YOUTUBE_ID",
            hdUrl = null,
            thumbnailUrl = THUMBNAIL_URL,
        )

        assertEquals(THUMBNAIL_URL, apod.previewImageUrl)
    }

    @Test
    fun `video entries with no thumbnail and no recoverable frame have nothing to render`() {
        val unpreviewable = listOf(
            // Vimeo exposes no poster frame at a predictable address.
            "https://player.vimeo.com/video/123456",
            // Not a video id, so nothing can be derived from it.
            "https://www.youtube.com/embed/",
            // Playable, but a frame extractor cannot follow a manifest to its segments.
            "https://apod.nasa.gov/apod/video/clip.m3u8",
            "https://apod.nasa.gov/apod/ap260910.html",
        )

        unpreviewable.forEach { url ->
            val apod = apod(
                mediaType = MediaType.VIDEO,
                url = url,
                hdUrl = null,
                thumbnailUrl = null,
            )

            assertEquals(null, apod.previewImageUrl)
        }
    }

    @Test
    fun `a video served as a file or a manifest can be streamed in-app`() {
        val streamable = listOf(
            "https://apod.nasa.gov/apod/video/clip.mp4",
            "https://apod.nasa.gov/apod/video/clip.webm",
            "https://apod.nasa.gov/apod/video/clip.MP4",
            "https://apod.nasa.gov/apod/video/clip.m3u8?token=abc",
            "https://apod.nasa.gov/apod/video/clip.mpd#t=10",
        )

        streamable.forEach { url ->
            val apod = apod(mediaType = MediaType.VIDEO, url = url, hdUrl = null, thumbnailUrl = null)

            assertEquals(url, apod.streamUrl)
        }
    }

    @Test
    fun `an embedded player page is not a stream`() {
        val embeds = listOf(
            "https://www.youtube.com/embed/abc123",
            "https://player.vimeo.com/video/123456",
            "https://apod.nasa.gov/apod/ap260910.html",
        )

        embeds.forEach { url ->
            val apod = apod(mediaType = MediaType.VIDEO, url = url, hdUrl = null, thumbnailUrl = null)

            assertEquals(null, apod.streamUrl)
        }
    }

    @Test
    fun `an image is never treated as a stream`() {
        val apod = apod(mediaType = MediaType.IMAGE, url = URL, hdUrl = HD_URL, thumbnailUrl = null)

        assertEquals(null, apod.streamUrl)
    }

    @Test
    fun `without a permalink the archive page url is derived from the publication date`() {
        val apod = apod(MediaType.IMAGE, url = URL, hdUrl = null, thumbnailUrl = null)

        assertEquals("https://apod.nasa.gov/apod/ap260910.html", apod.webPageUrl)
    }

    @Test
    fun `the web page is the permalink NASA published for the entry`() {
        val apod = apod(MediaType.IMAGE, url = URL, hdUrl = null, thumbnailUrl = null)
            .copy(permalink = PERMALINK)

        assertEquals(PERMALINK, apod.webPageUrl)
    }

    @Test
    fun `the picture is described by NASA's alt text, or by the title without one`() {
        val apod = apod(MediaType.IMAGE, url = URL, hdUrl = null, thumbnailUrl = null)

        assertEquals("Title", apod.imageDescription)
        assertEquals("Pillars of gas", apod.copy(altText = "Pillars of gas").imageDescription)
    }

    private fun apod(
        mediaType: MediaType,
        url: String,
        hdUrl: String?,
        thumbnailUrl: String?,
    ) = Apod(
        date = LocalDate.of(2026, 9, 10),
        title = "Title",
        explanation = "Explanation",
        mediaType = mediaType,
        url = url,
        hdUrl = hdUrl,
        thumbnailUrl = thumbnailUrl,
    )

    private companion object {
        const val URL = "https://apod.nasa.gov/image.jpg"
        const val HD_URL = "https://apod.nasa.gov/image_hd.jpg"
        const val THUMBNAIL_URL = "https://img.youtube.com/vi/x/0.jpg"
        const val YOUTUBE_ID = "dQw4w9WgXcQ"
        const val PERMALINK = "https://science.nasa.gov/image-article/apod-2026-september-10-ngc-1300/"
    }
}
