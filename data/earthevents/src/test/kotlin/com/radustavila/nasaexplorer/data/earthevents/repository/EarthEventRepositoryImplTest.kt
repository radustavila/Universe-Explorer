package com.radustavila.nasaexplorer.data.earthevents.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.earthevents.local.EarthEventsDatabase
import com.radustavila.nasaexplorer.data.earthevents.local.RoomEarthEventsLocalDataSource
import com.radustavila.nasaexplorer.data.earthevents.remote.EonetFixtures
import com.radustavila.nasaexplorer.data.earthevents.remote.RetrofitEonetRemoteDataSource
import com.radustavila.nasaexplorer.data.earthevents.remote.eonetService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The events pipeline end to end - recorded responses, real HTTP stack, real Room: a refresh
 * replaces the cache, failures keep it, and imagery layers are fetched once per category.
 */
@RunWith(RobolectricTestRunner::class)
class EarthEventRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider()
    private lateinit var database: EarthEventsDatabase
    private lateinit var repository: EarthEventRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EarthEventsDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = newRepository()
    }

    /** A repository over the same database, as after an app restart. */
    private fun newRepository(): EarthEventRepositoryImpl {
        val io = UnconfinedTestDispatcher()
        return EarthEventRepositoryImpl(
            localDataSource = RoomEarthEventsLocalDataSource(database.earthEventDao(), io),
            remoteDataSource = RetrofitEonetRemoteDataSource(eonetService(server), io),
            timeProvider = clock,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a refresh caches the open events, most recently updated first`() = runTest {
        server.enqueueFixture(EonetFixtures.OPEN_EVENTS)

        val result = repository.refreshOpenEvents()

        assertEquals(DataResult.Success(5), result)
        val events = repository.observeOpenEvents().first()
        assertEquals(5, events.size)
        assertEquals(events.sortedByDescending { it.lastUpdate }, events)
        assertEquals(13, events.first { it.id == "EONET_25340" }.track.size)
    }

    @Test
    fun `events stay readable offline after one sync`() = runTest {
        server.enqueueFixture(EonetFixtures.OPEN_EVENTS)
        repository.refreshOpenEvents()
        server.enqueueStatus(503)

        val failed = newRepository().refreshOpenEvents()

        assertTrue(failed is DataResult.Failure)
        assertEquals(5, newRepository().observeOpenEvents().first().size)
    }

    @Test
    fun `a refresh replaces the list, so closed events disappear`() = runTest {
        server.enqueueFixture(EonetFixtures.OPEN_EVENTS)
        repository.refreshOpenEvents()
        server.enqueueFixture(EonetFixtures.FLOOD_POLYGONS)

        repository.refreshOpenEvents()

        val events = repository.observeOpenEvents().first()
        assertEquals(3, events.size)
        assertTrue(events.all { it.category == EarthEventCategory.FLOODS })
    }

    @Test
    fun `an answer of which nothing parses keeps the cache`() = runTest {
        server.enqueueFixture(EonetFixtures.OPEN_EVENTS)
        repository.refreshOpenEvents()
        server.enqueueJson("""{"events": [{"id": "E1", "title": "Broken", "geometry": []}]}""")

        val result = repository.refreshOpenEvents()

        assertEquals(DataResult.Failure(AppError.EmptyResponse), result)
        assertEquals(5, repository.observeOpenEvents().first().size)
    }

    @Test
    fun `an empty answer empties the map`() = runTest {
        server.enqueueFixture(EonetFixtures.OPEN_EVENTS)
        repository.refreshOpenEvents()
        server.enqueueJson("""{"events": []}""")

        val result = repository.refreshOpenEvents()

        assertEquals(DataResult.Success(0), result)
        assertTrue(repository.observeOpenEvents().first().isEmpty())
    }

    @Test
    fun `an imagery layer is fetched once per category, then read from the cache`() = runTest {
        server.enqueueFixture(EonetFixtures.LAYERS_WILDFIRES)

        val first = repository.imageryLayerFor(EarthEventCategory.WILDFIRES)
        val second = newRepository().imageryLayerFor(EarthEventCategory.WILDFIRES)

        assertEquals("VIIRS_SNPP_CorrectedReflectance_TrueColor", (first as DataResult.Success).data?.id)
        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a failed layer lookup reports the error`() = runTest {
        server.enqueueStatus(500)

        val result = repository.imageryLayerFor(EarthEventCategory.SEVERE_STORMS)

        assertTrue(result is DataResult.Failure)
    }
}
