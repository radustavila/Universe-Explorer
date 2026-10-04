package com.radustavila.nasaexplorer.data.nasa.remote

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import java.net.HttpURLConnection
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Network contract tests against a real HTTP stack (OkHttp + Retrofit + kotlinx.serialization)
 * driven by MockWebServer, covering every failure shape the app must survive.
 */
class ApodRemoteDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var remoteDataSource: ApodRemoteDataSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val client = OkHttpClient.Builder()
            .readTimeout(READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .callTimeout(READ_TIMEOUT_MILLIS * 2, TimeUnit.MILLISECONDS)
            .build()

        val retrofit = RetrofitFactory(
            json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true },
            callFactory = client,
        ).create(server.url("/").toString())

        remoteDataSource = RetrofitApodRemoteDataSource(
            service = retrofit.create(NasaApodService::class.java),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses a successful date-range response`() = runTest {
        server.enqueue(jsonResponse(RANGE_JSON))

        val result = remoteDataSource.fetchRange(START_DATE, END_DATE)

        val entries = (result as DataResult.Success).data
        assertEquals(2, entries.size)
        assertEquals("2026-09-10", entries.first().date)
        assertEquals("video", entries.first().mediaType)
        assertEquals("https://www.youtube.com/embed/abc?rel=0", entries.first().url)
        assertEquals("https://assets.science.nasa.gov/ngc1300.jpg", entries.last().url)
    }

    @Test
    fun `sends the date range in APOD's yyMMdd form`() = runTest {
        server.enqueue(jsonResponse(RANGE_JSON))

        remoteDataSource.fetchRange(START_DATE, END_DATE)

        val requestUrl = server.takeRequest().requestUrl!!
        assertEquals("/wp-json/wp/v2/apod-basic", requestUrl.encodedPath)
        assertEquals("260909", requestUrl.queryParameter("date_from"))
        assertEquals("260910", requestUrl.queryParameter("date_to"))
        assertEquals("1", requestUrl.queryParameter("page"))
    }

    @Test
    fun `follows pages until one comes back short`() = runTest {
        val fullPage = (1..NasaApodService.MAX_PAGE_SIZE).joinToString(prefix = "[", postfix = "]") { day ->
            """{"date": "2026-08-${"%02d".format(day)}", "media_type": "image"}"""
        }
        server.enqueue(jsonResponse(fullPage))
        server.enqueue(jsonResponse(RANGE_JSON))

        val result = remoteDataSource.fetchRange(START_DATE, END_DATE)

        assertEquals(NasaApodService.MAX_PAGE_SIZE + 2, (result as DataResult.Success).data.size)
        assertEquals("1", server.takeRequest().requestUrl!!.queryParameter("page"))
        assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("page"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `maps server errors to http failures`() = runTest {
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR))

        val result = remoteDataSource.fetchRange(START_DATE, END_DATE)

        assertEquals(AppError.Http(code = 500, message = "Server Error"), (result as DataResult.Failure).error)
    }

    @Test
    fun `maps quota exhaustion to a rate limit failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))

        val result = remoteDataSource.fetchRange(START_DATE, END_DATE)

        assertEquals(AppError.RateLimited(retryAfterSeconds = 120), (result as DataResult.Failure).error)
    }

    @Test
    fun `maps malformed payloads to serialization failures`() = runTest {
        server.enqueue(jsonResponse("""{"unexpected": "object instead of array"}"""))

        val result = remoteDataSource.fetchRange(START_DATE, END_DATE)

        assertTrue((result as DataResult.Failure).error is AppError.Serialization)
    }

    @Test
    fun `maps a stalled connection to a timeout`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(RANGE_JSON)
                .setBodyDelay(READ_TIMEOUT_MILLIS * 4, TimeUnit.MILLISECONDS),
        )

        val result = remoteDataSource.fetchRange(START_DATE, END_DATE)

        assertTrue((result as DataResult.Failure).error is AppError.Timeout)
    }

    @Test
    fun `maps a dropped connection to a missing connection`() = runTest {
        server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.DISCONNECT_AT_START })

        val result = remoteDataSource.fetchRange(START_DATE, END_DATE)

        assertEquals(AppError.NoConnection, (result as DataResult.Failure).error)
    }

    @Test
    fun `an empty window is a successful empty list`() = runTest {
        server.enqueue(jsonResponse("[]"))

        val result = remoteDataSource.fetchRange(START_DATE, END_DATE)

        assertEquals(emptyList<Any>(), (result as DataResult.Success).data)
    }

    @Test
    fun `fetches a single entry by date`() = runTest {
        server.enqueue(jsonResponse(SINGLE_JSON))

        val result = remoteDataSource.fetchSingle(END_DATE)

        assertEquals("2026-09-10", (result as DataResult.Success).data.date)
        assertEquals("/wp-json/wp/v2/apod-basic/260910", server.takeRequest().requestUrl!!.encodedPath)
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(HttpURLConnection.HTTP_OK)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        val START_DATE: LocalDate = LocalDate.of(2026, 9, 9)
        val END_DATE: LocalDate = LocalDate.of(2026, 9, 10)
        const val READ_TIMEOUT_MILLIS = 300L

        val RANGE_JSON = """
            [
              {
                "date": "2026-09-10",
                "post_id": 1,
                "title": "Flight Through Orion",
                "media_type": "video",
                "explanation": "<strong>Explanation: </strong>A visualization.",
                "hdurl": "https://assets.science.nasa.gov/orion_snapshot.png",
                "basic_html": "<center><iframe src=\"//www.youtube.com/embed/abc?rel=0\"></iframe></center>"
              },
              {
                "date": "2026-09-09",
                "post_id": 2,
                "title": "Spiral Galaxy NGC 1300",
                "media_type": "image",
                "explanation": "A barred spiral galaxy.",
                "hdurl": "https://assets.science.nasa.gov/ngc1300.jpg?w=4000",
                "basic_html": "<a href=\"https://assets.science.nasa.gov/ngc1300.jpg\"><IMG SRC=\"https://assets.science.nasa.gov/ngc1300.jpg\"></a>"
              }
            ]
        """.trimIndent()

        val SINGLE_JSON = """
            {
              "date": "2026-09-10",
              "title": "Spiral Galaxy NGC 1300",
              "explanation": "A barred spiral galaxy.",
              "media_type": "image",
              "basic_html": "<img src=\"https://assets.science.nasa.gov/ngc1300.jpg\">"
            }
        """.trimIndent()
    }
}
