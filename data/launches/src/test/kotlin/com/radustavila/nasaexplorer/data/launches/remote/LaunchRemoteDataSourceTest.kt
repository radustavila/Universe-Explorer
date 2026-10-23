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
    fun `asks for the next 30 upcoming launches in detail in one call`() = runTest {
        server.enqueueFixture(UPCOMING_FIXTURE)

        val result = remoteDataSource.fetchUpcoming()

        assertEquals(5, (result as DataResult.Success).data.size)
        assertEquals(1, server.requestCount)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/2.3.0/launches/upcoming/", url.encodedPath)
        assertEquals("30", url.queryParameter("limit"))
        assertEquals("detailed", url.queryParameter("mode"))
        assertEquals("true", url.queryParameter("hide_recent_previous"))
    }

    @Test
    fun `asks for the last 20 launches in detail in one call`() = runTest {
        server.enqueueFixture(PREVIOUS_DETAILED_FIXTURE)

        val result = remoteDataSource.fetchPrevious()

        assertEquals(4, (result as DataResult.Success).data.size)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/2.3.0/launches/previous/", url.encodedPath)
        assertEquals("20", url.queryParameter("limit"))
        assertEquals("detailed", url.queryParameter("mode"))
    }

    @Test
    fun `re-checks one launch by its id`() = runTest {
        server.enqueueFixture(SCRUBBED_FIXTURE)

        val result = remoteDataSource.fetchLaunch("e2886a96-5ce9-4f45-872f-4e25a00be837")

        assertEquals("e2886a96-5ce9-4f45-872f-4e25a00be837", (result as DataResult.Success).data.id)
        assertEquals(
            "/2.3.0/launches/e2886a96-5ce9-4f45-872f-4e25a00be837/",
            server.takeRequest().requestUrl!!.encodedPath,
        )
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
