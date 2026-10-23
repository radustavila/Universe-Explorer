package com.radustavila.nasaexplorer.data.earthevents.mapper

import com.radustavila.nasaexplorer.core.model.Earthquake
import com.radustavila.nasaexplorer.core.model.EarthquakeAlertLevel
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.data.earthevents.local.EarthquakeEntity
import com.radustavila.nasaexplorer.data.earthevents.remote.EarthquakeFeed
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.EarthquakeFeatureDto
import java.time.Instant

/**
 * A quake the app can draw, or null when the feature is malformed: no id, magnitude, time or
 * position, coordinates out of range, or not an earthquake at all (USGS also lists quarry blasts
 * and explosions).
 */
internal fun EarthquakeFeatureDto.toDomainOrNull(): Earthquake? {
    val id = id?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val properties = properties ?: return null
    if (properties.type != null && properties.type != EARTHQUAKE_TYPE) return null
    val magnitude = properties.mag?.takeIf(Double::isFinite) ?: return null
    val time = properties.time ?: return null
    val coordinates = geometry?.coordinates?.takeIf { it.size >= 2 } ?: return null
    val (longitude, latitude) = coordinates
    if (latitude !in -MAX_LATITUDE..MAX_LATITUDE || longitude !in -MAX_LONGITUDE..MAX_LONGITUDE) return null
    return Earthquake(
        id = id,
        magnitude = magnitude,
        place = properties.place?.trim()?.takeIf(String::isNotEmpty),
        time = Instant.ofEpochMilli(time),
        position = GeoCoordinate(latitude = latitude, longitude = longitude),
        // Depth is sometimes missing for quakes too fresh to be located in depth; USGS then uses 10.
        depthKm = coordinates.getOrNull(2)?.takeIf(Double::isFinite) ?: DEFAULT_DEPTH_KM,
        url = properties.url?.takeIf { it.startsWith("https://") } ?: "$EVENT_PAGE$id",
        feltReports = properties.felt?.takeIf { it > 0 },
        alertLevel = EarthquakeAlertLevel.fromKey(properties.alert),
        tsunami = properties.tsunami == 1,
        significance = properties.sig ?: 0,
    )
}

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
