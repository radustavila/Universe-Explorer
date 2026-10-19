package com.radustavila.nasaexplorer.data.exoplanets.mapper

import com.radustavila.nasaexplorer.core.model.DiscoveryMethod
import com.radustavila.nasaexplorer.data.exoplanets.remote.dto.ExoplanetDto
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExoplanetMappersTest {

    private val dto = ExoplanetDto(
        name = "TOI-789 c",
        hostName = "TOI-789",
        discoveryYear = 2026,
        discoveryPublished = "2026-09",
        discoveryMethod = "tran",
        discoveryFacility = "Transiting Exoplanet Survey Satellite (TESS)",
        radiusEarth = 1.15,
        massEarth = 3.0,
        orbitalPeriodDays = 8.0432344,
        equilibriumTemperatureKelvin = 394.0,
        distanceParsecs = 43.407,
    )

    @Test
    fun `a complete row round-trips to the domain model`() {
        val planet = dto.toEntityOrNull(fetchedAtMillis = 42)!!.toDomain()

        assertEquals("TOI-789 c", planet.name)
        assertEquals("TOI-789", planet.hostName)
        assertEquals(2026, planet.discoveryYear)
        assertEquals(YearMonth.of(2026, 9), planet.discoveryPublished)
        assertEquals(DiscoveryMethod.TRANSIT, planet.discoveryMethod)
        assertEquals(1.15, planet.radiusEarth!!, 0.0)
        assertEquals(43.407, planet.distanceParsecs!!, 0.0)
    }

    @Test
    fun `rows that cannot identify a planet are dropped`() {
        assertNull(dto.copy(name = null).toEntityOrNull(0))
        assertNull(dto.copy(name = "  ").toEntityOrNull(0))
        assertNull(dto.copy(hostName = null).toEntityOrNull(0))
        assertNull(dto.copy(discoveryYear = null).toEntityOrNull(0))
    }

    @Test
    fun `non-physical measurements become unknown instead of being drawn`() {
        val entity = dto.copy(radiusEarth = 0.0, massEarth = -1.0, distanceParsecs = Double.NaN)
            .toEntityOrNull(0)!!

        assertNull(entity.radiusEarth)
        assertNull(entity.massEarth)
        assertNull(entity.distanceParsecs)
    }

    @Test
    fun `publication month tolerates full dates and garbage`() {
        assertEquals("2026-09", dto.copy(discoveryPublished = "2026-09-15").toEntityOrNull(0)!!.discoveryPublished)
        assertNull(dto.copy(discoveryPublished = "soon").toEntityOrNull(0)!!.discoveryPublished)
    }

    @Test
    fun `unknown methods are stored as other`() {
        assertEquals(
            DiscoveryMethod.OTHER,
            dto.copy(discoveryMethod = "new").toEntityOrNull(0)!!.toDomain().discoveryMethod,
        )
    }
}
