package com.radustavila.nasaexplorer.feature.earthevents

import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapMarker
import com.radustavila.nasaexplorer.core.testing.data.TestEarthquakes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EarthquakeOverlaysTest {

    @Test
    fun `bigger quakes are drawn first so smaller ones stay tappable`() {
        val small = TestEarthquakes.small()
        val big = TestEarthquakes.significant()

        val overlay = earthquakeOverlay(listOf(small, big))

        assertEquals(listOf(big.id, small.id), overlay.shapes.map { it.id })
        val (bigMarker, smallMarker) = overlay.shapes.map { it as MapMarker }
        assertTrue(bigMarker.radius > smallMarker.radius)
        assertTrue(isEarthquakeOverlay(overlay.id))
    }

    @Test
    fun `the selection overlay holds only the selected quake, larger`() {
        val quake = TestEarthquakes.earthquake()

        val selected = earthquakeSelectionOverlay(quake).shapes.single() as MapMarker

        assertTrue(selected.radius > (earthquakeOverlay(listOf(quake)).shapes.single() as MapMarker).radius)
        assertTrue(earthquakeSelectionOverlay(null).shapes.isEmpty())
    }

    @Test
    fun `a focus target sits south of the quake, less so the closer the zoom`() {
        val quake = GeoPoint(latitude = 38.7, longitude = 14.2)

        val far = quake.southBy(dp = 110.0, zoom = 3.0)
        val near = quake.southBy(dp = 110.0, zoom = 6.0)

        assertEquals(quake.longitude, far.longitude, 0.0)
        assertTrue(far.latitude < near.latitude && near.latitude < quake.latitude)
        assertEquals(-85.0, GeoPoint(-84.9, 0.0).southBy(dp = 500.0, zoom = 1.0).latitude, 0.0)
    }
}
