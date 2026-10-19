package com.radustavila.nasaexplorer.core.map.internal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapMarker
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.map.overlay.MapPolygon
import com.radustavila.nasaexplorer.core.map.overlay.MapPolyline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

class OverlayFeaturesTest {

    @Test
    fun `a marker becomes a point with its id, colour and size`() {
        val feature = MapMarker(
            id = "EONET_123",
            position = GeoPoint(latitude = 45.5, longitude = -120.25),
            color = Color.Red,
            radius = 8.dp,
        ).toFeature(overlayId = "wildfires")

        val point = feature.geometry() as Point
        assertEquals(-120.25, point.longitude(), 0.0)
        assertEquals(45.5, point.latitude(), 0.0)
        assertEquals("wildfires", feature.getStringProperty(OverlayProperties.OVERLAY_ID))
        assertEquals("EONET_123", feature.getStringProperty(OverlayProperties.SHAPE_ID))
        assertEquals("rgba(255, 0, 0, 1)", feature.getStringProperty(OverlayProperties.COLOR))
        assertEquals(8.0, feature.getNumberProperty(OverlayProperties.SIZE).toDouble(), 0.0)
        assertEquals("rgba(255, 255, 255, 1)", feature.getStringProperty(OverlayProperties.STROKE_COLOR))
    }

    @Test
    fun `a polyline keeps its points in order, longitude first`() {
        val feature = MapPolyline(
            id = "track",
            points = listOf(GeoPoint(10.0, 20.0), GeoPoint(11.0, 21.0), GeoPoint(12.0, 23.0)),
            color = Color.Blue,
            width = 3.dp,
        ).toFeature(overlayId = "storms")

        val line = feature.geometry() as LineString
        assertEquals(listOf(20.0 to 10.0, 21.0 to 11.0, 23.0 to 12.0), line.coordinates().map { it.longitude() to it.latitude() })
        assertEquals(3.0, feature.getNumberProperty(OverlayProperties.SIZE).toDouble(), 0.0)
    }

    @Test
    fun `polygon rings are closed and holes kept`() {
        val feature = MapPolygon(
            id = "oval",
            outline = listOf(GeoPoint(60.0, -10.0), GeoPoint(60.0, 10.0), GeoPoint(70.0, 0.0)),
            holes = listOf(listOf(GeoPoint(64.0, -1.0), GeoPoint(64.0, 1.0), GeoPoint(65.0, 0.0), GeoPoint(64.0, -1.0))),
            fillColor = Color.Green.copy(alpha = 0.4f),
        ).toFeature(overlayId = "aurora")

        val rings = (feature.geometry() as Polygon).coordinates()
        assertEquals(2, rings.size)
        assertEquals(4, rings[0].size)
        assertEquals(rings[0].first(), rings[0].last())
        assertEquals(4, rings[1].size)
        assertEquals("rgba(0, 255, 0, 0.4)", feature.getStringProperty(OverlayProperties.COLOR))
        assertEquals("rgba(0, 0, 0, 0)", feature.getStringProperty(OverlayProperties.STROKE_COLOR))
    }

    @Test
    fun `an overlay becomes one collection with a feature per shape`() {
        val overlay = MapOverlay(
            id = "events",
            shapes = listOf(
                MapMarker("a", GeoPoint(0.0, 0.0), Color.Red),
                MapPolyline("b", listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0)), Color.Red),
            ),
        )

        val features = requireNotNull(overlay.toFeatureCollection().features())
        assertEquals(listOf("a", "b"), features.map { it.getStringProperty(OverlayProperties.SHAPE_ID) })
    }

    @Test
    fun `invalid shapes are rejected where they are built`() {
        assertThrows(IllegalArgumentException::class.java) { GeoPoint(latitude = 91.0, longitude = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { MapPolyline("x", listOf(GeoPoint(0.0, 0.0)), Color.Red) }
        assertThrows(IllegalArgumentException::class.java) {
            MapPolygon("x", listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0)), Color.Red)
        }
    }
}
