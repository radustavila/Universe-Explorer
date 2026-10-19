package com.radustavila.nasaexplorer.data.nasaimages.remote

import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import com.radustavila.nasaexplorer.data.nasaimages.remote.dto.AssetResponseDto
import com.radustavila.nasaexplorer.data.nasaimages.remote.dto.SearchResponseDto
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.create

/** Recorded from images-api.nasa.gov on 2026-10-19. */
internal object NasaImagesFixtures {
    /** `q=galaxy&keywords=James Webb Space Telescope&media_type=image&page_size=5`: 110 hits. */
    const val WEBB_GALAXIES = "nasaimages/search_webb_galaxies.json"

    /** `q=webb&media_type=image,video,audio&page=2&page_size=5`: has prev and next links. */
    const val MIXED_PAGE_2 = "nasaimages/search_mixed_page2.json"
    const val VIDEOS = "nasaimages/search_video.json"
    const val AUDIO = "nasaimages/search_audio.json"
    const val EMPTY = "nasaimages/search_empty.json"
    const val BY_ID = "nasaimages/search_by_id.json"
    const val VIDEO_ASSETS = "nasaimages/asset_video.json"
    const val AUDIO_ASSETS = "nasaimages/asset_audio.json"
    const val IMAGE_ASSETS = "nasaimages/asset_image.json"
}

/** The same JSON configuration the app's network module provides. */
internal val testJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

internal fun nasaImagesService(server: FixtureServer): NasaImagesService =
    RetrofitFactory(testJson, OkHttpClient()).create(server.baseUrl).create()

internal fun searchFixture(path: String): SearchResponseDto =
    testJson.decodeFromString(Fixtures.read(path))

internal fun assetFixture(path: String): AssetResponseDto =
    testJson.decodeFromString(Fixtures.read(path))
