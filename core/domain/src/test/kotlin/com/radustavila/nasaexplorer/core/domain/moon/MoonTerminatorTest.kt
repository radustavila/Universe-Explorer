package com.radustavila.nasaexplorer.core.domain.moon

import com.radustavila.nasaexplorer.core.model.SelenographicPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonTerminatorTest {

    @Test
    fun `full Moon night side is the far side, split at the antimeridian`() {
        val polygons = MoonTerminator.nightSide(SelenographicPoint(0.0, 0.0))

        assertEquals(2, polygons.size)
        val longitudes = polygons.flatten().map(SelenographicPoint::longitude)
        assertTrue(longitudes.all { it <= -90.0 + EPSILON || it >= 90.0 - EPSILON })
        assertEquals(180.0, longitudes.max(), EPSILON)
        assertEquals(-180.0, longitudes.min(), EPSILON)
    }

    @Test
    fun `new Moon night side is one band over the near side`() {
        val polygons = MoonTerminator.nightSide(SelenographicPoint(0.0, 180.0))

        assertEquals(1, polygons.size)
        val longitudes = polygons.single().map(SelenographicPoint::longitude)
        assertEquals(-90.0, longitudes.min(), EPSILON)
        assertEquals(90.0, longitudes.max(), EPSILON)
    }

    @Test
    fun `polygons stay within the Mercator latitude limit`() {
        val polygons = MoonTerminator.nightSide(SelenographicPoint(1.5, 40.0))

        val latitudes = polygons.flatten().map(SelenographicPoint::latitude)
        assertEquals(MoonTerminator.DEFAULT_MAX_LATITUDE, latitudes.max(), EPSILON)
        assertEquals(-MoonTerminator.DEFAULT_MAX_LATITUDE, latitudes.min(), EPSILON)
    }

    @Test
    fun `a tilted Sun keeps the lit pole lit and the far pole dark`() {
        val sun = SelenographicPoint(latitude = 1.5, longitude = 0.0)

        // Near the north pole the Sun never sets; near the south pole it never rises.
        assertFalse(MoonTerminator.isDark(SelenographicPoint(89.5, 180.0), sun))
        assertTrue(MoonTerminator.isDark(SelenographicPoint(-89.5, 0.0), sun))
        assertEquals(0.0, MoonTerminator.halfWidthDegrees(89.5, 1.5), EPSILON)
        assertEquals(180.0, MoonTerminator.halfWidthDegrees(-89.5, 1.5), EPSILON)
    }

    @Test
    fun `points more than 90 degrees from the Sun are dark`() {
        val sun = SelenographicPoint(0.0, 90.0)

        assertFalse(MoonTerminator.isDark(SelenographicPoint(0.0, 45.0), sun))
        assertFalse(MoonTerminator.isDark(SelenographicPoint(0.0, 179.0), sun))
        assertTrue(MoonTerminator.isDark(SelenographicPoint(0.0, -45.0), sun))
        assertTrue(MoonTerminator.isDark(SelenographicPoint(30.0, -91.0), sun))
    }

    private companion object {
        const val EPSILON = 1e-6
    }
}
