package com.radustavila.nasaexplorer.data.earthevents.remote

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EonetRemoteDataSourceTest {

    @get:Rule
    val server = FixtureServer()

    private lateinit var remoteDataSource: EonetRemoteDataSource

    @Before
    fun setUp() {
        remoteDataSource = RetrofitEonetRemoteDataSource(eonetService(server), UnconfinedTestDispatcher())
    }

    @Test
    fun `open events are asked for within the active window`() = runTest {
        server.enqueueFixture(EonetFixtures.OPEN_EVENTS)

        val result = remoteDataSource.fetchOpenEvents()

        assertEquals(5, (result as DataResult.Success).data.events.size)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/events", url.encodedPath)
        assertEquals("open", url.queryParameter("status"))
        assertEquals("${EonetRemoteDataSource.ACTIVE_WINDOW_DAYS}", url.queryParameter("days"))
        assertNull(url.queryParameter("category"))
        assertNull(url.queryParameter("limit"))
    }

    @Test
    fun `layers are asked for by category key`() = runTest {
        server.enqueueFixture(EonetFixtures.LAYERS_SEVERE_STORMS)

        val result = remoteDataSource.fetchLayers(EarthEventCategory.SEVERE_STORMS)

        assertEquals(24, (result as DataResult.Success).data.categories.single().layers.size)
        assertEquals("/layers/severeStorms", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun `a server error becomes an AppError`() = runTest {
        server.enqueueStatus(503)

        val result = remoteDataSource.fetchOpenEvents()

        assertEquals(503, ((result as DataResult.Failure).error as AppError.Http).code)
    }
}
