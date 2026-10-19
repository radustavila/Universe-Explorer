package com.radustavila.nasaexplorer.core.map.internal

import com.radustavila.nasaexplorer.core.map.TileLoadStatus
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class GibsTileInterceptorTest {

    @get:Rule
    val server = FixtureServer()

    private val outcomes = mutableListOf<TileLoadStatus>()

    private val maskedTiles = mutableListOf<String>()

    /** Stands in for [NoDataMask]: "masks" tiles whose body says they have no-data. */
    private val fakeMask: (ByteArray) -> ByteArray? = { tile ->
        maskedTiles += tile.decodeToString()
        if (tile.decodeToString() == "jpeg with no-data") "png".encodeToByteArray() else null
    }

    private fun client(host: String = server.hostName) = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .addInterceptor(
            GibsTileInterceptor(onOutcome = outcomes::add, maxAgeSeconds = 3_600, host = host, noDataMask = fakeMask),
        )
        .build()

    private fun get(client: OkHttpClient = client(), path: String = "tile.jpg") =
        client.newCall(Request.Builder().url(server.baseUrl + path).build()).execute()

    private fun enqueueTile(body: String, contentType: String = "image/jpeg") {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", contentType).setBody(body))
    }

    @Test
    fun `imagery no-data is masked and the tile becomes a PNG`() {
        enqueueTile("jpeg with no-data")

        get(path = "VIIRS_SNPP_CorrectedReflectance_TrueColor/default/2026-10-18/L9/2/0/2.jpg").use { response ->
            assertEquals("image/png", response.header("Content-Type"))
            assertEquals("png", response.body.string())
        }
    }

    @Test
    fun `an imagery tile without no-data keeps its JPEG`() {
        enqueueTile("jpeg")

        get(path = "VIIRS_SNPP_CorrectedReflectance_TrueColor/default/2026-10-18/L9/4/5/8.jpg").use { response ->
            assertEquals("image/jpeg", response.header("Content-Type"))
            assertEquals("jpeg", response.body.string())
        }
        assertEquals(listOf("jpeg"), maskedTiles)
    }

    @Test
    fun `the Blue Marble base and PNG layers are never masked`() {
        enqueueTile("jpeg with no-data")
        enqueueTile("jpeg with no-data", contentType = "image/png")

        get(path = "BlueMarble_ShadedRelief_Bathymetry/default/L8/1/0/0.jpeg").use { response ->
            assertEquals("jpeg with no-data", response.body.string())
        }
        get(path = "VIIRS_Black_Marble/default/2016-01-01/L8/1/0/0.png").use { response ->
            assertEquals("jpeg with no-data", response.body.string())
        }
        assertEquals(emptyList<String>(), maskedTiles)
    }

    @Test
    fun `GIBS no-store headers are replaced so the ambient cache can serve tiles offline`() {
        server.enqueueStatus(
            code = 200,
            headers = mapOf(
                "Cache-Control" to "max-age=0, no-store, no-cache, must-revalidate",
                "Pragma" to "no-cache",
                "Expires" to "Thu, 1 Jan 1970 00:00:00 GMT",
                "ETag" to "abc",
            ),
        )

        get().use { response ->
            assertEquals("max-age=3600", response.header("Cache-Control"))
            assertNull(response.header("Pragma"))
            assertNull(response.header("Expires"))
            assertEquals("abc", response.header("ETag"))
        }
        assertEquals(listOf(TileLoadStatus.Loaded), outcomes)
    }

    @Test
    fun `a revalidated tile counts as loaded and gets a fresh max-age`() {
        server.enqueueStatus(code = 304, headers = mapOf("Cache-Control" to "max-age=0, must-revalidate"))

        get().use { response -> assertEquals("max-age=3600", response.header("Cache-Control")) }
        assertEquals(listOf(TileLoadStatus.Loaded), outcomes)
    }

    @Test
    fun `a 404 means GIBS has no imagery for that date`() {
        server.enqueueStatus(code = 404, headers = mapOf("Cache-Control" to "no-store"))

        get().use { response -> assertEquals("no-store", response.header("Cache-Control")) }
        assertEquals(listOf(TileLoadStatus.ImageryUnavailable), outcomes)
    }

    @Test
    fun `server errors and dropped connections are network errors`() {
        server.enqueueStatus(code = 503)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        get().close()
        assertThrows(IOException::class.java) { get() }

        assertEquals(listOf(TileLoadStatus.NetworkError, TileLoadStatus.NetworkError), outcomes)
    }

    @Test
    fun `other hosts pass through untouched`() {
        server.enqueueStatus(code = 200, headers = mapOf("Cache-Control" to "no-store"))

        get(client(host = "gibs.earthdata.nasa.gov")).use { response ->
            assertEquals("no-store", response.header("Cache-Control"))
        }
        assertEquals(emptyList<TileLoadStatus>(), outcomes)
    }
}
