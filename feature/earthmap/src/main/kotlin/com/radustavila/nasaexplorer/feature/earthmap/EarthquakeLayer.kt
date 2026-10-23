package com.radustavila.nasaexplorer.feature.earthmap

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.earthquake.color
import com.radustavila.nasaexplorer.core.designsystem.earthquake.markerRadius
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapMarker
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.model.Earthquake

/**
 * The quake layer: a dot per quake sized by magnitude and coloured by depth, as on the events map;
 * the [selected] one larger and outlined on top. Bigger quakes are drawn first, so small ones stay
 * tappable. Shape ids are quake ids.
 */
internal fun earthquakeOverlays(
    quakes: List<Earthquake>,
    visible: Boolean,
    selected: Earthquake?,
): List<MapOverlay> = listOf(
    MapOverlay(
        id = EARTHQUAKE_OVERLAY_ID,
        shapes = quakes.sortedByDescending(Earthquake::magnitude).map { it.marker(selected = false) },
        visible = visible,
    ),
    MapOverlay(
        id = EARTHQUAKE_SELECTED_OVERLAY_ID,
        shapes = listOfNotNull(selected?.takeIf { visible }?.marker(selected = true)),
    ),
)

private fun Earthquake.marker(selected: Boolean) = MapMarker(
    id = id,
    position = GeoPoint(latitude = position.latitude, longitude = position.longitude),
    color = depth.color.copy(alpha = if (selected) 1f else MARKER_ALPHA),
    radius = if (selected) markerRadius + 3.dp else markerRadius,
    // A light rim tells quakes from the events' dark-rimmed markers.
    strokeColor = if (selected) Color.White else Color.White.copy(alpha = STROKE_ALPHA),
    strokeWidth = if (selected) 3.dp else 1.dp,
)

internal const val EARTHQUAKE_OVERLAY_ID = "earthquakes"
internal const val EARTHQUAKE_SELECTED_OVERLAY_ID = "earthquake-selected"
private const val MARKER_ALPHA = 0.85f
private const val STROKE_ALPHA = 0.6f
