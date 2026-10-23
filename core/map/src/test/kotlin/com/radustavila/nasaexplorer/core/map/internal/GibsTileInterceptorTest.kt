package com.radustavila.nasaexplorer.core.map.internal

import com.radustavila.nasaexplorer.core.map.TileLoadStatus
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GibsTileInterceptorTest {

    @get:Rule
    val server = FixtureServer()

    private val outcomes = mutableListOf<TileLoadStatus>()
    private val reportedUrls = mutableListOf<String>()
    private val keyedTiles = mutableListOf<ByteArray>()
    private var keyerResult: ByteArray? = null

    private fun client(host: String = server.hostName) = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .addInterceptor(
            GibsTileInterceptor(
                onOutcome = { url, outcome ->
                    reportedUrls += url
                    outcomes += outcome
                },
                maxAgeSeconds = 3_600,
                host = host,
                keyer = { tile -> keyerResult.also { keyedTiles += tile } },
            ),
        )
        .build()

    private fun get(client: OkHttpClient = client(), path: String = "tile.jpg") =
        client.newCall(Request.Builder().url(server.baseUrl + path).build()).execute()

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
    fun `each outcome comes with the tile's URL`() {
        server.enqueueStatus(code = 200)

        get(path = "layer/default/2026-10-20/set/1/0/0.jpg").close()

        assertEquals(listOf(server.baseUrl + "layer/default/2026-10-20/set/1/0/0.jpg"), reportedUrls)
    }

    @Test
    fun `daily mosaic tiles with no-data areas come back keyed as PNG`() {
        server.enqueue(MockResponse().setBody(Buffer().write(JPEG)).setHeader("Content-Type", "image/jpeg"))
        keyerResult = PNG

        get(path = MOSAIC_TILE).use { response ->
            assertEquals("image/png", response.header("Content-Type"))
            assertArrayEquals(PNG, response.body.bytes())
            assertEquals("max-age=3600", response.header("Cache-Control"))
        }
        assertArrayEquals(JPEG, keyedTiles.single())
    }

    @Test
    fun `mosaic tiles without no-data areas pass through as they came`() {
        server.enqueue(MockResponse().setBody(Buffer().write(JPEG)).setHeader("Content-Type", "image/jpeg"))
        keyerResult = null

        get(path = MOSAIC_TILE).use { response ->
            assertEquals("image/jpeg", response.header("Content-Type"))
            assertArrayEquals(JPEG, response.body.bytes())
        }
    }

    @Test
    fun `other layers and failed tiles are never keyed`() {
        server.enqueue(MockResponse().setBody(Buffer().write(JPEG)))
        server.enqueue(MockResponse().setBody(Buffer().write(PNG)).setHeader("Content-Type", "image/png"))
        server.enqueueStatus(code = 404)

        get(path = "BlueMarble_ShadedRelief_Bathymetry/default/GoogleMapsCompatible_Level8/1/0/0.jpeg").close()
        get(path = "VIIRS_Black_Marble/default/2016-01-01/GoogleMapsCompatible_Level8/1/0/0.png").use { response ->
            assertArrayEquals(PNG, response.body.bytes())
        }
        get(path = MOSAIC_TILE).close()

        assertTrue(keyedTiles.isEmpty())
    }

    @Test
    fun `other hosts pass through untouched`() {
        server.enqueueStatus(code = 200, headers = mapOf("Cache-Control" to "no-store"))

        get(client(host = "gibs.earthdata.nasa.gov")).use { response ->
            assertEquals("no-store", response.header("Cache-Control"))
        }
        assertEquals(emptyList<TileLoadStatus>(), outcomes)
    }

    private companion object {
        const val MOSAIC_TILE =
            "VIIRS_SNPP_CorrectedReflectance_TrueColor/default/2026-10-20/GoogleMapsCompatible_Level9/1/0/0.jpg"
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
        val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    }
}
