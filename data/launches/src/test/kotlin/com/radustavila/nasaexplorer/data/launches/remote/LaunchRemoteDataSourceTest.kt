package com.radustavila.nasaexplorer.data.launches.remote

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.data.launches.LaunchFixtures
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LaunchRemoteDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var remoteDataSource: LaunchRemoteDataSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        remoteDataSource = RetrofitLaunchRemoteDataSource(
            service = launchLibraryService(server),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `asks for a few upcoming launches with their livestreams in one call`() = runTest {
        server.enqueue(MockResponse().setBody(LaunchFixtures.UPCOMING_DETAILED))

        val result = remoteDataSource.fetchUpcoming()

        assertEquals(5, (result as DataResult.Success).data.size)
        assertEquals(1, server.requestCount)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/2.3.0/launches/upcoming/", url.encodedPath)
        assertEquals("5", url.queryParameter("limit"))
        assertEquals("detailed", url.queryParameter("mode"))
        assertEquals("true", url.queryParameter("hide_recent_previous"))
    }

    @Test
    fun `a 429 maps to RateLimited with its Retry-After`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "1834")
                .setBody("""{"detail":"Request was throttled. Expected available in 1834 seconds."}"""),
        )

        val result = remoteDataSource.fetchUpcoming()

        assertEquals(DataResult.Failure(AppError.RateLimited(retryAfterSeconds = 1834)), result)
    }

    @Test
    fun `a malformed body maps to a serialization error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"results": "nope"}"""))

        val result = remoteDataSource.fetchUpcoming()

        assertTrue((result as DataResult.Failure).error is AppError.Serialization)
    }
}

internal fun launchLibraryService(server: MockWebServer): LaunchLibraryService =
    RetrofitFactory(
        json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true },
        callFactory = OkHttpClient(),
    ).create(server.url("/").toString()).create(LaunchLibraryService::class.java)
