package com.radustavila.nasaexplorer.data.spaceweather

import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.spaceweather.remote.DonkiService
import com.radustavila.nasaexplorer.data.spaceweather.remote.SwpcService
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.create

/** Responses recorded from NOAA SWPC and DONKI on 2026-10-19 (the aurora grid trimmed to three longitudes). */
internal object SpaceWeatherFixtures {
    const val KP_FORECAST = "spaceweather/kp_forecast.json"
    const val KP_ONE_MINUTE = "spaceweather/kp_1m.json"
    const val AURORA = "spaceweather/ovation_aurora_latest.json"
    const val FLARES = "spaceweather/flares.json"
    const val CMES = "spaceweather/cmes.json"
}

/** Same settings as `:core:network`'s production Json. */
internal val testJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

internal fun swpcService(server: FixtureServer): SwpcService =
    RetrofitFactory(testJson, OkHttpClient()).create(server.baseUrl).create()

internal fun donkiService(server: FixtureServer): DonkiService =
    RetrofitFactory(testJson, OkHttpClient()).create(server.baseUrl).create()
