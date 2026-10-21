package com.radustavila.nasaexplorer.feature.sky.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapMarker
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.map.overlay.MapPolyline
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.SatellitePosition
import kotlin.math.abs

const val ISS_TRACK_OVERLAY_ID = "iss-track"
const val ISS_POSITION_OVERLAY_ID = "iss-position"
const val OBSERVER_OVERLAY_ID = "observer"

/**
 * The ISS ground track, the bottom of what the shared map draws for the ISS (then the observer,
 * then the station). Each overlay is built on its own, because the station's dot moves far more
 * often than the rest. The track is cut where it crosses the antimeridian, so no line spans the
 * whole map.
 */
internal fun groundTrackOverlay(track: List<SatellitePosition>, color: Color): MapOverlay = MapOverlay(
    id = ISS_TRACK_OVERLAY_ID,
    shapes = track.splitAtAntimeridian().mapIndexed { index, segment ->
        MapPolyline(id = "track-$index", points = segment, color = color, width = 2.dp)
    },
)

internal fun observerOverlay(observer: GeoCoordinate?, color: Color): MapOverlay = MapOverlay(
    id = OBSERVER_OVERLAY_ID,
    shapes = listOfNotNull(
        observer?.let { MapMarker(id = "observer", position = it.toGeoPoint(), color = color, radius = 5.dp) },
    ),
)

internal fun stationOverlay(position: SatellitePosition?, color: Color): MapOverlay = MapOverlay(
    id = ISS_POSITION_OVERLAY_ID,
    shapes = listOfNotNull(
        position?.let { MapMarker(id = "iss", position = it.coordinate.toGeoPoint(), color = color, radius = 8.dp) },
    ),
)

internal fun List<SatellitePosition>.splitAtAntimeridian(): List<List<GeoPoint>> {
    val segments = mutableListOf<MutableList<GeoPoint>>()
    var current = mutableListOf<GeoPoint>()
    forEachIndexed { index, point ->
        if (index > 0 && abs(point.coordinate.longitude - this[index - 1].coordinate.longitude) > HALF_TURN) {
            segments += current
            current = mutableListOf()
        }
        current += point.coordinate.toGeoPoint()
    }
    segments += current
    return segments.filter { it.size >= 2 }
}

internal fun GeoCoordinate.toGeoPoint(): GeoPoint = GeoPoint(latitude = latitude, longitude = longitude)

private const val HALF_TURN = 180.0
