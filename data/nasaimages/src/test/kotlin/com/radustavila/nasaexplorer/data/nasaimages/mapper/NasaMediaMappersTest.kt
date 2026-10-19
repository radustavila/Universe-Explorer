package com.radustavila.nasaexplorer.data.nasaimages.mapper

import com.radustavila.nasaexplorer.core.model.NasaMediaType
import com.radustavila.nasaexplorer.data.nasaimages.remote.NasaImagesFixtures
import com.radustavila.nasaexplorer.data.nasaimages.remote.assetFixture
import com.radustavila.nasaexplorer.data.nasaimages.remote.searchFixture
import com.radustavila.nasaexplorer.data.nasaimages.remote.dto.RenditionLinkDto
import com.radustavila.nasaexplorer.data.nasaimages.remote.dto.SearchItemDataDto
import com.radustavila.nasaexplorer.data.nasaimages.remote.dto.SearchItemDto
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsing and mapping of recorded library responses. */
class NasaMediaMappersTest {

    @Test
    fun `a recorded image search maps every item with its renditions`() {
        val page = searchFixture(NasaImagesFixtures.WEBB_GALAXIES).toPage(page = 1)

        assertEquals(110, page.totalHits)
        assertEquals(2, page.nextPage)
        assertEquals(5, page.items.size)

        val webb = page.items.first()
        assertEquals("PIA26702", webb.nasaId)
        assertEquals("Webb Data Reveals Dark Matter", webb.title)
        assertEquals(NasaMediaType.IMAGE, webb.mediaType)
        assertEquals(LocalDate.of(2026, 1, 26), webb.dateCreated)
        assertEquals("JPL", webb.center)
        assertEquals("NASA/STScI/J. DePasquale/A. Pagan", webb.creator)
        assertEquals(listOf("Webb", "Dark Matter", "James Webb Space Telescope"), webb.keywords)
        assertEquals("https://images-assets.nasa.gov/image/PIA26702/PIA26702~thumb.jpg", webb.thumbnailUrl)
        assertEquals("https://images-assets.nasa.gov/image/PIA26702/PIA26702~large.jpg", webb.imageUrl)
        assertEquals("https://images-assets.nasa.gov/image/PIA26702/PIA26702~orig.png", webb.originalUrl)
        assertTrue(webb.altText!!.startsWith("Containing nearly 800,000 galaxies"))
    }

    @Test
    fun `a huge original is downloadable but not used for zooming`() {
        // PIA26702's PNG original is 45 MB.
        val webb = searchFixture(NasaImagesFixtures.WEBB_GALAXIES).toPage(1).items.first()

        assertEquals(webb.imageUrl, webb.fullImageUrl)
    }

    @Test
    fun `a TIFF original is downloadable but zooms on the large rendition`() {
        val tiff = searchFixture(NasaImagesFixtures.WEBB_GALAXIES).toPage(1).items[1]

        assertEquals("https://images-assets.nasa.gov/image/1301190/1301190~orig.tif", tiff.originalUrl)
        assertEquals("https://images-assets.nasa.gov/image/1301190/1301190~large.jpg", tiff.fullImageUrl)
    }

    @Test
    fun `a small decodable original is the zoom image`() {
        val item = imageItem(
            links = listOf(
                RenditionLinkDto(href = "http://images-assets.nasa.gov/image/x/x~large.jpg", rel = "alternate"),
                RenditionLinkDto(
                    href = "http://images-assets.nasa.gov/image/x/x~orig.jpg",
                    rel = "canonical",
                    size = 4_000_000,
                ),
            ),
        ).toDomainOrNull()!!

        assertEquals("https://images-assets.nasa.gov/image/x/x~orig.jpg", item.fullImageUrl)
    }

    @Test
    fun `asset links are moved to https and their spaces encoded`() {
        val video = searchFixture(NasaImagesFixtures.VIDEOS).toPage(1).items.first()

        assertEquals("Webb Journey to Space Ep2", video.nasaId)
        assertEquals(NasaMediaType.VIDEO, video.mediaType)
        assertEquals(
            "$EP2_DIR/Webb%20Journey%20to%20Space%20Ep2~thumb.jpg",
            video.thumbnailUrl,
        )
        // Captions are not an image rendition.
        assertTrue(video.imageUrl!!.endsWith("~large.jpg"))
    }

    @Test
    fun `audio items carry no image at all`() {
        val audio = searchFixture(NasaImagesFixtures.AUDIO).toPage(1).items.first()

        assertEquals(NasaMediaType.AUDIO, audio.mediaType)
        assertNull(audio.thumbnailUrl)
        assertNull(audio.imageUrl)
        assertNull(audio.previewUrl)
    }

    @Test
    fun `items without keywords map with none, and html is stripped from descriptions`() {
        val page = searchFixture(NasaImagesFixtures.MIXED_PAGE_2).toPage(page = 2)
        assertEquals(emptyList<String>(), page.items.first().keywords)

        val audio = searchFixture(NasaImagesFixtures.AUDIO).toPage(1).items
        assertTrue(audio.none { "<" in it.description && ">" in it.description && "</" in it.description })
    }

    @Test
    fun `a middle page links to the next one`() {
        val page = searchFixture(NasaImagesFixtures.MIXED_PAGE_2).toPage(page = 2)

        assertEquals(959, page.totalHits)
        assertEquals(3, page.nextPage)
    }

    @Test
    fun `the last page and an empty search have no next page`() {
        assertNull(searchFixture(NasaImagesFixtures.BY_ID).toPage(1).nextPage)

        val empty = searchFixture(NasaImagesFixtures.EMPTY).toPage(1)
        assertEquals(0, empty.totalHits)
        assertEquals(emptyList<Any>(), empty.items)
        assertNull(empty.nextPage)
    }

    @Test
    fun `paging stops where the library stops serving results`() {
        // 10,000 hits at 30 a page: page 333 is the last one the library answers.
        assertNull(searchFixture(NasaImagesFixtures.MIXED_PAGE_2).toPage(page = 334).nextPage)
    }

    @Test
    fun `comma separated keywords from older uploads are split`() {
        val item = imageItem(keywords = listOf("Apollo 11, Moon,  apollo 11", "EVA")).toDomainOrNull()!!

        assertEquals(listOf("Apollo 11", "Moon", "EVA"), item.keywords)
    }

    @Test
    fun `items without an id, a known media type or a title are dropped`() {
        assertNull(imageItem(nasaId = " ").toDomainOrNull())
        assertNull(imageItem(mediaType = "model").toDomainOrNull())
        assertNull(imageItem(title = null).toDomainOrNull())
        assertNull(SearchItemDto(data = emptyList()).toDomainOrNull())
    }

    @Test
    fun `videos stream a phone sized rendition`() {
        val url = assetFixture(NasaImagesFixtures.VIDEO_ASSETS).playbackUrlFor(NasaMediaType.VIDEO)

        assertEquals(
            "$EP2_DIR/Webb%20Journey%20to%20Space%20Ep2~medium.mp4",
            url,
        )
    }

    @Test
    fun `recordings stream the 128k mp3`() {
        val url = assetFixture(NasaImagesFixtures.AUDIO_ASSETS).playbackUrlFor(NasaMediaType.AUDIO)

        assertEquals(
            "$EP433_DIR/Ep433_Callsign%20White%20Flight~128k.mp3",
            url,
        )
    }

    @Test
    fun `an image has nothing to play`() {
        assertNull(assetFixture(NasaImagesFixtures.IMAGE_ASSETS).playbackUrlFor(NasaMediaType.IMAGE))
        assertNull(assetFixture(NasaImagesFixtures.IMAGE_ASSETS).playbackUrlFor(NasaMediaType.VIDEO))
    }

    @Test
    fun `an entity round-trips to the same domain item`() {
        val items = searchFixture(NasaImagesFixtures.WEBB_GALAXIES).toPage(1).items +
            searchFixture(NasaImagesFixtures.VIDEOS).toPage(1).items

        items.forEach { media ->
            assertEquals(media, media.toEntity(Instant.EPOCH).toDomain())
        }
    }

    private fun imageItem(
        nasaId: String? = "x",
        title: String? = "Title",
        mediaType: String = "image",
        keywords: List<String> = emptyList(),
        links: List<RenditionLinkDto> = emptyList(),
    ) = SearchItemDto(
        data = listOf(SearchItemDataDto(nasaId = nasaId, title = title, mediaType = mediaType, keywords = keywords)),
        links = links,
    )

    private companion object {
        const val EP2_DIR = "https://images-assets.nasa.gov/video/Webb%20Journey%20to%20Space%20Ep2"
        const val EP433_DIR = "https://images-assets.nasa.gov/audio/Ep433_Callsign%20White%20Flight"
    }
}
