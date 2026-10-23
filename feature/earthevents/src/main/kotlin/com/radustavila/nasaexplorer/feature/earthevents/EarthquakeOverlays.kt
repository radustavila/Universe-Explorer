package com.radustavila.nasaexplorer.feature.earthevents

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.earthquake.color
import com.radustavila.nasaexplorer.core.designsystem.earthquake.markerRadius
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapMarker
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.model.Earthquake

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

private fun Earthquake.marker(selected: Boolean) = MapMarker(
    id = id,
    position = mapPosition,
    color = depth.color.copy(alpha = if (selected) 1f else MARKER_ALPHA),
    radius = if (selected) markerRadius + 3.dp else markerRadius,
    strokeColor = if (selected) Color.White else Color.Black.copy(alpha = STROKE_ALPHA),
    strokeWidth = if (selected) 3.dp else 1.dp,
)

internal const val EARTHQUAKE_OVERLAY_ID = "earthquakes"
internal const val EARTHQUAKE_SELECTED_OVERLAY_ID = "earthquake-selected"
private const val MARKER_ALPHA = 0.85f
private const val STROKE_ALPHA = 0.6f
