package com.radustavila.nasaexplorer.core.skymap

import com.radustavila.nasaexplorer.core.model.DeepSkyCatalogue
import com.radustavila.nasaexplorer.core.model.DeepSkyObject
import com.radustavila.nasaexplorer.core.model.DeepSkyType
import com.radustavila.nasaexplorer.core.model.ObservingInstrument
import java.time.Month
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reads the bundled `deepsky.bin` itself, so a regenerated catalogue is checked too. */
class ResourceDeepSkyCatalogTest {

    private val objects = ResourceDeepSkyCatalog().read()

    private fun messier(number: Int) = objects.single { it.messier == number }

    private fun caldwell(number: Int) = objects.single { it.caldwell == number }

    /** Within a hundredth of a degree of OpenNGC's sexagesimal J2000 position. */
    private fun assertAt(item: DeepSkyObject, hours: Double, degrees: Double) {
        assertEquals(item.id, hours * 15.0, item.position.rightAscensionDegrees, 0.01)
        assertEquals(item.id, degrees, item.position.declinationDegrees, 0.01)
    }

    private fun hms(h: Int, m: Int, s: Double) = h + m / 60.0 + s / 3600.0

    private fun dms(sign: Int, d: Int, m: Int, s: Double) = sign * (d + m / 60.0 + s / 3600.0)

    @Test
    fun `all 110 Messier and 109 Caldwell objects are there, once each`() {
        assertEquals((1..DeepSkyCatalogue.MESSIER.size).toList(), objects.mapNotNull { it.messier }.sorted())
        assertEquals((1..DeepSkyCatalogue.CALDWELL.size).toList(), objects.mapNotNull { it.caldwell }.sorted())
        assertEquals(objects.size, objects.map { it.id }.toSet().size)
    }

    @Test
    fun `Messier objects have their positions and data`() {
        val andromeda = messier(31)
        assertEquals("NGC0224", andromeda.id)
        assertEquals("NGC 224", andromeda.designation)
        assertEquals("Andromeda Galaxy", andromeda.commonName)
        assertEquals(DeepSkyType.GALAXY, andromeda.type)
        assertEquals("And", andromeda.constellation)
        assertEquals(3.44f, andromeda.magnitude!!, 0.001f)
        assertEquals(177.8f, andromeda.majorAxisArcmin!!, 0.05f)
        assertEquals(69.7f, andromeda.minorAxisArcmin!!, 0.05f)
        assertEquals(35, andromeda.positionAngleDegrees)
        assertAt(andromeda, hms(0, 42, 44.35), dms(1, 41, 16, 8.6))
        assertAt(messier(1), hms(5, 34, 31.97), dms(1, 22, 0, 52.1))
        assertAt(messier(42), hms(5, 35, 16.48), dms(-1, 5, 23, 22.8))
        assertAt(messier(13), hms(16, 41, 41.63), dms(1, 36, 27, 40.7))
        assertAt(messier(51), hms(13, 29, 52.71), dms(1, 47, 11, 42.6))
        assertAt(messier(104), hms(12, 39, 59.43), dms(-1, 11, 37, 23.0))
        assertEquals(DeepSkyType.SUPERNOVA_REMNANT, messier(1).type)
        assertEquals(DeepSkyType.GLOBULAR_CLUSTER, messier(13).type)
    }

    @Test
    fun `objects from the addendum and the hand-made fixes are there`() {
        val pleiades = messier(45)
        assertEquals("Mel022", pleiades.id)
        assertEquals("Pleiades", pleiades.commonName)
        assertAt(pleiades, hms(3, 47, 28.6), dms(1, 24, 6, 19.0))
        assertEquals(DeepSkyType.STAR_GROUP, messier(40).type)
        assertEquals("", messier(40).designation)
        // OpenNGC calls M102 a duplicate of M101; the app takes the Spindle Galaxy.
        assertEquals("NGC5866", messier(102).id)
        assertAt(messier(102), hms(15, 6, 29.50), dms(1, 55, 45, 47.6))
    }

    @Test
    fun `Caldwell objects follow NASA's list`() {
        assertEquals("NGC0188", caldwell(1).id)
        assertEquals("NGC7023", caldwell(4).id)
        assertEquals("NGC6543", caldwell(6).id)
        assertEquals("C009", caldwell(9).id)
        assertEquals("NGC0147", caldwell(17).id)
        assertEquals("NGC6882", caldwell(37).id) // OpenNGC keeps NGC 6885 as a duplicate of it.
        assertEquals("NGC2239", caldwell(50).id) // Likewise NGC 2244.
        assertEquals("NGC3201", caldwell(79).id)
        assertEquals("NGC3195", caldwell(109).id)
        val omegaCentauri = caldwell(80)
        assertEquals("Omega Centauri", omegaCentauri.commonName)
        assertAt(omegaCentauri, hms(13, 26, 45.89), dms(-1, 47, 28, 36.7))
        assertAt(caldwell(106), hms(0, 24, 5.36), dms(-1, 72, 4, 53.2))
        val doubleCluster = caldwell(14)
        assertEquals(DeepSkyType.OPEN_CLUSTER, doubleCluster.type)
        assertAt(doubleCluster, hms(2, 20, 42.0), dms(1, 57, 8, 15.0))
        assertEquals(0.5f, caldwell(41).magnitude!!, 0.001f)
        assertEquals(ObservingInstrument.NAKED_EYE, caldwell(99).instrument)
    }

    @Test
    fun `the rest is brighter than magnitude 12, brightest first`() {
        val unlisted = objects.filter { it.messier == null && it.caldwell == null }
        assertTrue(unlisted.all { it.magnitude!! < 12f })
        val magnitudes = objects.mapNotNull { it.magnitude }
        assertTrue(magnitudes.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(objects.dropWhile { it.magnitude != null }.all { it.magnitude == null })
        assertTrue(objects.all { it.position.rightAscensionDegrees in 0.0..360.0 })
        assertTrue(objects.all { it.position.declinationDegrees in -90.0..90.0 })
        assertTrue(objects.none { it.constellation == "Se1" || it.constellation == "Se2" })
    }

    @Test
    fun `best month and instrument read the sky as observers do`() {
        assertEquals(Month.OCTOBER, messier(31).bestMonth)
        assertEquals(Month.JANUARY, messier(42).bestMonth)
        assertEquals(Month.JUNE, messier(13).bestMonth)
        assertEquals(ObservingInstrument.NAKED_EYE, messier(31).instrument)
        assertEquals(ObservingInstrument.BINOCULARS, messier(13).instrument)
        assertEquals(ObservingInstrument.SMALL_TELESCOPE, messier(51).instrument)
    }

    @Test
    fun `the catalogue is read once`() = runTest {
        val repository = ResourceDeepSkyCatalog()

        assertSame(repository.objects(), repository.objects())
    }
}
