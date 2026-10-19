package com.radustavila.nasaexplorer.data.launches.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.launches.local.LaunchesDatabase
import com.radustavila.nasaexplorer.data.launches.local.RoomPeopleInSpaceLocalDataSource
import com.radustavila.nasaexplorer.data.launches.remote.RetrofitLaunchRemoteDataSource
import com.radustavila.nasaexplorer.data.launches.remote.launchLibraryService
import java.time.Instant
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

/** Recorded LL2 `astronauts/?in_space=true` response (lldev, 2026-10-19), real HTTP stack, real Room. */
@RunWith(RobolectricTestRunner::class)
class PeopleInSpaceRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private lateinit var database: LaunchesDatabase
    private lateinit var repository: PeopleInSpaceRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LaunchesDatabase::class.java,
        ).allowMainThreadQueries().build()

        val io = UnconfinedTestDispatcher()
        repository = PeopleInSpaceRepositoryImpl(
            localDataSource = RoomPeopleInSpaceLocalDataSource(database.personInSpaceDao(), io),
            remoteDataSource = RetrofitLaunchRemoteDataSource(launchLibraryService(server), io),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a refresh caches everyone in space in one call, without Starman`() = runTest {
        server.enqueueFixture(IN_SPACE_FIXTURE)

        val result = repository.refreshPeopleInSpace()

        // 11 entries, one of them the mannequin launched on a Tesla in 2018.
        assertEquals(DataResult.Success(10), result)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest().requestUrl!!
        assertEquals("/2.3.0/astronauts/", request.encodedPath)
        assertEquals("true", request.queryParameter("in_space"))
        assertEquals("list", request.queryParameter("mode"))

        val people = repository.observePeopleInSpace().first()
        assertTrue(people.none { it.name == "Starman" })
        val watkins = people.single { it.name == "Jessica Watkins" }
        assertEquals("NASA", watkins.agency)
        assertEquals("United States of America", watkins.nationality)
        assertEquals(Instant.parse("2026-10-01T15:10:06Z"), watkins.launchedAt)
        // Longest in orbit first.
        assertEquals(people.sortedBy { it.launchedAt }, people)
    }

    @Test
    fun `a 429 keeps the cached crew`() = runTest {
        server.enqueueFixture(IN_SPACE_FIXTURE)
        repository.refreshPeopleInSpace()
        val cached = repository.observePeopleInSpace().first()

        server.enqueueStatus(code = 429, headers = mapOf("Retry-After" to "1800"))

        assertEquals(DataResult.Failure(AppError.RateLimited(retryAfterSeconds = 1800)), repository.refreshPeopleInSpace())
        assertEquals(cached, repository.observePeopleInSpace().first())
    }

    @Test
    fun `an answer with nobody usable keeps the cached crew`() = runTest {
        server.enqueueFixture(IN_SPACE_FIXTURE)
        repository.refreshPeopleInSpace()

        server.enqueueJson("""{"count":1,"results":[{"id":1,"name":"Starman","type":{"id":6,"name":"Non-Human"}}]}""")

        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.refreshPeopleInSpace())
        assertEquals(10, repository.observePeopleInSpace().first().size)
    }

    private companion object {
        const val IN_SPACE_FIXTURE = "astronauts/in_space.json"
    }
}
