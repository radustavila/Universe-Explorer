package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.Earthquake
import com.radustavila.nasaexplorer.core.model.EarthquakeAlertLevel
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import java.time.Duration
import java.time.Instant

/** Quakes shaped like USGS's feeds on 2026-10-23. */
object TestEarthquakes {

    val NOW: Instant = Instant.parse("2026-10-23T11:00:00Z")

    fun earthquake(
        id: String = "us6000u4bk",
        magnitude: Double = 5.7,
        place: String? = "61 km WNW of Leni, Italy",
        time: Instant = NOW.minus(Duration.ofHours(1)),
        position: GeoCoordinate = GeoCoordinate(latitude = 38.7199, longitude = 14.152),
        depthKm: Double = 10.0,
        feltReports: Int? = 4,
        alertLevel: EarthquakeAlertLevel? = EarthquakeAlertLevel.GREEN,
        tsunami: Boolean = false,
        significance: Int = 502,
    ): Earthquake = Earthquake(
        id = id,
        magnitude = magnitude,
        place = place,
        time = time,
        position = position,
        depthKm = depthKm,
        url = "https://earthquake.usgs.gov/earthquakes/eventpage/$id",
        feltReports = feltReports,
        alertLevel = alertLevel,
        tsunami = tsunami,
        significance = significance,
    )

    /** A small, deep quake far from anyone: shown on the map, never an alert. */
    fun small(
        id: String = "us7000qx2a",
        time: Instant = NOW.minus(Duration.ofHours(5)),
    ): Earthquake = earthquake(
        id = id,
        magnitude = 2.9,
        place = "120 km S of Fiji",
        time = time,
        position = GeoCoordinate(latitude = -19.2, longitude = 178.4),
        depthKm = 540.0,
        feltReports = null,
        alertLevel = null,
        significance = 129,
    )

    /** The month's strongest: significant, with a tsunami flag. */
    fun significant(
        id: String = "us6000tzzz",
        time: Instant = NOW.minus(Duration.ofDays(12)),
    ): Earthquake = earthquake(
        id = id,
        magnitude = 7.7,
        place = "12 km WSW of Pitaloza Arriba, Panama",
        time = time,
        position = GeoCoordinate(latitude = 7.4, longitude = -80.6),
        depthKm = 22.0,
        feltReports = 3210,
        alertLevel = EarthquakeAlertLevel.ORANGE,
        tsunami = true,
        significance = 1790,
    )
}
