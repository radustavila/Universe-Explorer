package com.radustavila.nasaexplorer.data.earthevents.mapper

import com.radustavila.nasaexplorer.core.model.Earthquake
import com.radustavila.nasaexplorer.core.model.EarthquakeAlertLevel
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.data.earthevents.local.EarthquakeEntity
import com.radustavila.nasaexplorer.data.earthevents.remote.EarthquakeFeed
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.EarthquakeFeatureDto
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.EarthquakePropertiesDto
import java.time.Instant

/**
 * A quake the app can draw, or null when the feature is malformed: no id, magnitude, time or
 * position, coordinates out of range, or not an earthquake at all (USGS also lists quarry blasts
 * and explosions).
 */
internal fun EarthquakeFeatureDto.toDomainOrNull(): Earthquake? {
    val id = id?.trim()?.takeIf(String::isNotEmpty)
    val coordinates = geometry?.coordinates?.takeIf { it.size >= 2 && it.isOnTheGlobe() }
    val quake = properties?.takeIf { it.type == null || it.type == EARTHQUAKE_TYPE }
    return if (id != null && coordinates != null) quake?.toDomainOrNull(id, coordinates) else null
}

private fun EarthquakePropertiesDto.toDomainOrNull(id: String, coordinates: List<Double>): Earthquake? {
    val magnitude = mag?.takeIf(Double::isFinite)
    val time = time
    if (magnitude == null || time == null) return null
    val (longitude, latitude) = coordinates
    return Earthquake(
        id = id,
        magnitude = magnitude,
        place = place?.trim()?.takeIf(String::isNotEmpty),
        time = Instant.ofEpochMilli(time),
        position = GeoCoordinate(latitude = latitude, longitude = longitude),
        // Depth is sometimes missing for quakes too fresh to be located in depth; USGS then uses 10.
        depthKm = coordinates.getOrNull(2)?.takeIf(Double::isFinite) ?: DEFAULT_DEPTH_KM,
        url = url?.takeIf { it.startsWith("https://") } ?: "$EVENT_PAGE$id",
        feltReports = felt?.takeIf { it > 0 },
        alertLevel = EarthquakeAlertLevel.fromKey(alert),
        tsunami = tsunami == 1,
        significance = sig ?: 0,
    )
}

/** `[longitude, latitude, …]` within range. */
private fun List<Double>.isOnTheGlobe(): Boolean =
    get(1) in -MAX_LATITUDE..MAX_LATITUDE && get(0) in -MAX_LONGITUDE..MAX_LONGITUDE

internal fun Earthquake.toEntity(feed: EarthquakeFeed) = EarthquakeEntity(
    feed = feed.key,
    id = id,
    magnitude = magnitude,
    place = place,
    timeMillis = time.toEpochMilli(),
    latitude = position.latitude,
    longitude = position.longitude,
    depthKm = depthKm,
    url = url,
    felt = feltReports,
    alert = alertLevel?.key,
    tsunami = tsunami,
    significance = significance,
)

internal fun EarthquakeEntity.toDomain() = Earthquake(
    id = id,
    magnitude = magnitude,
    place = place,
    time = Instant.ofEpochMilli(timeMillis),
    position = GeoCoordinate(latitude = latitude, longitude = longitude),
    depthKm = depthKm,
    url = url,
    feltReports = felt,
    alertLevel = EarthquakeAlertLevel.fromKey(alert),
    tsunami = tsunami,
    significance = significance,
)

private const val EARTHQUAKE_TYPE = "earthquake"
private const val EVENT_PAGE = "https://earthquake.usgs.gov/earthquakes/eventpage/"
private const val DEFAULT_DEPTH_KM = 10.0
private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0
