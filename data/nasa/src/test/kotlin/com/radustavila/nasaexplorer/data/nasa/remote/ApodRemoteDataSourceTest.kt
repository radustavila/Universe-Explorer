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
        assertEquals("2026-09-09", entries.first().date)
        assertEquals("video", entries.last().mediaType)
    }

    @Test
    fun `sends the date range as query parameters`() = runTest {
        server.enqueue(jsonResponse(RANGE_JSON))

        remoteDataSource.fetchRange(START_DATE, END_DATE)

        val requestUrl = server.takeRequest().requestUrl!!
        assertEquals("/planetary/apod", requestUrl.encodedPath)
        assertEquals("2026-09-09", requestUrl.queryParameter("start_date"))
        assertEquals("2026-09-10", requestUrl.queryParameter("end_date"))
        assertEquals("true", requestUrl.queryParameter("thumbs"))
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
        assertEquals("2026-09-10", server.takeRequest().requestUrl!!.queryParameter("date"))
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
                "date": "2026-09-09",
                "title": "Spiral Galaxy NGC 1300",
                "explanation": "A barred spiral galaxy.",
                "media_type": "image",
                "url": "https://apod.nasa.gov/apod/image/ngc1300.jpg",
                "hdurl": "https://apod.nasa.gov/apod/image/ngc1300_hd.jpg",
                "service_version": "v1"
              },
              {
                "date": "2026-09-10",
                "title": "Flight Through Orion",
                "explanation": "A visualization.",
                "media_type": "video",
                "url": "https://www.youtube.com/embed/abc",
                "thumbnail_url": "https://img.youtube.com/vi/abc/0.jpg",
                "copyright": "Jane Doe"
              }
            ]
        """.trimIndent()

        val SINGLE_JSON = """
            {
              "date": "2026-09-10",
              "title": "Spiral Galaxy NGC 1300",
              "explanation": "A barred spiral galaxy.",
              "media_type": "image",
              "url": "https://apod.nasa.gov/apod/image/ngc1300.jpg"
            }
        """.trimIndent()
    }
}
