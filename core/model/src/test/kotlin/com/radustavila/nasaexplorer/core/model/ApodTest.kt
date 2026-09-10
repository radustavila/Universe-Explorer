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
    fun `video entries without a thumbnail have nothing to render`() {
        val apod = apod(
            mediaType = MediaType.VIDEO,
            url = "https://youtube.com/embed/x",
            hdUrl = null,
            thumbnailUrl = null,
        )

        assertEquals(null, apod.previewImageUrl)
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
    }
}
