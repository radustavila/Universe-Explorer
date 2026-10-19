package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.MoonPhase
import com.radustavila.nasaexplorer.core.model.Planet
import com.radustavila.nasaexplorer.core.model.PlanetTonight
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tonight above Bucharest and London on 2026-10-19, against Skyfield 1.55 with JPL's DE421
 * (`almanac.risings_and_settings`, `dark_twilight_day`, `moon_phases`). The roadmap asks for rise
 * and set times within two minutes of a reference; these agree to a few seconds.
 *
 * The Sun's and Moon's references are for the upper limb (`radius_degrees` 16' and the Moon's
 * semi-diameter) with 34' of refraction, the almanac convention timeanddate.com and the USNO use.
 * Skyfield's default, the centre of the disc, sets about 1.5 minutes earlier.
 */
class AstronomyEngineSkyCalculatorTest {

    private val calculator = AstronomyEngineSkyCalculator()
    private val noon = Instant.parse("2026-10-19T12:00:00Z")

    @Test
    fun `the night runs from this evening's sunset through twilight to tomorrow's sunrise`() {
        val night = calculator.skyTonight(ReferenceElements.BUCHAREST, noon).night

        assertClose("2026-10-19T15:24:57Z", night.sunset)
        assertClose("2026-10-19T15:54:24Z", night.civilDusk)
        assertClose("2026-10-19T16:28:08Z", night.nauticalDusk)
        assertClose("2026-10-19T17:01:44Z", night.astronomicalDusk)
        assertClose("2026-10-20T02:59:50Z", night.astronomicalDawn)
        assertClose("2026-10-20T03:33:29Z", night.nauticalDawn)
        assertClose("2026-10-20T04:07:18Z", night.civilDawn)
        assertClose("2026-10-20T04:36:50Z", night.sunrise)
    }

    @Test
    fun `after midnight it is still the same night`() {
        val night = calculator.skyTonight(ReferenceElements.BUCHAREST, Instant.parse("2026-10-20T01:00:00Z")).night

        assertClose("2026-10-19T15:24:57Z", night.sunset)
        assertClose("2026-10-20T04:36:50Z", night.sunrise)
    }

    @Test
    fun `moon phase and rise and set match the reference in Bucharest`() {
        val moon = calculator.skyTonight(ReferenceElements.BUCHAREST, noon).moon

        // First quarter was on 2026-10-18 16:12 UTC.
        assertEquals(MoonPhase.FIRST_QUARTER, moon.phase)
        assertEquals(99.04, moon.phaseAngleDegrees, 0.1)
        assertEquals(0.580, moon.illuminatedFraction, 0.005)
        // Up since 12:25 UTC, so it is above the horizon at sunset.
        assertTrue(moon.isUpAtStart)
        assertClose("2026-10-19T21:53:37Z", moon.set)
        assertClose("2026-10-20T12:49:04Z", moon.rise)
        assertClose("2026-10-26T04:11:49Z", moon.nextFullMoon)
        assertClose("2026-11-09T07:02:07Z", moon.nextNewMoon)
    }

    @Test
    fun `moon rise and set match the reference in London`() {
        val moon = calculator.skyTonight(ReferenceElements.LONDON, noon).moon

        assertTrue(moon.isUpAtStart)
        assertClose("2026-10-19T23:18:32Z", moon.set)
        assertClose("2026-10-20T14:55:14Z", moon.rise)
    }

    @Test
    fun `planet rise and set times match the reference`() {
        val planets = calculator.skyTonight(ReferenceElements.BUCHAREST, noon).planets.associateBy { it.planet }

        val jupiter = planets.getValue(Planet.JUPITER)
        assertFalse(jupiter.isUpAtStart)
        assertClose("2026-10-19T23:00:32Z", jupiter.rise)
        assertClose("2026-10-20T13:03:16Z", jupiter.set)

        val saturn = planets.getValue(Planet.SATURN)
        assertTrue(saturn.isUpAtStart)
        assertClose("2026-10-20T03:11:39Z", saturn.set)

        val mars = planets.getValue(Planet.MARS)
        assertClose("2026-10-19T22:03:03Z", mars.rise)

        // Venus set at 15:00, before the Sun: it is a morning object.
        val venus = planets.getValue(Planet.VENUS)
        assertFalse(venus.isUpAtStart)
        assertClose("2026-10-20T05:30:25Z", venus.rise)
        assertClose("2026-10-20T14:56:05Z", venus.set)
    }

    @Test
    fun `best time is the highest point while the sky is dark`() {
        val sky = calculator.skyTonight(ReferenceElements.BUCHAREST, noon)
        val planets = sky.planets.associateBy { it.planet }

        val saturn = planets.getValue(Planet.SATURN)
        val jupiter = planets.getValue(Planet.JUPITER)
        assertTrue(saturn.isVisibleTonight)
        assertTrue(jupiter.isVisibleTonight)
        // Saturn crosses the meridian in the evening; Jupiter climbs until dawn.
        assertNotNull(saturn.bestAltitudeDegrees)
        assertTrue(saturn.bestTime!! in sky.night.civilDusk!!..sky.night.civilDawn!!)
        assertTrue(Duration.between(jupiter.bestTime, sky.night.civilDawn).abs() < Duration.ofMinutes(15))
        assertTrue(jupiter.bestAltitudeDegrees!! >= PlanetTonight.MIN_VISIBLE_ALTITUDE)
        // Visible planets are listed first.
        assertTrue(sky.planets.takeWhile { it.isVisibleTonight }.size == sky.planets.count { it.isVisibleTonight })
    }

    private fun assertClose(expected: String, actual: Instant?) {
        assertNotNull("expected $expected", actual)
        val error = Duration.between(Instant.parse(expected), actual).abs()
        assertTrue("$actual is $error from $expected", error <= Duration.ofMinutes(2))
    }
}
