package com.radustavila.nasaexplorer.feature.earthevents

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.earthquake.color
import com.radustavila.nasaexplorer.core.designsystem.earthquake.markerRadius
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapMarker
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.model.Earthquake
import kotlin.math.cos
import kotlin.math.pow

/**
 * The quakes over the events: a dot per quake, sized by magnitude and coloured by depth. Bigger
 * quakes are drawn first, so the small ones on top of them stay tappable. Shape ids are quake ids.
 */
internal fun earthquakeOverlay(quakes: List<Earthquake>): MapOverlay = MapOverlay(
    id = EARTHQUAKE_OVERLAY_ID,
    shapes = quakes.sortedByDescending(Earthquake::magnitude).map { it.marker(selected = false) },
)

/** The selected quake again, larger and outlined, on top of everything. */
internal fun earthquakeSelectionOverlay(selected: Earthquake?): MapOverlay = MapOverlay(
    id = EARTHQUAKE_SELECTED_OVERLAY_ID,
    shapes = listOfNotNull(selected?.marker(selected = true)),
)

internal fun isEarthquakeOverlay(overlayId: String): Boolean =
    overlayId == EARTHQUAKE_OVERLAY_ID || overlayId == EARTHQUAKE_SELECTED_OVERLAY_ID

internal val Earthquake.mapPosition: GeoPoint
    get() = GeoPoint(latitude = position.latitude, longitude = position.longitude)

/**
 * The point [dp] below this one on screen at [zoom], in Web Mercator with 512 dp tiles (MapLibre's):
 * a degree of latitude spans 1/cos(latitude) times as many dp as a degree of longitude.
 */
internal fun GeoPoint.southBy(dp: Double, zoom: Double): GeoPoint {
    val degreesPerDp = FULL_CIRCLE_DEGREES / (TILE_SIZE_DP * 2.0.pow(zoom))
    val shifted = latitude - dp * degreesPerDp * cos(Math.toRadians(latitude))
    return copy(latitude = shifted.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE))
}

private fun Earthquake.marker(selected: Boolean) = MapMarker(
    id = id,
    position = mapPosition,
    color = depth.color.copy(alpha = if (selected) 1f else MARKER_ALPHA),
    radius = if (selected) markerRadius + 3.dp else markerRadius,
    // A light rim tells quakes from the events' dark-rimmed markers.
    strokeColor = if (selected) Color.White else Color.White.copy(alpha = STROKE_ALPHA),
    strokeWidth = if (selected) 3.dp else 1.dp,
)

internal const val EARTHQUAKE_OVERLAY_ID = "earthquakes"
internal const val EARTHQUAKE_SELECTED_OVERLAY_ID = "earthquake-selected"
private const val MARKER_ALPHA = 0.85f
private const val FULL_CIRCLE_DEGREES = 360.0
private const val TILE_SIZE_DP = 512.0
private const val MAX_MERCATOR_LATITUDE = 85.0
private const val STROKE_ALPHA = 0.6f
