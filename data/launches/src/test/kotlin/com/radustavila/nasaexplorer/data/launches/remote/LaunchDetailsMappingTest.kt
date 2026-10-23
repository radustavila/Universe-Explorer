package com.radustavila.nasaexplorer.data.launches.remote

import com.radustavila.nasaexplorer.core.model.BoosterLanding
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.core.model.LaunchTimeline
import com.radustavila.nasaexplorer.core.model.NetPrecision
import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import com.radustavila.nasaexplorer.data.launches.local.LaunchEntity
import com.radustavila.nasaexplorer.data.launches.mapper.toDomain
import com.radustavila.nasaexplorer.data.launches.mapper.toEntityOrNull
import com.radustavila.nasaexplorer.data.launches.remote.dto.LaunchDto
import com.radustavila.nasaexplorer.data.launches.remote.dto.LaunchListDto
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The detail page's fields, mapped from recorded detailed responses (F27). */
class LaunchDetailsMappingTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private val upcoming = read(UPCOMING_DETAILED_FIXTURE, LaunchEntity.TIMELINE_UPCOMING)
    private val past = read(PREVIOUS_DETAILED_FIXTURE, LaunchEntity.TIMELINE_PAST)

    @Test
    fun `a Falcon 9 with a drone-ship landing carries mission, pad, booster, webcast and precision`() {
        val starlink = upcoming.byName("Starlink Group 15-16")

        assertEquals(LaunchStatus.GO, starlink.status)
        assertEquals(NetPrecision.MINUTE, starlink.netPrecision)
        assertEquals(LaunchTimeline.UPCOMING, starlink.timeline)
        assertEquals("Falcon 9", starlink.rocketName)
        assertEquals("Block 5", starlink.rocketVariant)
        assertEquals("Starlink Group 15-16", starlink.mission?.name)
        assertEquals("LEO", starlink.mission?.orbitAbbrev)
        assertTrue(starlink.mission?.description.orEmpty().isNotBlank())
        val pad = checkNotNull(starlink.padPosition)
        assertEquals(34.6, pad.latitude, 0.1)
        assertEquals(-120.6, pad.longitude, 0.1)
        val booster = starlink.boosters.single()
        val landing = checkNotNull(booster.landing)
        assertEquals(true, landing.attempt)
        assertNull(landing.success)
        assertEquals("OCISLY", landing.locationAbbrev)
        assertTrue(starlink.webcasts.isNotEmpty())
        assertEquals(starlink.webcasts.first().url, starlink.livestreamUrl)
        assertFalse(starlink.isCrewed)
    }

    @Test
    fun `a flown crewed Falcon 9 reports its crew, outcome and the booster's landing`() {
        val crew13 = past.byName("Crew-13")

        assertEquals(LaunchStatus.SUCCESS, crew13.status)
        assertEquals(LaunchTimeline.PAST, crew13.timeline)
        assertEquals(4, crew13.crewCount)
        val booster = crew13.boosters.single()
        assertEquals("B1101", booster.serial)
        assertEquals(3, booster.flightNumber)
        assertEquals(true, booster.landing?.success)
        assertEquals("LZ-40", booster.landing?.locationAbbrev)
        assertEquals("RTLS", booster.landing?.typeAbbrev)
        assertEquals(60, crew13.probability)
    }

    @Test
    fun `an expendable core is no landing attempt, beside two boosters landing at the Cape`() {
        val heavy = past.byName("NROL-97")

        val attempts = heavy.boosters.map { it.landing?.attempt }
        assertEquals(listOf(false, true, true), attempts)
        assertEquals(
            BoosterLanding(
                attempt = false,
                success = null,
                locationName = "Atlantic Ocean",
                locationAbbrev = "ATL",
                typeName = "Expended",
                typeAbbrev = "EXP",
            ),
            heavy.boosters.first().landing,
        )
        assertEquals("B1106", heavy.boosters.first().serial)
        assertEquals(Instant.parse("2026-10-02T03:53:00Z"), heavy.windowStart)
        assertEquals(Instant.parse("2026-10-02T04:42:00Z"), heavy.windowEnd)
        assertEquals(90, heavy.probability)
        // Launch Library 2's "Unknown" orbit is left out rather than shown.
        assertNull(heavy.mission?.orbitName)
    }

    @Test
    fun `a rocket without reusable stages has no boosters`() {
        val electron = past.byName("Owlright")

        assertTrue(electron.boosters.isEmpty())
        assertEquals("Electron", electron.rocketName)
        assertEquals(GeoCoordinate::class, electron.padPosition?.let { it::class })
    }

    @Test
    fun `a scrubbed launch is on hold with its new NET and no failure`() {
        val dto = json.decodeFromString<LaunchDto>(Fixtures.read(SCRUBBED_FIXTURE))

        val scrubbed = checkNotNull(dto.toEntityOrNull()).toDomain()

        assertEquals(LaunchStatus.HOLD, scrubbed.status)
        assertFalse(scrubbed.status.isFirm)
        assertEquals(Instant.parse("2026-10-25T05:07:00Z"), scrubbed.net)
        assertNull(scrubbed.failReason)
    }

    @Test
    fun `month-precision launches are not exact enough to count down to`() {
        val vulcan = upcoming.byName("Amazon Leo (LV-01)")

        assertEquals(LaunchStatus.TBD, vulcan.status)
        assertEquals(NetPrecision.DAY, vulcan.netPrecision)
        assertFalse(vulcan.netPrecision.isExact)
    }

    private fun read(fixture: String, timeline: String): List<Launch> =
        json.decodeFromString<LaunchListDto>(Fixtures.read(fixture)).results
            .map { dto -> checkNotNull(dto.toEntityOrNull(timeline)).toDomain() }

    private fun List<Launch>.byName(part: String): Launch = single { part in it.name }
}

/**
 * `GET /2.3.0/launches/upcoming/?mode=detailed&limit=30&hide_recent_previous=true`, recorded from
 * lldev.thespacedevs.com on 2026-10-23: four of its launches (a Falcon 9 with a drone-ship landing,
 * a Long March, a TBD Vulcan, a TBC Soyuz), trimmed of nested fields the app never reads.
 */
internal const val UPCOMING_DETAILED_FIXTURE = "launches/upcoming_detailed.json"

/**
 * `GET /2.3.0/launches/previous/?mode=detailed&limit=20`, recorded the same day: Crew-13, the
 * Falcon Heavy NROL-97 (expended core), an Electron and USSF-481, trimmed the same way.
 */
internal const val PREVIOUS_DETAILED_FIXTURE = "launches/previous_detailed.json"

/**
 * `GET /2.3.0/launches/{id}/` for the recorded Starlink Group 15-16 as it reads after a scrub: the
 * recording with its status set to On Hold and its NET and window moved a day later.
 */
internal const val SCRUBBED_FIXTURE = "launches/launch_scrubbed.json"
