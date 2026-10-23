package com.radustavila.nasaexplorer.data.mars.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.MarsRover
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.mars.local.MarsDatabase
import com.radustavila.nasaexplorer.data.mars.local.RoomMarsLocalDataSource
import com.radustavila.nasaexplorer.data.mars.remote.MarsFixtures
import com.radustavila.nasaexplorer.data.mars.remote.RetrofitMarsRemoteDataSource
import com.radustavila.nasaexplorer.data.mars.remote.marsService
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The repository over a real (in-memory) Room database and recorded feeds. */
@RunWith(RobolectricTestRunner::class)
class MarsPhotoRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private lateinit var database: MarsDatabase
    private lateinit var repository: MarsPhotoRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarsDatabase::class.java,
        ).allowMainThreadQueries().build()
        val io = UnconfinedTestDispatcher()
        repository = MarsPhotoRepositoryImpl(
            localDataSource = RoomMarsLocalDataSource(database.marsPhotoDao(), io),
            remoteDataSource = RetrofitMarsRemoteDataSource(marsService(server), io),
            timeProvider = FakeTimeProvider(),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a refresh stores both rovers' photos without thumbnails`() = runTest {
        serve(curiosity = MarsFixtures.CURIOSITY_LATEST, perseverance = MarsFixtures.PERSEVERANCE_LATEST)

        val result = repository.refresh()

        assertEquals(DataResult.Success(14), result)
        assertEquals(8, repository.observeRoverPhotos(MarsRover.CURIOSITY).first().size)
        assertEquals(6, repository.observeRoverPhotos(MarsRover.PERSEVERANCE).first().size)
    }

    @Test
    fun `a rover's photos come newest sol first`() = runTest {
        serve(curiosity = MarsFixtures.CURIOSITY_LATEST, perseverance = MarsFixtures.PERSEVERANCE_LATEST)
        repository.refresh()

        val sols = repository.observeRoverPhotos(MarsRover.PERSEVERANCE).first().map { it.sol }

        assertEquals(listOf(2013, 2013, 2013, 2013, 2012, 2012), sols)
    }

    @Test
    fun `Curiosity is stored even when Perseverance's feed is down`() = runTest {
        serve(curiosity = MarsFixtures.CURIOSITY_LATEST, perseverance = null)

        val result = repository.refresh()

        assertEquals(403, ((result as DataResult.Failure).error as AppError.Http).code)
        assertEquals(8, repository.observeRoverPhotos(MarsRover.CURIOSITY).first().size)
        assertTrue(repository.observeRoverPhotos(MarsRover.PERSEVERANCE).first().isEmpty())
    }

    @Test
    fun `Curiosity is in the cache while Perseverance is still answering`() = runTest {
        val perseveranceAnswered = java.util.concurrent.CountDownLatch(1)
        server.route { request ->
            if (request.path.orEmpty().startsWith("/api/v1/")) {
                server.fixtureResponse(MarsFixtures.CURIOSITY_LATEST)
            } else {
                perseveranceAnswered.await(20, java.util.concurrent.TimeUnit.SECONDS)
                server.fixtureResponse(MarsFixtures.PERSEVERANCE_LATEST)
            }
        }

        val refresh = backgroundScope.async(kotlinx.coroutines.Dispatchers.IO) { repository.refresh() }
        val curiosity = repository.observeRoverPhotos(MarsRover.CURIOSITY).first { it.isNotEmpty() }
        // Perseverance is held until now, so the refresh cannot have finished.
        assertTrue(refresh.isActive)
        perseveranceAnswered.countDown()

        assertEquals(8, curiosity.size)
        assertEquals(DataResult.Success(14), refresh.await())
    }

    @Test
    fun `a feed with nothing usable counts as a failure and keeps the cache`() = runTest {
        serve(curiosity = MarsFixtures.CURIOSITY_LATEST, perseverance = MarsFixtures.PERSEVERANCE_LATEST)
        repository.refresh()
        serve(curiosity = MarsFixtures.CURIOSITY_EMPTY, perseverance = MarsFixtures.PERSEVERANCE_LATEST)

        val result = repository.refresh()

        assertEquals(DataResult.Failure(AppError.EmptyResponse), result)
        assertEquals(8, repository.observeRoverPhotos(MarsRover.CURIOSITY).first().size)
    }

    @Test
    fun `the rail takes each rover's newest photos, newest shot first`() = runTest {
        serve(curiosity = MarsFixtures.CURIOSITY_LATEST, perseverance = MarsFixtures.PERSEVERANCE_LATEST)
        repository.refresh()

        val latest = repository.observeLatest(perRover = 2).first()

        assertEquals(4, latest.size)
        assertEquals(2, latest.count { it.rover == MarsRover.PERSEVERANCE })
        assertEquals(latest.sortedByDescending { it.takenAt }, latest)
    }

    @Test
    fun `a photo is observed by id`() = runTest {
        serve(curiosity = MarsFixtures.CURIOSITY_LATEST, perseverance = MarsFixtures.PERSEVERANCE_LATEST)
        repository.refresh()

        val photo = repository.observePhoto("5048MH0009060011804545C00_DXXX").first()

        assertEquals("Mars Hand Lens Imager (MAHLI)", photo?.cameraName)
    }

    /** Answers by path, since the two rovers are fetched concurrently. A null feed answers 403. */
    private fun serve(curiosity: String?, perseverance: String?) {
        server.route { request ->
            val fixture = if (request.path.orEmpty().startsWith("/api/v1/")) curiosity else perseverance
            if (fixture == null) MockResponse().setResponseCode(403) else server.fixtureResponse(fixture)
        }
    }
}
