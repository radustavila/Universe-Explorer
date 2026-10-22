package com.radustavila.nasaexplorer.data.forecast.remote

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import java.time.Instant
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import retrofit2.create

/** The request MET Norway's terms ask for, and how each kind of answer comes back. */
class MetNorwayRemoteDataSourceTest {

    @get:Rule
    val server = FixtureServer()

    private val dataSource by lazy { metNorwayRemoteDataSource(server) }
    private val bucharest = ForecastPlace.of(GeoCoordinate(44.42684, 26.10254))

    @Test
    fun `the request names the app, rounds the place and skips the http cache`() = runTest {
        server.enqueueFixture("metnorway/complete_bucharest.json", headers = RECORDED_HEADERS)

        dataSource.fetchForecast(bucharest, ifModifiedSince = null)

        val request = server.takeRequest()
        assertEquals("/weatherapi/locationforecast/2.0/complete", request.requestUrl!!.encodedPath)
        assertEquals("44.43", request.requestUrl!!.queryParameter("lat"))
        assertEquals("26.1", request.requestUrl!!.queryParameter("lon"))
        assertEquals(TEST_USER_AGENT, request.getHeader("User-Agent"))
        assertEquals("no-store", request.getHeader("Cache-Control"))
        assertNull(request.getHeader("If-Modified-Since"))
    }

    @Test
    fun `the recorded forecast comes back with its Last-Modified and Expires`() = runTest {
        server.enqueueFixture("metnorway/complete_bucharest.json", headers = RECORDED_HEADERS)

        val result = dataSource.fetchForecast(bucharest, null) as DataResult.Success
        val download = result.data as ForecastDownload.Updated

        assertEquals(91, download.forecast.properties.timeseries.size)
        assertEquals("2026-10-22T19:17:32Z", download.forecast.properties.meta?.updatedAt)
        assertEquals(LAST_MODIFIED, download.lastModified)
        assertEquals(Instant.parse("2026-10-22T20:42:51Z"), download.expiresAt)
    }

    @Test
    fun `a revalidation sends If-Modified-Since and a 304 means not modified`() = runTest {
        server.enqueueStatus(304, headers = mapOf("Expires" to "Thu, 22 Oct 2026 21:12:51 GMT"))

        val download = (dataSource.fetchForecast(bucharest, LAST_MODIFIED) as DataResult.Success).data

        assertEquals(ForecastDownload.NotModified(Instant.parse("2026-10-22T21:12:51Z")), download)
        assertEquals(LAST_MODIFIED, server.takeRequest().getHeader("If-Modified-Since"))
    }

    @Test
    fun `a ban is a rate limit, a server error an http failure`() = runTest {
        server.enqueueStatus(403)
        server.enqueueStatus(503)

        assertEquals(DataResult.Failure(AppError.RateLimited()), dataSource.fetchForecast(bucharest, null))
        assertEquals(DataResult.Failure(AppError.Http(503, "Server Error")), dataSource.fetchForecast(bucharest, null))
    }

    companion object {
        const val TEST_USER_AGENT = "NasaExplorer/test (+https://github.com/radustavila/Universe-Explorer)"
        const val LAST_MODIFIED = "Thu, 22 Oct 2026 20:12:45 GMT"

        /** As api.met.no sent them with the recorded response. */
        val RECORDED_HEADERS = mapOf("Last-Modified" to LAST_MODIFIED, "Expires" to "Thu, 22 Oct 2026 20:42:51 GMT")
    }
}

internal fun metNorwayRemoteDataSource(server: FixtureServer): MetNorwayRemoteDataSource {
    val client = OkHttpClient.Builder()
        .addInterceptor(MetNorwayUserAgentInterceptor { MetNorwayRemoteDataSourceTest.TEST_USER_AGENT })
        .build()
    val retrofit = RetrofitFactory(
        json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true },
        callFactory = client,
    ).create(server.baseUrl)
    return MetNorwayRemoteDataSource(retrofit.create(), UnconfinedTestDispatcher())
}
