package com.radustavila.nasaexplorer.core.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpicPhotoRefTest {

    @Test
    fun `a photo's favourite id round-trips to where it can be found again`() {
        val image = EpicImage(
            identifier = "20261016002713",
            collection = EpicCollection.ENHANCED,
            capturedAt = Instant.parse("2026-10-16T00:22:24Z"),
            caption = "",
            centroid = GeoPoint(0.66, 163.7),
            thumbnailUrl = "",
            imageUrl = "",
        )

        val id = image.ref.favoriteId

        assertEquals("enhanced/2026-10-16/20261016002713", id)
        assertEquals(
            EpicPhotoRef(EpicCollection.ENHANCED, LocalDate.of(2026, 10, 16), "20261016002713"),
            EpicPhotoRef.fromFavoriteId(id),
        )
    }

    @Test
    fun `anything else is not a favourite id`() {
        listOf("epic_1b_20261016002713", "infrared/2026-10-16/1", "natural/16-10-2026/1", "natural/2026-10-16/", "")
            .forEach { assertNull(it, EpicPhotoRef.fromFavoriteId(it)) }
    }
}
