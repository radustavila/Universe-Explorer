package com.radustavila.nasaexplorer.core.skymap

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reads the bundled files themselves, so a regenerated catalogue is checked too. */
class ResourceSkyCatalogTest {

    private val catalog = runBlocking { ResourceSkyCatalog(ResourceDeepSkyCatalog()).read() }

    @Test
    fun `every naked-eye star is there, brightest first`() {
        assertEquals(8920, catalog.stars.size)
        assertTrue(catalog.stars.all { it.magnitude <= 6.5f })
        assertTrue(catalog.stars.zipWithNext().all { (a, b) -> a.magnitude <= b.magnitude })
        val sirius = catalog.stars.first()
        assertEquals("Sirius", sirius.name)
        assertEquals(101.287, sirius.position.rightAscensionDegrees, 0.01)
        assertEquals(-16.716, sirius.position.declinationDegrees, 0.01)
        assertEquals(-1.44f, sirius.magnitude, 0.01f)
    }

    @Test
    fun `named stars keep their names and colours`() {
        val named = catalog.stars.filter { it.name != null }
        assertEquals(440, named.size)
        val betelgeuse = named.single { it.name == "Betelgeuse" }
        assertEquals(1.5f, betelgeuse.colourIndex!!, 0.01f)
        val polaris = named.single { it.name == "Polaris" }
        assertEquals(89.26, polaris.position.declinationDegrees, 0.01)
        assertTrue(catalog.stars.all { it.position.rightAscensionDegrees in 0.0..360.0 })
        assertTrue(catalog.stars.all { it.position.declinationDegrees in -90.0..90.0 })
    }

    @Test
    fun `all 88 constellations have figures, Serpens in two parts`() {
        assertEquals(89, catalog.constellations.size)
        assertEquals(88, catalog.constellations.map { it.id }.toSet().size)
        assertEquals(2, catalog.constellations.count { it.id == "Ser" })
        val orion = catalog.constellations.single { it.id == "Ori" }
        assertEquals("Orion", orion.name)
        assertTrue(orion.lines.isNotEmpty())
        val points = catalog.constellations.flatMap { it.lines.flatten() }
        assertTrue(points.all { it.rightAscensionDegrees in 0.0..360.0 && it.declinationDegrees in -90.0..90.0 })
        assertEquals("Boötes", catalog.constellations.single { it.id == "Boo" }.name)
    }

    @Test
    fun `the deep-sky catalogue comes with the stars`() {
        assertEquals(1775, catalog.deepSky.size)
    }

    @Test
    fun `constellation names join Serpens' two parts`() {
        val names = catalog.constellationNames()
        assertEquals(88, names.size)
        assertEquals("Serpens", names["Ser"])
        assertEquals("Ursa Major", names["UMa"])
        assertTrue(catalog.deepSky.all { it.constellation in names })
    }

    @Test
    fun `the catalogue is read once`() = runTest {
        val repository = ResourceSkyCatalog(ResourceDeepSkyCatalog())

        assertSame(repository.catalog(), repository.catalog())
    }
}
