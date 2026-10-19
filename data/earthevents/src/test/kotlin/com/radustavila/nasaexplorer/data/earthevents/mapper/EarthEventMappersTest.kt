package com.radustavila.nasaexplorer.data.earthevents.mapper

import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EarthEventShape
import com.radustavila.nasaexplorer.core.model.EarthEventSource
import com.radustavila.nasaexplorer.core.model.EventMagnitude
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.data.earthevents.remote.EonetFixtures
import com.radustavila.nasaexplorer.data.earthevents.remote.eventsFixture
import com.radustavila.nasaexplorer.data.earthevents.remote.testJson
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.EventsResponseDto
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarthEventMappersTest {

    private fun parse(json: String) = testJson.decodeFromString<EventsResponseDto>(json).events

    @Test
    fun `recorded open events parse with Point geometry`() {
        val events = eventsFixture(EonetFixtures.OPEN_EVENTS).events.mapNotNull { it.toDomainOrNull() }

        assertEquals(
            listOf("EONET_25340", "EONET_25310", "EONET_25311", "EONET_6510", "EONET_20710"),
            events.map { it.id },
        )
        val fire = events.first { it.id == "EONET_25310" }
        assertEquals("Prescribed Fire Newman Piles RX, Coconino, Arizona", fire.title)
        assertEquals(EarthEventCategory.WILDFIRES, fire.category)
        assertEquals(
            listOf(EarthEventSource("IRWIN", "https://irwin.doi.gov/observer/incidents/2026-AZCOF-001566")),
            fire.sources,
        )
        assertEquals(EarthEventShape.Point(GeoCoordinate(latitude = 35.016667, longitude = -111.65)), fire.latest.shape)
        assertEquals(EventMagnitude(751.0, "acres"), fire.latest.magnitude)
        assertEquals(Instant.parse("2026-10-14T10:42:00Z"), fire.lastUpdate)
        assertNull(fire.closedAt)
    }

    @Test
    fun `a storm keeps its whole track, oldest report first, with wind speeds`() {
        val storm = eventsFixture(EonetFixtures.OPEN_EVENTS).events
            .mapNotNull { it.toDomainOrNull() }
            .first { it.id == "EONET_25340" }

        assertEquals(EarthEventCategory.SEVERE_STORMS, storm.category)
        assertEquals(13, storm.track.size)
        assertEquals(storm.geometry.map { it.date }.sorted(), storm.geometry.map { it.date })
        assertEquals(GeoCoordinate(latitude = -10.8, longitude = -163.3), storm.track.first())
        assertEquals(EventMagnitude(35.0, "kts"), storm.geometry.first().magnitude)
    }

    @Test
    fun `reports given out of order are sorted by date`() {
        val event = parse(
            """
            {"events": [{"id": "E1", "title": "Storm", "categories": [{"id": "severeStorms"}],
              "geometry": [
                {"date": "2026-10-18T12:00:00Z", "type": "Point", "coordinates": [3.0, 3.0]},
                {"date": "2026-10-18T00:00:00Z", "type": "Point", "coordinates": [1.0, 1.0]},
                {"date": "2026-10-18T06:00:00Z", "type": "Point", "coordinates": [2.0, 2.0]}
              ]}]}
            """,
        ).single().toDomainOrNull()!!

        assertEquals(listOf(1.0, 2.0, 3.0), event.track.map { it.longitude })
    }

    @Test
    fun `an event without magnitude has none`() {
        val volcano = eventsFixture(EonetFixtures.OPEN_EVENTS).events
            .mapNotNull { it.toDomainOrNull() }
            .first { it.id == "EONET_20710" }

        assertEquals(EarthEventCategory.VOLCANOES, volcano.category)
        assertNull(volcano.latest.magnitude)
    }

    @Test
    fun `recorded floods parse with Polygon geometry`() {
        val floods = eventsFixture(EonetFixtures.FLOOD_POLYGONS).events.mapNotNull { it.toDomainOrNull() }

        assertEquals(3, floods.size)
        val spain = floods.first()
        assertEquals("Flood in Spain 1104235", spain.title)
        assertEquals(EarthEventCategory.FLOODS, spain.category)
        assertEquals(Instant.parse("2026-10-18T00:00:00Z"), spain.closedAt)
        val polygon = spain.latest.shape as EarthEventShape.Polygon
        assertEquals(1, polygon.rings.size)
        assertEquals(127, polygon.rings.single().size)
        assertEquals(GeoCoordinate(latitude = 41.4938593, longitude = 2.3766254), polygon.rings.single().first())
        assertTrue(polygon.center.latitude in 41.0..42.0)
        assertTrue(spain.track.isEmpty())
    }

    @Test
    fun `malformed events are dropped`() {
        val events = parse(
            """
            {"events": [
              {"title": "No id", "categories": [{"id": "wildfires"}],
               "geometry": [{"date": "2026-10-18T00:00:00Z", "type": "Point", "coordinates": [1.0, 1.0]}]},
              {"id": "E2", "title": "Unknown category", "categories": [{"id": "meteors"}],
               "geometry": [{"date": "2026-10-18T00:00:00Z", "type": "Point", "coordinates": [1.0, 1.0]}]},
              {"id": "E3", "title": "No geometry", "categories": [{"id": "wildfires"}], "geometry": []},
              {"id": "E4", "title": "Out of range", "categories": [{"id": "wildfires"}],
               "geometry": [{"date": "2026-10-18T00:00:00Z", "type": "Point", "coordinates": [200.0, 1.0]}]},
              {"id": "E5", "title": "Bad date", "categories": [{"id": "wildfires"}],
               "geometry": [{"date": "yesterday", "type": "Point", "coordinates": [1.0, 1.0]}]},
              {"id": "E6", "title": "  Kept  ", "categories": [{"id": "wildfires"}],
               "sources": [{"id": "X", "url": "not a link"}],
               "geometry": [
                 {"date": "2026-10-18T00:00:00Z", "type": "LineString", "coordinates": [[1.0, 1.0]]},
                 {"date": "2026-10-18T00:00:00Z", "type": "Point", "coordinates": [1.0, 2.0]}
               ]}
            ]}
            """,
        ).mapNotNull { it.toDomainOrNull() }

        val kept = events.single()
        assertEquals("E6", kept.id)
        assertEquals("Kept", kept.title)
        assertTrue(kept.sources.isEmpty())
        assertEquals(1, kept.geometry.size)
    }

    @Test
    fun `an event survives the trip through the cache unchanged`() {
        val events = (eventsFixture(EonetFixtures.OPEN_EVENTS).events + eventsFixture(EonetFixtures.FLOOD_POLYGONS).events)
            .mapNotNull { it.toDomainOrNull() }
        val now = Instant.parse("2026-10-19T10:00:00Z")

        val restored = events.map { it.toEntity(Json, now).toDomainOrNull(Json) }

        assertEquals(events, restored)
    }
}
