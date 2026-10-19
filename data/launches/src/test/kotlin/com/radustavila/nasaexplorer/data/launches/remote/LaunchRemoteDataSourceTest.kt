package com.radustavila.nasaexplorer.data.launches.remote

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.create

class LaunchRemoteDataSourceTest {

    @get:Rule
    val server = FixtureServer()

    private lateinit var remoteDataSource: LaunchRemoteDataSource

    @Before
    fun setUp() {
        remoteDataSource = RetrofitLaunchRemoteDataSource(
            service = launchLibraryService(server),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun `asks for a few upcoming launches with their livestreams in one call`() = runTest {
        server.enqueueFixture(UPCOMING_FIXTURE)

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
        server.enqueueJson(
            body = """{"detail":"Request was throttled. Expected available in 1834 seconds."}""",
            code = 429,
            headers = mapOf("Retry-After" to "1834"),
        )

        val result = remoteDataSource.fetchUpcoming()

        assertEquals(DataResult.Failure(AppError.RateLimited(retryAfterSeconds = 1834)), result)
    }

    @Test
    fun `a malformed body maps to a serialization error`() = runTest {
        server.enqueueJson("""{"results": "nope"}""")

        val result = remoteDataSource.fetchUpcoming()

        assertTrue((result as DataResult.Failure).error is AppError.Serialization)
    }
}

internal fun launchLibraryService(server: FixtureServer): LaunchLibraryService =
    RetrofitFactory(
        json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true },
        callFactory = OkHttpClient(),
    ).create(server.baseUrl).create()
