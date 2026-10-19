package com.radustavila.nasaexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceWeatherTest {

    @Test
    fun `NOAA thirds round to the reported Kp and storm level`() {
        assertEquals(4, Kp.level(4.33))
        assertEquals(5, Kp.level(4.67))
        assertEquals(GeomagneticStorm.G1, GeomagneticStorm.forKp(4.67))
        assertNull(GeomagneticStorm.forKp(4.33))
        assertEquals(GeomagneticStorm.G3, GeomagneticStorm.forKp(7.0))
        assertEquals(GeomagneticStorm.G5, GeomagneticStorm.forKp(9.0))
    }

    @Test
    fun `geomagnetic latitude is higher in North America than in Europe at the same latitude`() {
        val winnipeg = AuroraVisibility.geomagneticLatitude(latitude = 49.9, longitude = -97.1)
        val munich = AuroraVisibility.geomagneticLatitude(latitude = 48.1, longitude = 11.6)

        assertEquals(58.0, winnipeg, 1.5)
        assertEquals(48.0, munich, 1.5)
    }

    @Test
    fun `minimum Kp falls with distance from the geomagnetic pole`() {
        assertEquals(0, AuroraVisibility.minimumKp(latitude = 69.6, longitude = 18.9)) // Tromsø
        assertEquals(5, AuroraVisibility.minimumKp(latitude = 59.3, longitude = 18.1)) // Stockholm
        assertEquals(7, AuroraVisibility.minimumKp(latitude = 51.5, longitude = -0.1)) // London
        assertNull(AuroraVisibility.minimumKp(latitude = 41.9, longitude = 12.5)) // Rome
        // Southern hemisphere: Hobart sees the aurora australis in strong storms.
        assertEquals(9, AuroraVisibility.minimumKp(latitude = -42.9, longitude = 147.3))
    }

    @Test
    fun `alert settings need a place before they have a threshold`() {
        assertNull(AuroraAlertSettings(enabled = true).minimumKp)
        assertEquals(0, AuroraAlertSettings(enabled = true, latitude = 70.0, longitude = 20.0).minimumKp)
    }

    @Test
    fun `size is estimated from absolute magnitude when JPL has no diameter`() {
        val size = SizeEstimate.fromAbsoluteMagnitude(24.49)

        assertTrue(size.isRange)
        assertEquals(33.6, size.minMeters, 1.0)
        assertEquals(75.2, size.maxMeters, 1.0)
    }

    @Test
    fun `a close approach converts au to lunar distances`() {
        val approach = CloseApproach(
            designation = "2026 UK",
            name = "(2026 UK)",
            time = java.time.Instant.parse("2026-10-19T04:38:00Z"),
            distanceAu = 0.0194736855784829,
            relativeVelocityKmPerSecond = 15.1,
            absoluteMagnitude = 25.0,
            diameterKm = null,
        )

        assertEquals(7.58, approach.distanceLunar, 0.01)
    }
}
