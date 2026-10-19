package com.radustavila.nasaexplorer.data.earthevents.remote

import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.EventsResponseDto
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.LayersResponseDto
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.create

/** Recorded from eonet.gsfc.nasa.gov/api/v3 on 2026-10-19. */
internal object EonetFixtures {
    /**
     * `events?status=open&days=60`, trimmed to five of its events (the full answer is ~220 KB):
     * Tropical Cyclone Wati (13 points, kts), two prescribed fires (acres), Iceberg D33C (23 points,
     * no magnitude) and, from the unwindowed open list, Nevados del Chillan volcano.
     */
    const val OPEN_EVENTS = "earthevents/open_events.json"

    /** `events?category=floods&status=all&limit=3`: three closed GDACS floods with Polygon geometry. */
    const val FLOOD_POLYGONS = "earthevents/floods_polygons.json"

    /** `layers/wildfires`: 27 layers, 24 GIBS WMTS (EPSG:4326) and 3 WMS. */
    const val LAYERS_WILDFIRES = "earthevents/layers_wildfires.json"

    /** `layers/severeStorms`: 24 layers. */
    const val LAYERS_SEVERE_STORMS = "earthevents/layers_severe_storms.json"
}

/** The same JSON configuration the app's network module provides. */
internal val testJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

internal fun eonetService(server: FixtureServer): EonetService =
    RetrofitFactory(testJson, OkHttpClient()).create(server.baseUrl).create()

internal fun eventsFixture(path: String): EventsResponseDto = testJson.decodeFromString(Fixtures.read(path))

internal fun layersFixture(path: String): LayersResponseDto = testJson.decodeFromString(Fixtures.read(path))
