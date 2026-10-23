package com.radustavila.nasaexplorer.data.earthevents.mapper

import com.radustavila.nasaexplorer.core.model.EarthquakeAlertLevel
import com.radustavila.nasaexplorer.core.model.EarthquakeDepth
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.data.earthevents.remote.EarthquakeFeed
import com.radustavila.nasaexplorer.data.earthevents.remote.UsgsFixtures
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.EarthquakeFeatureDto
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.EarthquakeGeometryDto
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.EarthquakePropertiesDto
import com.radustavila.nasaexplorer.data.earthevents.remote.earthquakeFeedFixture
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarthquakeMappersTest {

    private val week = earthquakeFeedFixture(UsgsFixtures.WEEK).features

    @Test
    fun `the recorded week maps its four good quakes and drops the three broken ones`() {
        val quakes = week.mapNotNull { it.toDomainOrNull() }

        assertEquals(7, week.size)
        assertEquals(listOf("us6000u4bk", "us6000u4b3", "aka2026uzkyen", "us6000u43g"), quakes.map { it.id })
    }

    @Test
    fun `a quake keeps USGS's fields`() {
        val sicily = week.first().toDomainOrNull()!!

        assertEquals(5.7, sicily.magnitude, 0.0)
        assertEquals("61 km WNW of Leni, Italy", sicily.place)
        assertEquals(Instant.ofEpochMilli(1_792_750_756_729), sicily.time)
        assertEquals(GeoCoordinate(latitude = 38.7199, longitude = 14.152), sicily.position)
        assertEquals(10.0, sicily.depthKm, 0.0)
        assertEquals(EarthquakeDepth.SHALLOW, sicily.depth)
        assertEquals("https://earthquake.usgs.gov/earthquakes/eventpage/us6000u4bk", sicily.url)
        assertEquals(4, sicily.feltReports)
        assertEquals(EarthquakeAlertLevel.GREEN, sicily.alertLevel)
        assertFalse(sicily.tsunami)
        assertFalse(sicily.isSignificant)
    }

    @Test
    fun `depth classes, significance and the tsunami flag come through`() {
        val quakes = week.mapNotNull { it.toDomainOrNull() }.associateBy { it.id }
        assertEquals(EarthquakeDepth.DEEP, quakes.getValue("us6000u4b3").depth)
        assertEquals(EarthquakeDepth.INTERMEDIATE, quakes.getValue("aka2026uzkyen").depth)
        assertNull("nobody felt it", quakes.getValue("aka2026uzkyen").feltReports)
        assertTrue(quakes.getValue("us6000u43g").isSignificant)

        val vanuatu = earthquakeFeedFixture(UsgsFixtures.SIGNIFICANT).features[1].toDomainOrNull()!!
        assertTrue(vanuatu.tsunami)
    }

    @Test
    fun `blasts, missing times and missing ids are dropped, a missing depth defaults`() {
        val quake = EarthquakeFeatureDto(
            id = "x1",
            properties = EarthquakePropertiesDto(mag = 3.0, time = 1L, type = "earthquake"),
            geometry = EarthquakeGeometryDto(listOf(1.0, 2.0)),
        )
        assertEquals(10.0, quake.toDomainOrNull()!!.depthKm, 0.0)
        assertEquals("https://earthquake.usgs.gov/earthquakes/eventpage/x1", quake.toDomainOrNull()!!.url)
        assertNull(quake.copy(properties = quake.properties!!.copy(type = "quarry blast")).toDomainOrNull())
        assertNull(quake.copy(properties = quake.properties!!.copy(time = null)).toDomainOrNull())
        assertNull(quake.copy(id = " ").toDomainOrNull())
    }

    @Test
    fun `a cached row reads back as the quake that was stored`() {
        val quake = week.first().toDomainOrNull()!!

        assertEquals(quake, quake.toEntity(EarthquakeFeed.MAGNITUDE_2_5_WEEK).toDomain())
    }
}
