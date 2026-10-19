package com.radustavila.nasaexplorer.data.epic.remote

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.data.epic.RecordedResponses
import java.time.LocalDate
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

/** Parses recorded EPIC responses through the real OkHttp + Retrofit + kotlinx.serialization stack. */
class EpicRemoteDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var remoteDataSource: EpicRemoteDataSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val retrofit = RetrofitFactory(
            json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true },
            callFactory = OkHttpClient(),
        ).create(server.url("/").toString())

        remoteDataSource = RetrofitEpicRemoteDataSource(
            service = retrofit.create(EpicService::class.java),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses the recorded latest-day response`() = runTest {
        server.enqueue(json(RecordedResponses.NATURAL_LATEST))

        val images = (remoteDataSource.fetchLatest(EpicCollection.NATURAL) as DataResult.Success).data

        assertEquals(13, images.size)
        val first = images.first()
        assertEquals("20261016002713", first.identifier)
        assertEquals("epic_1b_20261016002713", first.image)
        assertEquals("2026-10-16 00:22:24", first.date)
        assertEquals(0.65918, first.centroidCoordinates?.lat)
        assertEquals(163.696289, first.centroidCoordinates?.lon)
        assertEquals("/api/natural", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun `requests one day of the chosen collection in ISO form`() = runTest {
        server.enqueue(json("[]"))

        val result = remoteDataSource.fetchDay(EpicCollection.ENHANCED, LocalDate.of(2026, 10, 9))

        // A day without photos is an empty array, not an error; the repository decides what it means.
        assertEquals(DataResult.Success(emptyList<Any>()), result)
        assertEquals("/api/enhanced/date/2026-10-09", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun `parses the recorded archive listing`() = runTest {
        server.enqueue(json(RecordedResponses.NATURAL_ALL))

        val dates = (remoteDataSource.fetchAvailableDates(EpicCollection.NATURAL) as DataResult.Success).data

        assertEquals("2026-10-16", dates.first().date)
        assertEquals("/api/natural/all", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun `maps server errors and malformed bodies to application errors`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(json("""{"unexpected": "object"}"""))

        val serverError = remoteDataSource.fetchLatest(EpicCollection.NATURAL)
        val malformed = remoteDataSource.fetchLatest(EpicCollection.NATURAL)

        assertEquals(503, ((serverError as DataResult.Failure).error as AppError.Http).code)
        assertTrue((malformed as DataResult.Failure).error is AppError.Serialization)
    }

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
