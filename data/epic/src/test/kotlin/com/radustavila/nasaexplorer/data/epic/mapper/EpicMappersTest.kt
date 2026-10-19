package com.radustavila.nasaexplorer.data.epic.mapper

import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.GeoPoint
import com.radustavila.nasaexplorer.data.epic.remote.dto.EpicCoordinatesDto
import com.radustavila.nasaexplorer.data.epic.remote.dto.EpicDateDto
import com.radustavila.nasaexplorer.data.epic.remote.dto.EpicImageDto
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpicMappersTest {

    @Test
    fun `derives archive URLs from the collection, the UTC capture day and the file name`() {
        val entity = requireNotNull(dto().toEntityOrNull(EpicCollection.NATURAL, CACHED_AT, BASE_URL))

        assertEquals("2026-10-16", entity.date)
        assertEquals(Instant.parse("2026-10-16T00:22:24Z").toEpochMilli(), entity.capturedAtEpochMillis)
        assertEquals(
            "https://epic.gsfc.nasa.gov/archive/natural/2026/10/16/thumbs/epic_1b_20261016002713.jpg",
            entity.thumbnailUrl,
        )
        assertEquals(
            "https://epic.gsfc.nasa.gov/archive/natural/2026/10/16/jpg/epic_1b_20261016002713.jpg",
            entity.imageUrl,
        )
    }

    @Test
    fun `enhanced photos live under their own archive tree`() {
        val entity = requireNotNull(
            dto(image = "epic_RGB_20261014001752", date = "2026-10-14 00:13:03")
                .toEntityOrNull(EpicCollection.ENHANCED, CACHED_AT, BASE_URL),
        )

        assertEquals("enhanced", entity.collection)
        assertEquals(
            "https://epic.gsfc.nasa.gov/archive/enhanced/2026/10/14/jpg/epic_RGB_20261014001752.jpg",
            entity.imageUrl,
        )
    }

    @Test
    fun `drops records that cannot be placed, shown or labelled`() {
        assertNull(dto(date = "16/10/2026").toEntityOrNull(EpicCollection.NATURAL, CACHED_AT, BASE_URL))
        assertNull(dto(image = " ").toEntityOrNull(EpicCollection.NATURAL, CACHED_AT, BASE_URL))
        assertNull(
            dto().copy(centroidCoordinates = EpicCoordinatesDto(lat = 1.0))
                .toEntityOrNull(EpicCollection.NATURAL, CACHED_AT, BASE_URL),
        )
    }

    @Test
    fun `round-trips to the domain model`() {
        val image = requireNotNull(dto().toEntityOrNull(EpicCollection.NATURAL, CACHED_AT, BASE_URL)).toDomain()

        assertEquals(EpicCollection.NATURAL, image.collection)
        assertEquals(LocalDate.of(2026, 10, 16), image.date)
        assertEquals(GeoPoint(latitude = 0.65918, longitude = 163.696289), image.centroid)
    }

    @Test
    fun `archive listings are de-duplicated, cleaned and sorted newest first`() {
        val dates = listOf("2026-10-13", "2026-10-14", "2026-10-13", "garbage", null)
            .map(::EpicDateDto)
            .toDates()

        assertEquals(listOf(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 13)), dates)
    }

    private fun dto(
        image: String = "epic_1b_20261016002713",
        date: String = "2026-10-16 00:22:24",
    ) = EpicImageDto(
        identifier = image.substringAfterLast('_'),
        caption = "This image was taken by NASA's EPIC camera onboard the NOAA DSCOVR spacecraft",
        image = image,
        date = date,
        centroidCoordinates = EpicCoordinatesDto(lat = 0.65918, lon = 163.696289),
    )

    private companion object {
        const val BASE_URL = "https://epic.gsfc.nasa.gov/"
        val CACHED_AT: Instant = Instant.parse("2026-10-19T10:00:00Z")
    }
}
