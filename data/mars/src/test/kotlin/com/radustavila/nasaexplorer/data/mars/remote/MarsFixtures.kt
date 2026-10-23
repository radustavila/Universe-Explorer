package com.radustavila.nasaexplorer.data.mars.remote

import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import com.radustavila.nasaexplorer.data.mars.remote.dto.CuriosityPageDto
import com.radustavila.nasaexplorer.data.mars.remote.dto.PerseverancePageDto
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.create

/**
 * Recorded mars.nasa.gov responses, trimmed to a few items each.
 *
 * Both were recorded on 2026-10-19: Curiosity's at sol 5048, Perseverance's (after its feed came
 * back from a stretch of HTTP 403s) at sols 2013 and 2012, from the first two pages of the feed.
 * The Perseverance feed no longer lists thumbnails, and gives UTC times without an offset.
 */
internal object MarsFixtures {
    /** 11 items of sol 5048: MAHLI, Navcam, Hazcams, ChemCam; 3 of them thumbnails. */
    const val CURIOSITY_LATEST = "mars/curiosity_latest.json"

    /** What the catalogue answers to a query with no match. */
    const val CURIOSITY_EMPTY = "mars/curiosity_empty.json"

    /** 6 items of sols 2013 and 2012: Navcam, Mastcam-Z, SuperCam, SkyCam. */
    const val PERSEVERANCE_LATEST = "mars/perseverance_latest.json"
}

/** The same JSON configuration the app's network module provides. */
internal val testJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

internal fun marsService(server: FixtureServer): MarsRawImagesService =
    RetrofitFactory(testJson, OkHttpClient()).create(server.baseUrl).create()

internal fun curiosityFixture(path: String = MarsFixtures.CURIOSITY_LATEST): CuriosityPageDto =
    testJson.decodeFromString(Fixtures.read(path))

internal fun perseveranceFixture(path: String = MarsFixtures.PERSEVERANCE_LATEST): PerseverancePageDto =
    testJson.decodeFromString(Fixtures.read(path))
