package com.radustavila.nasaexplorer.data.earthevents.remote.dto

import kotlinx.serialization.Serializable

/**
 * USGS GeoJSON summary feed (`summary/2.5_week.geojson`, …), reduced to the fields the app uses.
 * Every field is optional: a malformed quake is dropped during mapping instead of failing the feed.
 */
@Serializable
data class EarthquakeFeedDto(
    val features: List<EarthquakeFeatureDto> = emptyList(),
)

@Serializable
data class EarthquakeFeatureDto(
    /** USGS event id, e.g. `us6000u4bk`. */
    val id: String? = null,
    val properties: EarthquakePropertiesDto? = null,
    val geometry: EarthquakeGeometryDto? = null,
)

@Serializable
data class EarthquakePropertiesDto(
    val mag: Double? = null,
    /** "61 km WNW of Leni, Italy". */
    val place: String? = null,
    /** Origin time, epoch milliseconds. */
    val time: Long? = null,
    /** The event page. */
    val url: String? = null,
    /** "Did You Feel It?" reports. */
    val felt: Int? = null,
    /** PAGER level: green, yellow, orange or red. */
    val alert: String? = null,
    /** 1 for a large quake in an oceanic region. */
    val tsunami: Int? = null,
    val sig: Int? = null,
    /** `earthquake`, or `quarry blast`, `explosion`, … */
    val type: String? = null,
)

@Serializable
data class EarthquakeGeometryDto(
    /** `[longitude, latitude, depth km]`. */
    val coordinates: List<Double> = emptyList(),
)
