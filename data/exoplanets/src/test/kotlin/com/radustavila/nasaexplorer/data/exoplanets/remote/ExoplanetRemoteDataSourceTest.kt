package com.radustavila.nasaexplorer.data.exoplanets.remote

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.create

/** The TAP endpoint against recorded responses: request shape, parsing and failures. */
class ExoplanetRemoteDataSourceTest {

    @get:Rule
    val server = FixtureServer()

    private val dataSource by lazy {
        RetrofitExoplanetRemoteDataSource(exoplanetArchiveService(server), UnconfinedTestDispatcher())
    }

    @Test
    fun `catalog request sends the encoded ADQL as json`() = runTest {
        server.enqueueFixture(CATALOG_FIXTURE)

        dataSource.fetchCatalog(newestSinceYear = 2025)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/TAP/sync", request.requestUrl!!.encodedPath)
        assertEquals(
            "query=${ExoplanetQueries.encode(ExoplanetQueries.catalog(2025))}&format=json",
            request.requestUrl!!.encodedQuery,
        )
        // Decoded by the server exactly as built: nothing was encoded twice.
        assertEquals(ExoplanetQueries.catalog(2025), request.requestUrl!!.queryParameter("query"))
    }

    @Test
    fun `recorded catalogue parses every row, gaps as null`() = runTest {
        server.enqueueFixture(CATALOG_FIXTURE)

        val rows = (dataSource.fetchCatalog(2025) as DataResult.Success).data

        assertEquals(24, rows.size)
        val proxima = rows.single { it.name == "Proxima Cen b" }
        assertEquals("Proxima Cen", proxima.hostName)
        assertEquals(2016, proxima.discoveryYear)
        assertEquals("2016-08", proxima.discoveryPublished)
        assertEquals("rv", proxima.discoveryMethod)
        assertEquals(1.30119, proxima.distanceParsecs!!, 1e-9)
        val unknownDistance = rows.single { it.name == "V2376 Ori b" }
        assertNull(unknownDistance.distanceParsecs)
        assertNull(unknownDistance.orbitalPeriodDays)
    }

    @Test
    fun `count request returns the archive total`() = runTest {
        server.enqueueFixture(COUNT_FIXTURE)

        assertEquals(DataResult.Success(6451), dataSource.fetchConfirmedCount())
        assertEquals(
            "query=select%20count%28*%29%20from%20pscomppars&format=json",
            server.takeRequest().requestUrl!!.encodedQuery,
        )
    }

    @Test
    fun `an empty count result is an empty response`() = runTest {
        server.enqueueJson("[]")

        assertEquals(DataResult.Failure(AppError.EmptyResponse), dataSource.fetchConfirmedCount())
    }

    @Test
    fun `a rejected query surfaces as an http error`() = runTest {
        server.enqueue(
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "text/xml")
                .setBody(Fixtures.read(ERROR_FIXTURE)),
        )

        val result = dataSource.fetchCatalog(2025)

        assertTrue(result is DataResult.Failure && result.error is AppError.Http)
        assertEquals(400, ((result as DataResult.Failure).error as AppError.Http).code)
    }

    @Test
    fun `a 429 surfaces as rate limited`() = runTest {
        server.enqueueStatus(code = 429, headers = mapOf("Retry-After" to "120"))

        assertEquals(DataResult.Failure(AppError.RateLimited(120)), dataSource.fetchCatalog(2025))
    }
}

internal const val CATALOG_FIXTURE = "exoplanets/catalog.json"
internal const val COUNT_FIXTURE = "exoplanets/count.json"
internal const val ERROR_FIXTURE = "exoplanets/error_invalid_identifier.xml"

internal fun exoplanetArchiveService(server: FixtureServer): ExoplanetArchiveService =
    RetrofitFactory(
        json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true },
        callFactory = OkHttpClient(),
    ).create(server.baseUrl).create()
