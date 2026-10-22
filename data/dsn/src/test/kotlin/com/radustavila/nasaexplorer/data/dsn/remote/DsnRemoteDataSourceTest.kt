package com.radustavila.nasaexplorer.data.dsn.remote

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.create

/** Both feeds and Horizons against recorded responses: request shape, parsing and failures. */
@RunWith(RobolectricTestRunner::class)
class DsnRemoteDataSourceTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-22T16:13:20Z"))
    private val dataSource by lazy { remoteDataSource(server, clock) }

    @Test
    fun `live request skips the http cache and passes the 5-second bucket`() = runTest {
        server.enqueueXml(Fixtures.read("dsn/dsn.xml"))

        val feed = (dataSource.fetchLive() as DataResult.Success).data

        assertEquals(3, feed.stations.size)
        val request = server.takeRequest()
        assertEquals("/data/dsn.xml", request.requestUrl!!.encodedPath)
        assertEquals((clock.currentInstant.toEpochMilli() / 5_000).toString(), request.requestUrl!!.queryParameter("r"))
        assertEquals("no-store", request.getHeader("Cache-Control"))
    }

    @Test
    fun `a broken feed is a serialization failure, a server error an http one`() = runTest {
        server.enqueueXml("<dsn><dish")
        server.enqueueStatus(503)

        assertTrue((dataSource.fetchLive() as DataResult.Failure).error is AppError.Serialization)
        assertEquals(AppError.Http(503, "Server Error"), (dataSource.fetchLive() as DataResult.Failure).error)
    }

    @Test
    fun `config parses from its own path`() = runTest {
        server.enqueueXml(Fixtures.read("dsn/config.xml"))

        val config = (dataSource.fetchConfig() as DataResult.Success).data

        assertEquals("/config.xml", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals(3, config.sites.size)
    }

    @Test
    fun `horizons is asked for one day of range from Earth's centre`() = runTest {
        server.enqueueFixture("dsn/horizons_31.json")

        val range = (dataSource.fetchRange(-31, LocalDate.of(2026, 10, 22)) as DataResult.Success).data

        assertEquals(172.652953067762, range.deltaAu, 1e-12)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/api/horizons.api", url.encodedPath)
        assertEquals("json", url.queryParameter("format"))
        assertEquals("'-31'", url.queryParameter("COMMAND"))
        assertEquals("'OBSERVER'", url.queryParameter("EPHEM_TYPE"))
        assertEquals("'500@399'", url.queryParameter("CENTER"))
        assertEquals("'20'", url.queryParameter("QUANTITIES"))
        assertEquals("'2026-10-22'", url.queryParameter("START_TIME"))
        assertEquals("'2026-10-23'", url.queryParameter("STOP_TIME"))
        assertEquals("'1d'", url.queryParameter("STEP_SIZE"))
    }

    @Test
    fun `an unknown horizons id is a failure with horizons' message`() = runTest {
        server.enqueueFixture("dsn/horizons_9999.json")

        val result = dataSource.fetchRange(-9999, LocalDate.of(2026, 10, 22))

        assertEquals(DataResult.Failure(AppError.Serialization("No such record, positive values only")), result)
    }
}

internal fun FixtureServer.enqueueXml(body: String) {
    enqueue(MockResponse().setHeader("Content-Type", "text/xml").setBody(body))
}

internal fun remoteDataSource(server: FixtureServer, clock: FakeTimeProvider): RetrofitDsnRemoteDataSource {
    val retrofit = RetrofitFactory(
        json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true },
        callFactory = OkHttpClient(),
    ).create(server.baseUrl)
    return RetrofitDsnRemoteDataSource(retrofit.create(), retrofit.create(), clock, UnconfinedTestDispatcher())
}
