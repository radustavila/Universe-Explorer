package com.radustavila.nasaexplorer.feature.skymap

import com.radustavila.nasaexplorer.core.model.ConstellationFigure
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import com.radustavila.nasaexplorer.core.model.SatelliteSkyPosition
import com.radustavila.nasaexplorer.core.model.SkyMapBodies
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.testing.data.TestSkyMap
import com.radustavila.nasaexplorer.feature.skymap.render.SkyNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkySearchTest {

    private val bootes = ConstellationFigure("Boo", "Boötes", EquatorialCoordinate(215.0, 30.0), 1, emptyList())
    private val catalog = TestSkyMap.catalog().let { it.copy(constellations = it.constellations + bootes) }
    private val names = SkyNames(
        bodies = SolarSystemBody.entries.associateWith { it.name.lowercase().replaceFirstChar(Char::uppercase) },
        satellites = mapOf(25544 to "ISS"),
        cardinals = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW"),
        galacticCentre = "Galactic centre",
        milkyWayCore = "Milky Way core",
    )
    private val bodies = SkyMapBodies(
        solarSystem = emptyList(),
        satellites = listOf(SatelliteSkyPosition(25544, "ISS (ZARYA)", HorizontalCoordinate(10.0, 10.0))),
    )

    @Test
    fun `stars, planets and constellations are found by any part of their name`() {
        val sirius = SkyTarget.StarTarget(catalog.stars.indexOf(TestSkyMap.SIRIUS))
        assertEquals(listOf(sirius), search("siri").map { it.target })
        assertEquals(listOf(SkyTarget.Body(SolarSystemBody.SATURN)), search("SAT").map { it.target })
        assertEquals(listOf(SkyTarget.Constellation(0)), search("orion").map { it.target })
    }

    @Test
    fun `the galactic centre is found by either of its names, once`() {
        assertEquals(listOf(SkyTarget.GalacticCentre), search("milky").map { it.target })
        assertEquals(listOf(SkyTarget.GalacticCentre), search("galactic").map { it.target })
        // "c" is in both names; the centre is listed once.
        val all = searchSky("c", catalog, bodies, names, limit = Int.MAX_VALUE)
        assertEquals(1, all.count { it.target == SkyTarget.GalacticCentre })
    }

    @Test
    fun `names that start with the query come first`() {
        // Mercury and Saturn contain "ur", Uranus starts with it.
        assertEquals(listOf("Uranus", "Mercury", "Saturn"), search("ur").map { it.name })
    }

    @Test
    fun `accents and abbreviations match`() {
        assertEquals("Boötes", search("bootes").single().name)
        assertEquals("Boötes", search("boo").single().name)
        assertEquals("Orion", search("Ori").first().name)
    }

    @Test
    fun `the ISS is found only while it is on the map`() {
        assertEquals(listOf(SkyTarget.Satellite(25544)), search("iss").map { it.target })
        assertTrue(searchSky("iss", catalog, null, names).isEmpty())
    }

    @Test
    fun `comets on the map are found by their full name`() {
        val withComet = bodies.copy(comets = listOf(SkyMapFixture.comet))

        val found = searchSky("2026 b1", catalog, withComet, names)

        assertEquals(listOf(SkySearchResult(SkyTarget.Comet("2026 B1"), "C/2026 B1 (Lemmon)")), found)
        assertTrue(search("lemmon").isEmpty())
    }

    @Test
    fun `blank queries find nothing`() {
        assertTrue(search("  ").isEmpty())
    }

    private fun search(query: String) = searchSky(query, catalog, bodies, names)
}
