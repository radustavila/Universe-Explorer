package com.radustavila.nasaexplorer.data.earthevents.mapper

import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EventImageryLayer
import com.radustavila.nasaexplorer.data.earthevents.local.ImageryLayerEntity
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.LayerDto
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.LayersResponseDto
import java.time.Instant

/**
 * The layers of a `/layers/{category}` answer the app's map can draw, translated to Web Mercator.
 *
 * EONET lists GIBS layers in their geographic projection (EPSG:4326), named by resolution
 * (`250m`, `2km`...). GIBS serves the same layers in Web Mercator under the matching
 * `GoogleMapsCompatible_LevelN` set, so only the matrix set and file extension change. WMS layers
 * and other hosts are skipped.
 */
internal fun LayersResponseDto.toImageryLayers(): List<EventImageryLayer> =
    categories.flatMap { it.layers }
        .mapNotNull { it.toImageryLayerOrNull() }
        .distinctBy(EventImageryLayer::id)

private fun LayerDto.toImageryLayerOrNull(): EventImageryLayer? {
    val id = name?.takeIf(String::isNotBlank) ?: return null
    if (serviceTypeId != WMTS_SERVICE || serviceUrl?.startsWith(GIBS_WMTS_PREFIX) != true) return null
    val parameters = this.parameters.firstOrNull() ?: return null
    val matrixSet = parameters[KEY_MATRIX_SET]?.let(GEOGRAPHIC_TO_WEB_MERCATOR::get) ?: return null
    val extension = parameters[KEY_FORMAT]?.let(FORMAT_EXTENSIONS::get) ?: return null
    return EventImageryLayer(id = id, tileMatrixSet = matrixSet, fileExtension = extension)
}

/**
 * The layer that shows [category] best among [layers]: the first of the preferred layers that EONET
 * lists for it, else the first layer listed.
 *
 * True colour is the default everywhere: smoke plumes, storm spirals and ice read best as a photo.
 * Floods prefer the 7-2-1 false colour, where flood water turns dark blue against green land.
 */
internal fun chooseImageryLayer(category: EarthEventCategory, layers: List<EventImageryLayer>): EventImageryLayer? {
    val preferred = when (category) {
        EarthEventCategory.FLOODS -> listOf(MODIS_TERRA_BANDS_721) + TRUE_COLOR_LAYERS
        else -> TRUE_COLOR_LAYERS
    }
    return preferred.firstNotNullOfOrNull { id -> layers.firstOrNull { it.id == id } } ?: layers.firstOrNull()
}

internal fun EventImageryLayer.toEntity(category: EarthEventCategory, position: Int, cachedAt: Instant) =
    ImageryLayerEntity(
        category = category.key,
        layerId = id,
        position = position,
        tileMatrixSet = tileMatrixSet,
        fileExtension = fileExtension,
        cachedAtMillis = cachedAt.toEpochMilli(),
    )

internal fun ImageryLayerEntity.toDomain() = EventImageryLayer(
    id = layerId,
    tileMatrixSet = tileMatrixSet,
    fileExtension = fileExtension,
)

private const val WMTS_SERVICE = "WMTS_1_0_0"
private const val GIBS_WMTS_PREFIX = "https://gibs.earthdata.nasa.gov/wmts/"
private const val KEY_MATRIX_SET = "TILEMATRIXSET"
private const val KEY_FORMAT = "FORMAT"

/** GIBS's geographic matrix sets and the Web Mercator set with the same resolution. */
private val GEOGRAPHIC_TO_WEB_MERCATOR = mapOf(
    "15.625m" to "GoogleMapsCompatible_Level13",
    "31.25m" to "GoogleMapsCompatible_Level12",
    "250m" to "GoogleMapsCompatible_Level9",
    "500m" to "GoogleMapsCompatible_Level8",
    "1km" to "GoogleMapsCompatible_Level7",
    "2km" to "GoogleMapsCompatible_Level6",
)

private val FORMAT_EXTENSIONS = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
)

private const val MODIS_TERRA_BANDS_721 = "MODIS_Terra_CorrectedReflectance_Bands721"

private val TRUE_COLOR_LAYERS = listOf(
    "VIIRS_SNPP_CorrectedReflectance_TrueColor",
    "MODIS_Terra_CorrectedReflectance_TrueColor",
    "MODIS_Aqua_CorrectedReflectance_TrueColor",
)
