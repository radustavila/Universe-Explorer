package com.radustavila.nasaexplorer.data.nasa.mapper

import com.radustavila.nasaexplorer.core.database.entity.ApodEntity
import com.radustavila.nasaexplorer.core.model.ApodLink
import com.radustavila.nasaexplorer.core.model.MediaType
import com.radustavila.nasaexplorer.data.nasa.remote.dto.ApodDto
import com.radustavila.nasaexplorer.data.nasa.remote.dto.ExplanationLinkDto
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApodMappersTest {

    @Test
    fun `maps a complete image payload`() {
        val entity = imageDto().toEntityOrNull(CACHED_AT)

        assertEquals(
            ApodEntity(
                date = "2026-09-10",
                title = "Spiral Galaxy NGC 1300",
                explanation = "A barred spiral galaxy.",
                mediaType = "image",
                url = "https://apod.nasa.gov/apod/image/ngc1300.jpg",
                hdUrl = "https://apod.nasa.gov/apod/image/ngc1300_hd.jpg",
                thumbnailUrl = null,
                copyright = "Jane Doe",
                isFavorite = false,
                cachedAtEpochMillis = CACHED_AT.toEpochMilli(),
                lastSyncedAtEpochMillis = CACHED_AT.toEpochMilli(),
            ),
            entity,
        )
    }

    @Test
    fun `keeps the video thumbnail so a video entry can still be rendered`() {
        val entity = ApodDto(
            date = "2026-09-09",
            title = "Orion",
            explanation = "Flight through the nebula.",
            mediaType = "video",
            url = "https://www.youtube.com/embed/abc",
            thumbnailUrl = "https://img.youtube.com/vi/abc/0.jpg",
        ).toEntityOrNull(CACHED_AT)

        assertEquals("video", entity?.mediaType)
        assertEquals("https://img.youtube.com/vi/abc/0.jpg", entity?.thumbnailUrl)
        assertEquals(MediaType.VIDEO, entity?.let { MediaType.fromApiValue(it.mediaType) })
    }

    @Test
    fun `unknown media types degrade to other instead of failing`() {
        val entity = imageDto().copy(mediaType = "interactive").toEntityOrNull(CACHED_AT)

        assertEquals("other", entity?.mediaType)
    }

    @Test
    fun `entries without a usable date are dropped`() {
        assertNull(imageDto().copy(date = null).toEntityOrNull(CACHED_AT))
        assertNull(imageDto().copy(date = "not-a-date").toEntityOrNull(CACHED_AT))
    }

    @Test
    fun `an empty thumbnail url is treated as absent, not as a broken link`() {
        // NASA publishes some videos with "thumbnail_url": "" rather than omitting the field.
        val entity = ApodDto(
            date = "2026-09-09",
            title = "Witness XZ Andromedae Wink",
            explanation = "A native video.",
            mediaType = "video",
            url = "https://apod.nasa.gov/apod/image/2609/xz_and.mp4",
            thumbnailUrl = "",
        ).toEntityOrNull(CACHED_AT)

        assertNull(entity?.thumbnailUrl)
        assertEquals("https://apod.nasa.gov/apod/image/2609/xz_and.mp4", entity?.url)
    }

    @Test
    fun `entries without any media url are dropped`() {
        assertNull(imageDto().copy(url = null, hdUrl = null, thumbnailUrl = null).toEntityOrNull(CACHED_AT))
        assertNull(imageDto().copy(url = "", hdUrl = "", thumbnailUrl = " ").toEntityOrNull(CACHED_AT))
    }

    @Test
    fun `blank titles and copyright are normalised`() {
        val entity = imageDto().copy(title = "  ", copyright = "\n Jane Doe \n").toEntityOrNull(CACHED_AT)

        assertEquals("Untitled", entity?.title)
        assertEquals("Jane Doe", entity?.copyright)
    }

    @Test
    fun `multi-line credits are collapsed into a single line`() {
        val entity = imageDto()
            .copy(copyright = "Alessandro Merga\n Text:\nCecilia Chirenti\n(NASA)")
            .toEntityOrNull(CACHED_AT)

        assertEquals("Alessandro Merga Text: Cecilia Chirenti (NASA)", entity?.copyright)
    }

    @Test
    fun `a window with repeated dates collapses to one entity per date`() {
        val entities = listOf(
            imageDto().copy(title = "First"),
            imageDto().copy(title = "Duplicate of the same date"),
            imageDto().copy(date = "2026-09-09"),
            imageDto().copy(date = "broken"),
        ).toEntities(CACHED_AT)

        assertEquals(2, entities.size)
        assertEquals(
            "Duplicate of the same date",
            entities.first { it.date == "2026-09-10" }.title,
        )
    }

    @Test
    fun `entity maps back to the domain model`() {
        val apod = ApodEntity(
            date = "2026-09-10",
            title = "Title",
            explanation = "Explanation",
            mediaType = "image",
            url = "https://apod.nasa.gov/image.jpg",
            hdUrl = null,
            thumbnailUrl = null,
            copyright = null,
            // Legacy column: favourites live in their own table since v4.
            isFavorite = true,
            cachedAtEpochMillis = CACHED_AT.toEpochMilli(),
            lastSyncedAtEpochMillis = SYNCED_AT.toEpochMilli(),
        ).toDomain()

        assertEquals(LocalDate.of(2026, 9, 10), apod.date)
        assertEquals(MediaType.IMAGE, apod.mediaType)
        assertEquals(false, apod.isFavorite)
        assertEquals(CACHED_AT, apod.cachedAt)
        assertEquals(SYNCED_AT, apod.lastSyncedAt)
    }

    @Test
    fun `permalink, alt text and explanation links survive the round trip through the cache`() {
        val apod = imageDto().copy(
            explanation = "See the Pleiades and Wikipedia.",
            permalink = "https://science.nasa.gov/image-article/apod-2026-september-10-ngc-1300/",
            alt = "A barred spiral galaxy seen face on.",
            explanationLinks = listOf(
                ExplanationLinkDto(start = 4, end = 16, url = "https://apod.nasa.gov/apod/ap950620.html"),
                ExplanationLinkDto(start = 21, end = 30, url = "https://en.wikipedia.org/"),
            ),
        ).toEntityOrNull(CACHED_AT)?.toDomain()

        requireNotNull(apod)
        assertEquals(
            "https://science.nasa.gov/image-article/apod-2026-september-10-ngc-1300/",
            apod.webPageUrl,
        )
        assertEquals("A barred spiral galaxy seen face on.", apod.imageDescription)
        assertEquals(
            listOf(
                ApodLink(start = 4, end = 16, url = "https://apod.nasa.gov/apod/ap950620.html"),
                ApodLink(start = 21, end = 30, url = "https://en.wikipedia.org/"),
            ),
            apod.explanationLinks,
        )
        assertEquals(LocalDate.of(1995, 6, 20), apod.explanationLinks.first().apodDate)
    }

    @Test
    fun `links outside the explanation are dropped and an entry without links stores none`() {
        val entity = imageDto().copy(
            explanationLinks = listOf(ExplanationLinkDto(start = 10, end = 400, url = "https://x.org/")),
        ).toEntityOrNull(CACHED_AT)

        assertNull(entity?.explanationLinks)
        assertTrue(entity?.toDomain()?.explanationLinks.orEmpty().isEmpty())
    }

    @Test
    fun `an unreadable links column degrades to no links`() {
        val apod = requireNotNull(imageDto().toEntityOrNull(CACHED_AT))
            .copy(explanationLinks = "{not json")
            .toDomain()

        assertTrue(apod.explanationLinks.isEmpty())
        assertEquals("A barred spiral galaxy.", apod.explanation)
    }

    private fun imageDto() = ApodDto(
        date = "2026-09-10",
        title = "Spiral Galaxy NGC 1300",
        explanation = "A barred spiral galaxy.",
        mediaType = "image",
        url = "https://apod.nasa.gov/apod/image/ngc1300.jpg",
        hdUrl = "https://apod.nasa.gov/apod/image/ngc1300_hd.jpg",
        copyright = "Jane Doe",
    )

    private companion object {
        val CACHED_AT: Instant = Instant.parse("2026-09-10T12:00:00Z")
        val SYNCED_AT: Instant = Instant.parse("2026-09-12T08:00:00Z")
    }
}
