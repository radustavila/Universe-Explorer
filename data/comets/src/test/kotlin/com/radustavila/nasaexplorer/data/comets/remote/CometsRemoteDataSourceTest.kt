package com.radustavila.nasaexplorer.data.comets.remote

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.create

/** SBDB and Horizons against recorded responses: request shape, parsing and failures. */
@RunWith(RobolectricTestRunner::class)
class CometsRemoteDataSourceTest {

    @get:Rule
    val server = FixtureServer()

    private val dataSource by lazy { remoteDataSource(server) }

    @Test
    fun `sbdb is asked for comets with the filter and fields`() = runTest {
        server.enqueueFixture("comets/sbdb_comets.json")

        val candidates = (dataSource.fetchCandidates(Instant.parse("2026-10-23T00:00:00Z")) as DataResult.Success).data

        assertEquals(5, candidates.size)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/sbdb_query.api", url.encodedPath)
        assertEquals("c", url.queryParameter("sb-kind"))
        assertEquals("full_name,pdes,M1,K1,q,e,tp", url.queryParameter("fields"))
        assertEquals(
            """{"AND":["M1|LT|11","q|LT|2.5","tp|RG|2461136.5|2461686.5"]}""",
            url.queryParameter("sb-cdata"),
        )
    }

    @Test
    fun `horizons is asked for a geocentric daily table of the comet`() = runTest {
        server.enqueueFixture("comets/horizons_2026_A2.json")

        val rows = dataSource.fetchEphemeris("2026 A2", LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 22))

        assertEquals(121, (rows as DataResult.Success).data.size)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/api/horizons.api", url.encodedPath)
        assertEquals("'DES=2026 A2;CAP;NOFRAG'", url.queryParameter("COMMAND"))
        assertEquals("'OBSERVER'", url.queryParameter("EPHEM_TYPE"))
        assertEquals("'500@399'", url.queryParameter("CENTER"))
        assertEquals("'1,9,19,20'", url.queryParameter("QUANTITIES"))
        assertEquals("'DEG'", url.queryParameter("ANG_FORMAT"))
        assertEquals("'YES'", url.queryParameter("CSV_FORMAT"))
        assertEquals("'2026-08-24'", url.queryParameter("START_TIME"))
        assertEquals("'2026-12-22'", url.queryParameter("STOP_TIME"))
        assertEquals("'1d'", url.queryParameter("STEP_SIZE"))
    }

    @Test
    fun `no match, a horizons error and a server error are failures`() = runTest {
        server.enqueueFixture("comets/horizons_not_found.json")
        server.enqueueJson("""{"error":"Cannot read COMMAND"}""")
        server.enqueueStatus(503)
        val from = LocalDate.of(2026, 8, 24)
        val until = LocalDate.of(2026, 12, 22)

        val notFound = dataSource.fetchEphemeris("2099 Z9", from, until) as DataResult.Failure
        assertTrue(notFound.error is AppError.Serialization)
        assertEquals(
            DataResult.Failure(AppError.Serialization("Cannot read COMMAND")),
            dataSource.fetchEphemeris("2026 A2", from, until),
        )
        val serverError = dataSource.fetchCandidates(Instant.EPOCH) as DataResult.Failure
        assertEquals(AppError.Http(503, "Server Error"), serverError.error)
    }
}

internal fun remoteDataSource(server: FixtureServer): RetrofitCometsRemoteDataSource {
    val retrofit = RetrofitFactory(
        json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true },
        callFactory = OkHttpClient(),
    ).create(server.baseUrl)
    return RetrofitCometsRemoteDataSource(retrofit.create(), retrofit.create(), UnconfinedTestDispatcher())
}
