package com.radustavila.nasaexplorer.core.map.internal

import com.radustavila.nasaexplorer.core.map.TileLoadStatus
import com.radustavila.nasaexplorer.core.map.gibs.GIBS_HOST
import com.radustavila.nasaexplorer.core.map.gibs.GibsLayer
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Sits on the OkHttp client MapLibre fetches tiles with, for two reasons:
 *
 * 1. GIBS answers every tile with `Cache-Control: no-store, must-revalidate` and an expired
 *    `Expires`. MapLibre's ambient cache refuses stale must-revalidate tiles while offline, so the
 *    map would go blank without a connection. Tiles for a given layer and date do not change once
 *    published, so the headers are rewritten to a plain `max-age`; MapLibre still revalidates with
 *    the ETag once that passes.
 * 2. It reports each tile request's outcome, which [SpaceMap] turns into [TileLoadStatus].
 * 3. Daily imagery tiles paint "no data" opaque black; [NoDataMask] makes it transparent so the
 *    Blue Marble base shows through (the base itself is left alone).
 *
 * Requests to other hosts pass through untouched.
 */
internal class GibsTileInterceptor(
    private val onOutcome: (TileLoadStatus) -> Unit,
    private val maxAgeSeconds: Long = TimeUnit.DAYS.toSeconds(1),
    private val host: String = GIBS_HOST,
    private val noDataMask: (ByteArray) -> ByteArray? = NoDataMask::transcode,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != host) return chain.proceed(request)

        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            // MapLibre cancels requests for tiles that scrolled away; that is not a failure.
            if (!chain.call().isCanceled()) onOutcome(TileLoadStatus.NetworkError)
            throw e
        }

        val usable = response.isSuccessful || response.code == HttpURLConnection.HTTP_NOT_MODIFIED
        onOutcome(
            when {
                usable -> TileLoadStatus.Loaded
                response.code in CLIENT_ERRORS -> TileLoadStatus.ImageryUnavailable
                else -> TileLoadStatus.NetworkError
            },
        )
        if (!usable) return response

        return response.newBuilder()
            .removeHeader("Pragma")
            .removeHeader("Expires")
            .header("Cache-Control", "max-age=$maxAgeSeconds")
            .apply { if (response.code == HttpURLConnection.HTTP_OK && isImageryJpeg(request.url)) maskNoData(response) }
            .build()
    }

    private fun Response.Builder.maskNoData(response: Response) {
        val body = response.body
        val tile = body.bytes()
        val masked = noDataMask(tile)
        if (masked == null) {
            body(tile.toResponseBody(body.contentType()))
        } else {
            body(masked.toResponseBody(PNG))
            header("Content-Type", PNG.toString())
            removeHeader("Content-Length")
            // The ETag still names the JPEG; MapLibre sends it back and a 304 keeps this PNG.
        }
    }

    private fun isImageryJpeg(url: HttpUrl): Boolean {
        val path = url.encodedPath
        return (path.endsWith(".jpg") || path.endsWith(".jpeg")) && "/${GibsLayer.BlueMarble.id}/" !in path
    }

    private companion object {
        val CLIENT_ERRORS = 400..499
        val PNG = "image/png".toMediaType()
    }
}
