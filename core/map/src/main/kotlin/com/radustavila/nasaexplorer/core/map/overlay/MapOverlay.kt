package com.radustavila.nasaexplorer.core.map.overlay

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A WGS 84 position. */
@Immutable
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "latitude $latitude is out of range" }
        require(longitude in -180.0..180.0) { "longitude $longitude is out of range" }
    }
}

/**
 * A named group of shapes drawn above the imagery, such as "open wildfires" (F9) or "aurora oval"
 * (F6). Overlays stack in list order, the last one on top. Updating an overlay's shapes or
 * visibility is cheap: only its data is replaced.
 */
@Immutable
data class MapOverlay(
    /** Unique among the overlays passed to one map. */
    val id: String,
    val shapes: List<MapShape>,
    val visible: Boolean = true,
)

/** Something drawn on an overlay. [id] comes back in [MapShapeClick] when the user taps it. */
@Immutable
sealed interface MapShape {
    val id: String
}

/** A filled circle at one position, sized in dp so it stays the same on screen at every zoom. */
data class MapMarker(
    override val id: String,
    val position: GeoPoint,
    val color: Color,
    val radius: Dp = 6.dp,
    val strokeColor: Color = Color.White,
    val strokeWidth: Dp = 1.5.dp,
) : MapShape

/** A line through two or more points, e.g. a storm track. */
data class MapPolyline(
    override val id: String,
    val points: List<GeoPoint>,
    val color: Color,
    val width: Dp = 2.dp,
) : MapShape {
    init {
        require(points.size >= 2) { "a polyline needs at least two points" }
    }
}

/**
 * A filled area, e.g. an event footprint or an aurora probability band. Rings need not repeat their
 * first point at the end; the map closes them.
 */
data class MapPolygon(
    override val id: String,
    val outline: List<GeoPoint>,
    val fillColor: Color,
    val holes: List<List<GeoPoint>> = emptyList(),
    val strokeColor: Color = Color.Transparent,
    val strokeWidth: Dp = 1.dp,
) : MapShape {
    init {
        require(outline.size >= 3) { "a polygon needs at least three points" }
        require(holes.all { it.size >= 3 }) { "a polygon hole needs at least three points" }
    }
}

/** A tap on [shapeId] of overlay [overlayId], at [position]. Markers win over lines over areas. */
@Immutable
data class MapShapeClick(val overlayId: String, val shapeId: String, val position: GeoPoint)
