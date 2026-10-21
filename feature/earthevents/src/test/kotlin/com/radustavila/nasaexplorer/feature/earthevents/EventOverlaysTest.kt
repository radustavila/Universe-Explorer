package com.radustavila.nasaexplorer.feature.earthevents

import com.radustavila.nasaexplorer.core.map.gibs.GibsLayer
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapMarker
import com.radustavila.nasaexplorer.core.map.overlay.MapPolygon
import com.radustavila.nasaexplorer.core.map.overlay.MapPolyline
import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EarthEventGeometry
import com.radustavila.nasaexplorer.core.model.EarthEventShape
import com.radustavila.nasaexplorer.core.model.EventImageryLayer
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.testing.data.TestEarthEvents
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventOverlaysTest {

    private val storm = TestEarthEvents.storm(points = 4)
    private val fire = TestEarthEvents.wildfire()

    @Test
    fun `areas, then tracks, then markers, one overlay per category, selection on top`() {
        val overlays = eventOverlays(listOf(fire, storm), hiddenCategories = emptySet(), selectedEventId = null)

        assertEquals(
            listOf(
                "event-areas-severeStorms", "event-areas-wildfires",
                "event-tracks-severeStorms", "event-tracks-wildfires",
                "event-markers-severeStorms", "event-markers-wildfires",
                SELECTED_OVERLAY_ID,
            ),
            overlays.map { it.id },
        )
        assertTrue(overlays.single { it.id == SELECTED_OVERLAY_ID }.shapes.isEmpty())
    }

    @Test
    fun `markers sit at the latest report, coloured by category`() {
        val markers = eventOverlays(listOf(storm), emptySet(), null)
            .single { it.id == "event-markers-severeStorms" }.shapes

        val marker = markers.single() as MapMarker
        assertEquals(storm.id, marker.id)
        assertEquals(GeoPoint(latitude = -10.8 - 3, longitude = -163.3 + 3), marker.position)
        assertEquals(EarthEventCategory.SEVERE_STORMS.color, marker.color)
    }

    @Test
    fun `a storm track runs through every report, oldest first`() {
        val track = eventOverlays(listOf(storm), emptySet(), null)
            .single { it.id == "event-tracks-severeStorms" }.shapes.single() as MapPolyline

        assertEquals(storm.geometry.sortedBy { it.date }.map { it.shape.center.latitude }, track.points.map { it.latitude })
        assertEquals(4, track.points.size)
        assertEquals(storm.id, eventIdOf(track.id))
    }

    @Test
    fun `single reports get no track`() {
        val tracks = eventOverlays(listOf(fire), emptySet(), null).single { it.id == "event-tracks-wildfires" }

        assertTrue(tracks.shapes.isEmpty())
    }

    @Test
    fun `a track across the antimeridian is split there`() {
        val base = TestEarthEvents.NOW
        val crossing = storm.copy(
            geometry = listOf(178.0, 179.5, -179.5, -178.0).mapIndexed { index, longitude ->
                EarthEventGeometry(
                    base.plus(Duration.ofHours(6L * index)),
                    EarthEventShape.Point(GeoCoordinate(latitude = -15.0, longitude = longitude)),
                )
            },
        )

        val lines = eventOverlays(listOf(crossing), emptySet(), null)
            .single { it.id == "event-tracks-severeStorms" }.shapes.map { it as MapPolyline }

        assertEquals(listOf(listOf(178.0, 179.5), listOf(-179.5, -178.0)), lines.map { line -> line.points.map { it.longitude } })
        assertEquals(2, lines.map { it.id }.toSet().size)
    }

    @Test
    fun `an area event gets its outline and a marker in the middle`() {
        val flood = EarthEvent(
            id = "EONET_25413",
            title = "Flood in Spain",
            category = EarthEventCategory.FLOODS,
            sources = emptyList(),
            geometry = listOf(
                EarthEventGeometry(
                    TestEarthEvents.NOW,
                    EarthEventShape.Polygon(
                        listOf(listOf(GeoCoordinate(41.0, 2.0), GeoCoordinate(41.0, 3.0), GeoCoordinate(42.0, 3.0), GeoCoordinate(41.0, 2.0))),
                    ),
                ),
            ),
        )

        val overlays = eventOverlays(listOf(flood), emptySet(), null)

        val area = overlays.single { it.id == "event-areas-floods" }.shapes.single() as MapPolygon
        assertEquals(flood.id, eventIdOf(area.id))
        val marker = overlays.single { it.id == "event-markers-floods" }.shapes.single() as MapMarker
        assertEquals(GeoPoint(41.5, 2.5), marker.position)
    }

    @Test
    fun `the filter hides a category's overlays without removing them`() {
        val overlays = eventOverlays(listOf(fire, storm), hiddenCategories = setOf(EarthEventCategory.WILDFIRES), null)

        assertFalse(overlays.single { it.id == "event-markers-wildfires" }.visible)
        assertTrue(overlays.single { it.id == "event-markers-severeStorms" }.visible)
        assertEquals(1, overlays.single { it.id == "event-markers-wildfires" }.shapes.size)
    }

    @Test
    fun `the selected event is drawn again, larger, unless its category is hidden`() {
        val selected = eventOverlays(listOf(fire, storm), emptySet(), selectedEventId = fire.id)
            .single { it.id == SELECTED_OVERLAY_ID }.shapes.single() as MapMarker
        assertEquals(fire.id, selected.id)
        assertTrue(selected.radius.value > 6f)

        val hidden = eventOverlays(listOf(fire, storm), setOf(EarthEventCategory.WILDFIRES), selectedEventId = fire.id)
        assertTrue(hidden.single { it.id == SELECTED_OVERLAY_ID }.shapes.isEmpty())
    }

    @Test
    fun `event imagery builds a daily GIBS layer, or true colour without one`() {
        val date = LocalDate.of(2026, 10, 14)
        val layer = EventImageryLayer("MODIS_Terra_CorrectedReflectance_Bands721", "GoogleMapsCompatible_Level9", "jpg")

        val imagery = EventImagery("E1", layer, date)

        assertEquals(
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/MODIS_Terra_CorrectedReflectance_Bands721/default/" +
                "2026-10-14/GoogleMapsCompatible_Level9/3/2/1.jpg",
            imagery.toGibsImagery().layer.tileUrl(date, zoom = 3, x = 1, y = 2),
        )
        assertEquals("MODIS Terra Corrected Reflectance Bands721", imagery.layerName)
        assertEquals(GibsLayer.ViirsSnppTrueColor, EventImagery("E1", null, date).toGibsImagery().layer)
        assertEquals(
            GibsLayer.ViirsSnppTrueColor,
            EventImagery("E1", layer.copy(tileMatrixSet = "unknown"), date).toGibsImagery().layer,
        )
    }
}
