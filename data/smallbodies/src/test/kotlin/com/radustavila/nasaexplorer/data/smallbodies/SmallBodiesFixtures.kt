package com.radustavila.nasaexplorer.data.smallbodies

import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.smallbodies.remote.JplSsdService
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.create

/** Responses recorded from JPL's SSD APIs on 2026-10-19 (the Sentry list trimmed to six objects). */
internal object SmallBodiesFixtures {
    const val CAD = "smallbodies/cad.json"
    const val SENTRY = "smallbodies/sentry.json"
    const val FIREBALLS = "smallbodies/fireball.json"
    const val SBDB_2020_FN3 = "smallbodies/sbdb_2020_FN3.json"
    const val SBDB_APOPHIS = "smallbodies/sbdb_apophis.json"
    const val SBDB_NOT_FOUND = "smallbodies/sbdb_not_found.json"
}

/** Same settings as `:core:network`'s production Json. */
internal val testJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

internal fun jplSsdService(server: FixtureServer): JplSsdService =
    RetrofitFactory(testJson, OkHttpClient()).create(server.baseUrl).create()
