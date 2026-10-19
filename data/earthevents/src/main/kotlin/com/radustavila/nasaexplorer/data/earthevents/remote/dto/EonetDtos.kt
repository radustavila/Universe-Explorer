package com.radustavila.nasaexplorer.data.earthevents.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * NASA EONET v3 wire format (`/events`), reduced to the fields the app uses. Every field is
 * optional: a malformed event is dropped during mapping instead of failing the whole response.
 */
@Serializable
data class EventsResponseDto(
    val events: List<EventDto> = emptyList(),
)

@Serializable
data class EventDto(
    /** e.g. `EONET_25340`. */
    val id: String? = null,
    val title: String? = null,
    /** ISO-8601 instant the event was closed, `null` while open. */
    val closed: String? = null,
    val categories: List<CategoryDto> = emptyList(),
    val sources: List<SourceDto> = emptyList(),
    /** Every report of the event, as EONET orders it (oldest first, but not guaranteed). */
    val geometry: List<GeometryDto> = emptyList(),
)

@Serializable
data class CategoryDto(
    /** Category key, e.g. `severeStorms`. */
    val id: String? = null,
    val title: String? = null,
)

@Serializable
data class SourceDto(
    val id: String? = null,
    val url: String? = null,
)

/**
 * One GeoJSON-style report. [coordinates] is `[lon, lat]` for a `Point` and `[[[lon, lat], ...]]`
 * (outline first, then holes) for a `Polygon`, so it stays raw until mapping.
 */
@Serializable
data class GeometryDto(
    val date: String? = null,
    val type: String? = null,
    val coordinates: JsonElement? = null,
    val magnitudeValue: Double? = null,
    /** e.g. `kts`, `acres`, `NM^2`. */
    val magnitudeUnit: String? = null,
)

/** `/layers/{category}`: the imagery layers EONET suggests for one category. */
@Serializable
data class LayersResponseDto(
    val categories: List<LayerCategoryDto> = emptyList(),
)

@Serializable
data class LayerCategoryDto(
    val layers: List<LayerDto> = emptyList(),
)

@Serializable
data class LayerDto(
    /** GIBS layer identifier, e.g. `MODIS_Terra_CorrectedReflectance_TrueColor`. */
    val name: String? = null,
    /** e.g. `https://gibs.earthdata.nasa.gov/wmts/epsg4326/best/wmts.cgi`. */
    val serviceUrl: String? = null,
    /** `WMTS_1_0_0` or `WMS_1_1_1`. */
    val serviceTypeId: String? = null,
    /** WMTS parameters, e.g. `{"TILEMATRIXSET": "250m", "FORMAT": "image/jpeg"}`. */
    val parameters: List<Map<String, String>> = emptyList(),
)
