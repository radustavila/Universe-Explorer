package com.radustavila.nasaexplorer.core.map.internal

import com.radustavila.nasaexplorer.core.map.TileLoadStatus
import com.radustavila.nasaexplorer.core.map.gibs.CORRECTED_REFLECTANCE
import com.radustavila.nasaexplorer.core.map.gibs.GIBS_HOST
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Sits on the OkHttp client MapLibre fetches tiles with, for three reasons:
 *
 * 1. GIBS answers every tile with `Cache-Control: no-store, must-revalidate` and an expired
 *    `Expires`. MapLibre's ambient cache refuses stale must-revalidate tiles while offline, so the
 *    map would go blank without a connection. Tiles for a given layer and date do not change once
 *    published, so the headers are rewritten to a plain `max-age`; MapLibre still revalidates with
 *    the ETag once that passes.
 * 2. Daily mosaics ([com.radustavila.nasaexplorer.core.map.gibs.GibsLayer.hasNoDataGaps]) come as
 *    JPEGs with opaque black where no satellite passed; [keyer] turns that transparent, and the
 *    ambient cache keeps the keyed tile.
 * 3. It reports each tile request's URL and outcome, which [SpaceMap] turns into [TileLoadStatus].
 *
 * Requests to other hosts pass through untouched.
 */
internal class GibsTileInterceptor(
    private val onOutcome: (url: String, outcome: TileLoadStatus) -> Unit,
    private val maxAgeSeconds: Long = TimeUnit.DAYS.toSeconds(1),
    private val host: String = GIBS_HOST,
    private val keyer: NoDataKeyer = BitmapNoDataKeyer,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != host) return chain.proceed(request)
        val url = request.url.toString()

        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            // MapLibre cancels requests for tiles that scrolled away; that is not a failure.
            if (!chain.call().isCanceled()) onOutcome(url, TileLoadStatus.NetworkError)
            throw e
        }

        val usable = response.isSuccessful || response.code == HttpURLConnection.HTTP_NOT_MODIFIED
        onOutcome(
            url,
            when {
                usable -> TileLoadStatus.Loaded
                response.code in CLIENT_ERRORS -> TileLoadStatus.ImageryUnavailable
                else -> TileLoadStatus.NetworkError
            },
        )
        if (!usable) return response

        val cacheable = response.newBuilder()
            .removeHeader("Pragma")
            .removeHeader("Expires")
            .header("Cache-Control", "max-age=$maxAgeSeconds")
        if (response.code == HttpURLConnection.HTTP_OK && hasNoDataGaps(request.url.encodedPath)) {
            val contentType = response.body.contentType()
            val tile = response.body.bytes()
            val keyed = keyer.key(tile)
            if (keyed == null) {
                cacheable.body(tile.toResponseBody(contentType))
            } else {
                cacheable.body(keyed.toResponseBody(PNG)).header("Content-Type", PNG.toString())
            }
            cacheable.removeHeader("Content-Length")
        }
        return cacheable.build()
    }

    private companion object {
        val CLIENT_ERRORS = 400..499
        val PNG = "image/png".toMediaType()

        /** `/wmts/.../VIIRS_SNPP_CorrectedReflectance_TrueColor/default/<date>/<set>/z/y/x.jpg`. */
        fun hasNoDataGaps(path: String): Boolean =
            CORRECTED_REFLECTANCE in path && (path.endsWith(".jpg") || path.endsWith(".jpeg"))
    }
}
