package com.radustavila.nasaexplorer.data.launches.remote

import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.data.launches.LaunchFixtures
import com.radustavila.nasaexplorer.data.launches.mapper.toDomain
import com.radustavila.nasaexplorer.data.launches.mapper.toEntityOrNull
import com.radustavila.nasaexplorer.data.launches.remote.dto.LaunchDto
import com.radustavila.nasaexplorer.data.launches.remote.dto.LaunchListDto
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchDtoTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private val launches = json.decodeFromString<LaunchListDto>(LaunchFixtures.UPCOMING_DETAILED)
        .results
        .map { dto -> checkNotNull(dto.toEntityOrNull()).toDomain() }

    @Test
    fun `parses every launch of the recorded response`() {
        assertEquals(5, launches.size)
    }

    @Test
    fun `maps the fields the card shows`() {
        val first = launches.first()

        assertEquals("3b430f81-0ff1-4d85-8a1a-cef0de326198", first.id)
        assertEquals("Falcon 9 Block 5 | Bandwagon 5 (Dedicated Mid-Inclination Rideshare)", first.name)
        assertEquals(Instant.parse("2026-10-19T10:30:00Z"), first.net)
        assertEquals(LaunchStatus.GO, first.status)
        assertEquals("Falcon 9 Block 5", first.rocket)
        assertEquals("Space Launch Complex 40, Cape Canaveral SFS, FL, USA", first.pad)
        assertEquals(
            "https://spacelaunchnow.app/launch/falcon-9-block-5-bandwagon-5-dedicated-mid-inclina/",
            first.pageUrl,
        )
    }

    @Test
    fun `takes the first livestream and prefers the image thumbnail`() {
        val first = launches.first()

        assertEquals("https://x.com/i/broadcasts/1nxeLMddZlEJX", first.livestreamUrl)
        assertEquals(
            "https://thespacedevs-dev.nyc3.digitaloceanspaces.com/media/images/" +
                "falcon_9_liftof_image_thumbnail_20251102082912.jpeg",
            first.imageUrl,
        )
    }

    @Test
    fun `a launch without announced streams links to its page`() {
        val noStream = launches.first { it.name.startsWith("Ceres-1S") }

        assertNull(noStream.livestreamUrl)
        assertEquals(noStream.pageUrl, noStream.linkUrl)
    }

    @Test
    fun `maps the status by id`() {
        assertEquals(LaunchStatus.TBC, launches.last().status)
    }

    @Test
    fun `drops entries the card could not show`() {
        val valid = LaunchDto(id = "a", url = "https://ll/a/", name = "Rocket | Mission", net = "2026-10-19T10:30:00Z")

        assertNull(valid.copy(id = null).toEntityOrNull())
        assertNull(valid.copy(name = " ").toEntityOrNull())
        assertNull(valid.copy(url = null).toEntityOrNull())
        assertEquals("https://ll/a/", valid.toEntityOrNull()?.pageUrl)
        assertEquals(
            "https://spacelaunchnow.app/launch/rocket-mission/",
            valid.copy(slug = "rocket-mission", url = null).toEntityOrNull()?.pageUrl,
        )
        assertNull(valid.copy(net = "not a date").toEntityOrNull())
        assertEquals(LaunchStatus.UNKNOWN.id, valid.toEntityOrNull()?.statusId)
    }
}
