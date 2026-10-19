package com.radustavila.nasaexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExoplanetUnitsTest {

    @Test
    fun `parsecs convert to light-years`() {
        assertEquals(3.2616, ExoplanetUnits.parsecsToLightYears(1.0), TOLERANCE)
        // Proxima Centauri: 1.30119 pc is 4.244 light-years.
        assertEquals(4.244, ExoplanetUnits.parsecsToLightYears(1.30119), TOLERANCE)
        assertEquals(0.0, ExoplanetUnits.parsecsToLightYears(0.0), TOLERANCE)
    }

    @Test
    fun `earth radii convert to jupiter radii`() {
        assertEquals(1.0, ExoplanetUnits.earthRadiiToJupiterRadii(11.209), TOLERANCE)
        assertEquals(0.0892, ExoplanetUnits.earthRadiiToJupiterRadii(1.0), TOLERANCE)
    }

    @Test
    fun `earth masses convert to jupiter masses`() {
        assertEquals(1.0, ExoplanetUnits.earthMassesToJupiterMasses(317.83), TOLERANCE)
        assertEquals(2.5, ExoplanetUnits.earthMassesToJupiterMasses(794.575), TOLERANCE)
    }

    @Test
    fun `kelvin converts to celsius`() {
        assertEquals(0.0, ExoplanetUnits.kelvinToCelsius(273.15), TOLERANCE)
        assertEquals(120.85, ExoplanetUnits.kelvinToCelsius(394.0), TOLERANCE)
    }

    @Test
    fun `planet derives its converted quantities and keeps gaps as null`() {
        val planet = Exoplanet(
            name = "TOI-789 c",
            hostName = "TOI-789",
            discoveryYear = 2026,
            radiusEarth = 1.15,
            massEarth = 3.0,
            distanceParsecs = 43.407,
        )

        assertEquals(141.58, planet.distanceLightYears!!, 0.01)
        assertEquals(0.1026, planet.radiusJupiter!!, TOLERANCE)
        assertEquals(0.00944, planet.massJupiter!!, TOLERANCE)

        val unmeasured = planet.copy(radiusEarth = null, massEarth = null, distanceParsecs = null)
        assertNull(unmeasured.distanceLightYears)
        assertNull(unmeasured.radiusJupiter)
        assertNull(unmeasured.massJupiter)
    }

    @Test
    fun `earth-sized range is inclusive`() {
        assertEquals(true, 0.8 in ExoplanetUnits.EARTH_SIZED_RADII)
        assertEquals(true, 1.25 in ExoplanetUnits.EARTH_SIZED_RADII)
        assertEquals(false, 1.26 in ExoplanetUnits.EARTH_SIZED_RADII)
    }

    private companion object {
        const val TOLERANCE = 0.001
    }
}
