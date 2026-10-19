package com.radustavila.nasaexplorer.data.smallbodies.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.data.smallbodies.local.CloseApproachEntity
import com.radustavila.nasaexplorer.data.smallbodies.local.SmallBodiesDatabase
import com.radustavila.nasaexplorer.data.smallbodies.local.SmallBodyDetailsEntity
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmallBodiesCacheEvictorTest {

    private val now = Instant.parse("2026-10-19T14:20:00Z")
    private lateinit var database: SmallBodiesDatabase
    private lateinit var evictor: SmallBodiesCacheEvictor

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmallBodiesDatabase::class.java,
        ).allowMainThreadQueries().build()
        evictor = SmallBodiesCacheEvictor(database.smallBodiesDao(), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `long-passed approaches and stale details of departed objects go`() = runTest {
        val dao = database.smallBodiesDao()
        dao.replaceAll(
            approaches = listOf(approach("2026 UK", daysFromNow = -10), approach("2020 FN3", daysFromNow = 1)),
            sentry = emptyList(),
            fireballs = emptyList(),
        )
        dao.upsertDetails(details("2020 FN3", fetchedDaysAgo = 60))
        dao.upsertDetails(details("2019 OK", fetchedDaysAgo = 60))
        dao.upsertDetails(details("2024 YR4", fetchedDaysAgo = 2))

        assertEquals(2, evictor.evict(now))
        assertEquals(listOf("2020 FN3"), dao.observeCloseApproaches().first().map { it.approach.designation })
        assertNotNull(dao.observeDetails("2020 FN3").first())
        assertNull(dao.observeDetails("2019 OK").first())
        assertNotNull(dao.observeDetails("2024 YR4").first())
    }

    private fun approach(designation: String, daysFromNow: Long) = CloseApproachEntity(
        designation = designation,
        name = designation,
        time = now.plus(Duration.ofDays(daysFromNow)).toEpochMilli(),
        distanceAu = 0.02,
        relativeVelocityKmPerSecond = 10.0,
        absoluteMagnitude = 25.0,
        diameterKm = null,
    )

    private fun details(designation: String, fetchedDaysAgo: Long) = SmallBodyDetailsEntity(
        designation = designation,
        name = designation,
        orbitClass = "Apollo",
        isNearEarthObject = true,
        isPotentiallyHazardous = false,
        earthMoidAu = null,
        semiMajorAxisAu = null,
        eccentricity = null,
        inclinationDegrees = null,
        perihelionAu = null,
        aphelionAu = null,
        orbitalPeriodDays = null,
        absoluteMagnitude = null,
        diameterKm = null,
        firstObserved = null,
        lastObserved = null,
        observationsUsed = null,
        fetchedAt = now.minus(Duration.ofDays(fetchedDaysAgo)).toEpochMilli(),
    )
}
