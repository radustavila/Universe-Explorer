package com.radustavila.nasaexplorer.data.mars.remote

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MarsRemoteDataSourceTest {

    @get:Rule
    val server = FixtureServer()

    private lateinit var remoteDataSource: MarsRemoteDataSource

    @Before
    fun setUp() {
        remoteDataSource = RetrofitMarsRemoteDataSource(marsService(server), UnconfinedTestDispatcher())
    }

    @Test
    fun `Curiosity asks the raw image catalogue for its newest sol first`() = runTest {
        server.enqueueFixture(MarsFixtures.CURIOSITY_LATEST)

        val result = remoteDataSource.fetchCuriosity()

        assertEquals(11, (result as DataResult.Success).data.items.size)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/api/v1/raw_image_items/", url.encodedPath)
        assertEquals("sol desc", url.queryParameter("order"))
        assertEquals("msl:mission", url.queryParameter("condition_1"))
        assertEquals("${MarsRemoteDataSource.PAGE_SIZE}", url.queryParameter("per_page"))
    }

    @Test
    fun `Perseverance asks the raw images feed for JSON`() = runTest {
        server.enqueueFixture(MarsFixtures.PERSEVERANCE_LATEST)

        val result = remoteDataSource.fetchPerseverance()

        val first = (result as DataResult.Success).data.images.first()
        assertEquals("NLG_2013_0845644580_675ECM_N0920000NCAM00518_00_2I3J", first.imageid)
        assertEquals("NAVCAM_LEFT", first.camera?.instrument)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/rss/api/", url.encodedPath)
        assertEquals("raw_images", url.queryParameter("feed"))
        assertEquals("mars2020", url.queryParameter("category"))
        assertEquals("json", url.queryParameter("feedtype"))
        assertEquals("${MarsRemoteDataSource.PAGE_SIZE}", url.queryParameter("num"))
    }

    @Test
    fun `a 403 is the feed being down, not a rate limit`() = runTest {
        server.enqueueStatus(403)

        val error = (remoteDataSource.fetchPerseverance() as DataResult.Failure).error

        assertEquals(403, (error as AppError.Http).code)
        assertFalse(error.isRetryable)
    }

    @Test
    fun `a 429 still is a rate limit`() = runTest {
        server.enqueueStatus(429, headers = mapOf("Retry-After" to "120"))

        val error = (remoteDataSource.fetchCuriosity() as DataResult.Failure).error

        assertEquals(AppError.RateLimited(retryAfterSeconds = 120), error)
    }

    @Test
    fun `a server error is retryable`() = runTest {
        server.enqueueStatus(503)

        val error = (remoteDataSource.fetchCuriosity() as DataResult.Failure).error

        assertEquals(503, (error as AppError.Http).code)
    }
}
