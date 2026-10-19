package com.radustavila.nasaexplorer.core.map.internal

import androidx.compose.ui.graphics.Color
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapMarker
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.map.overlay.MapPolygon
import com.radustavila.nasaexplorer.core.map.overlay.MapPolyline
import com.radustavila.nasaexplorer.core.map.overlay.MapShape
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * Feature properties the overlay style layers read with data-driven expressions, so one GeoJSON
 * source and four style layers draw an overlay whatever its shapes look like.
 */
internal object OverlayProperties {
    const val OVERLAY_ID = "overlayId"
    const val SHAPE_ID = "shapeId"
    const val COLOR = "color"
    const val SIZE = "size"
    const val STROKE_COLOR = "strokeColor"
    const val STROKE_WIDTH = "strokeWidth"
}

internal fun MapOverlay.toFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(shapes.map { it.toFeature(overlayId = id) })

internal fun MapShape.toFeature(overlayId: String): Feature {
    val feature = when (this) {
        is MapMarker -> Feature.fromGeometry(position.toPoint()).apply {
            addStringProperty(OverlayProperties.COLOR, color.toCssColor())
            addNumberProperty(OverlayProperties.SIZE, radius.value)
            addStringProperty(OverlayProperties.STROKE_COLOR, strokeColor.toCssColor())
            addNumberProperty(OverlayProperties.STROKE_WIDTH, strokeWidth.value)
        }

        is MapPolyline -> Feature.fromGeometry(LineString.fromLngLats(points.map(GeoPoint::toPoint))).apply {
            addStringProperty(OverlayProperties.COLOR, color.toCssColor())
            addNumberProperty(OverlayProperties.SIZE, width.value)
        }

        is MapPolygon -> {
            val rings = (listOf(outline) + holes).map { ring -> ring.closed().map(GeoPoint::toPoint) }
            Feature.fromGeometry(Polygon.fromLngLats(rings)).apply {
                addStringProperty(OverlayProperties.COLOR, fillColor.toCssColor())
                addStringProperty(OverlayProperties.STROKE_COLOR, strokeColor.toCssColor())
                addNumberProperty(OverlayProperties.STROKE_WIDTH, strokeWidth.value)
            }
        }
    }
    feature.addStringProperty(OverlayProperties.OVERLAY_ID, overlayId)
    feature.addStringProperty(OverlayProperties.SHAPE_ID, id)
    return feature
}

/** GeoJSON wants longitude first. */
private fun GeoPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun List<GeoPoint>.closed(): List<GeoPoint> = if (first() == last()) this else this + first()

/** `rgba(r, g, b, a)`, which MapLibre's `to-color` expression parses. */
internal fun Color.toCssColor(): String {
    fun channel(value: Float) = (value * MAX_CHANNEL).toInt()
    val alphaText = "%.3f".format(java.util.Locale.ROOT, alpha).trimEnd('0').trimEnd('.')
    return "rgba(${channel(red)}, ${channel(green)}, ${channel(blue)}, ${alphaText.ifEmpty { "0" }})"
}

private const val MAX_CHANNEL = 255f
