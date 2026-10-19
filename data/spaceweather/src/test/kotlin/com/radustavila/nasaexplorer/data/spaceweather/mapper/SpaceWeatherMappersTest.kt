package com.radustavila.nasaexplorer.data.spaceweather.mapper

import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.CmeAnalysisDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.CmeDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.EnlilRunDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.KpForecastDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.OvationDto
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceWeatherMappersTest {

    @Test
    fun `reads the three UTC spellings NOAA and DONKI use`() {
        assertEquals(Instant.parse("2026-10-12T00:00:00Z"), parseUtc("2026-10-12T00:00:00"))
        assertEquals(Instant.parse("2026-10-19T14:08:00Z"), parseUtc("2026-10-19T14:08:00Z"))
        assertEquals(Instant.parse("2026-09-19T17:57:00Z"), parseUtc("2026-09-19T17:57Z"))
        assertNull(parseUtc("yesterday"))
        assertNull(parseUtc(null))
    }

    @Test
    fun `Kp rows need a time, a value in range and a known kind`() {
        assertNull(KpForecastDto(timeTag = "2026-10-12T00:00:00", kp = null, observed = "observed").toEntityOrNull())
        assertNull(KpForecastDto(timeTag = "2026-10-12T00:00:00", kp = 12.0, observed = "observed").toEntityOrNull())
        assertNull(KpForecastDto(timeTag = "2026-10-12T00:00:00", kp = 3.0, observed = "guessed").toEntityOrNull())
        assertEquals("PREDICTED", KpForecastDto("2026-10-12T00:00:00", 3.0, "predicted").toEntityOrNull()?.kind)
    }

    @Test
    fun `the aurora grid follows the declared field order`() {
        val dto = OvationDto(
            observationTime = "2026-10-19T14:08:00Z",
            forecastTime = "2026-10-19T15:20:00Z",
            dataFormat = "[Latitude, Longitude, Aurora]",
            coordinates = listOf(listOf(65.0, 20.0, 42.0), listOf(91.0, 20.0, 99.0)),
        )

        val forecast = dto.toEntityOrNull(fetchedAt = Instant.EPOCH)!!.toDomain()!!

        assertEquals(42, forecast.probabilityAt(longitude = 20, latitude = 65))
        assertEquals(0, forecast.probabilityAt(longitude = 65, latitude = 20))
    }

    @Test
    fun `an aurora answer without points is unusable`() {
        assertNull(OvationDto(observationTime = "2026-10-19T14:08:00Z").toEntityOrNull(Instant.EPOCH))
    }

    @Test
    fun `a CME takes the most accurate analysis and its newest Earth-bound model run`() {
        val dto = CmeDto(
            id = "2026-10-01T10:00:00-CME-001",
            startTime = "2026-10-01T10:00Z",
            cmeAnalyses = listOf(
                CmeAnalysisDto(isMostAccurate = false, speed = 300.0),
                CmeAnalysisDto(
                    isMostAccurate = true,
                    speed = 1200.0,
                    enlilList = listOf(
                        EnlilRunDto("2026-10-01T12:00Z", "2026-10-03T08:00Z", isEarthGB = false),
                        EnlilRunDto("2026-10-01T18:00Z", "2026-10-03T06:00Z", isEarthGB = true),
                        EnlilRunDto("2026-10-01T20:00Z", null, isEarthGB = null),
                    ),
                ),
            ),
        )

        val cme = dto.toEntityOrNull()!!.toDomain()

        assertEquals(1200.0, cme.speedKmPerSecond!!, 0.0)
        assertEquals(Instant.parse("2026-10-03T06:00:00Z"), cme.earthArrival)
        assertTrue(cme.isEarthGlancingBlow)
    }
}
