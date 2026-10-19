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

    private fun client(host: String = server.hostName) = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .addInterceptor(GibsTileInterceptor(onOutcome = outcomes::add, maxAgeSeconds = 3_600, host = host))
        .build()

    private fun get(client: OkHttpClient = client()) =
        client.newCall(Request.Builder().url(server.baseUrl + "tile.jpg").build()).execute()

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
