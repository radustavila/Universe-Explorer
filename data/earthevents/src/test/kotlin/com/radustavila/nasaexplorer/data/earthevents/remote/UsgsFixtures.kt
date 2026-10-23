package com.radustavila.nasaexplorer.data.earthevents.remote

import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.EarthquakeFeedDto
import okhttp3.OkHttpClient
import retrofit2.create

/** Recorded from earthquake.usgs.gov/earthquakes/feed/v1.0/summary on 2026-10-23. */
internal object UsgsFixtures {
    /**
     * `2.5_week.geojson`, trimmed from 368 quakes to four: M5.7 off Sicily (shallow, felt, green),
     * M4.5 Philippines (591 km deep), M2.7 Alaska (123 km) and M7.0 Tibet (significant, orange). Three
     * copies of other quakes were broken on purpose: no geometry, no magnitude, latitude 123.
     */
    const val WEEK = "earthquakes/2.5_week.json"

    /** `significant_month.geojson`, its first four: Tibet again, Vanuatu (tsunami flag), Pakistan, Kamchatka. */
    const val SIGNIFICANT = "earthquakes/significant_month.json"

    const val LAST_MODIFIED = "Fri, 23 Oct 2026 11:17:15 GMT"
}

internal fun usgsService(server: FixtureServer): UsgsService =
    RetrofitFactory(testJson, OkHttpClient()).create(server.baseUrl).create()

internal fun earthquakeFeedFixture(path: String): EarthquakeFeedDto = testJson.decodeFromString(Fixtures.read(path))
