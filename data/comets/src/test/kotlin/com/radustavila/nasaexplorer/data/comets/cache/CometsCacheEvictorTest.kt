package com.radustavila.nasaexplorer.data.comets.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.data.comets.local.CometEntity
import com.radustavila.nasaexplorer.data.comets.local.CometEphemerisEntity
import com.radustavila.nasaexplorer.data.comets.local.CometsDatabase
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CometsCacheEvictorTest {

    private lateinit var database: CometsDatabase
    private val dao get() = database.cometsDao()
    private val now = Instant.parse("2026-10-23T00:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CometsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    private suspend fun store(fetchedAt: Instant, days: IntRange) {
        dao.storeRefresh(
            comets = listOf(comet(fetchedAt)),
            ephemerides = mapOf("2026 A2" to days.map { point(fetchedAt, it) }),
        )
    }

    @Test
    fun `days more than 90 days old go once a newer refresh exists`() = runTest {
        // The refresh window of a refresh made today starts 60 days ago: nothing in it is touched.
        store(fetchedAt = now, days = -120..60)

        val removed = CometsCacheEvictor(dao, Dispatchers.Unconfined).evict(now)

        assertEquals(30, removed)
        val oldest = dao.observeComet("2026 A2").first()!!.ephemeris.minOf { it.timeMillis }
        assertEquals(now.minus(Duration.ofDays(90)).toEpochMilli(), oldest)
    }

    @Test
    fun `a device offline for months keeps the window of its last refresh`() = runTest {
        val lastRefresh = now.minus(Duration.ofDays(200))
        store(fetchedAt = lastRefresh, days = -60..60)

        assertEquals(0, CometsCacheEvictor(dao, Dispatchers.Unconfined).evict(now))
    }

    @Test
    fun `clear cache empties both tables`() = runTest {
        store(fetchedAt = now, days = -2..2)

        CometsCacheCleaner(dao, Dispatchers.Unconfined).clear()

        assertTrue(dao.observeComets().first().isEmpty())
        assertEquals(null, dao.newestEphemerisFetchedAt())
    }

    private fun comet(fetchedAt: Instant) = CometEntity(
        designation = "2026 A2",
        name = "C/2026 A2 (Bok)",
        absoluteMagnitude = 9.2,
        slopeParameter = 12.0,
        perihelionDistanceAu = 1.938,
        eccentricity = 0.9976,
        perihelionTimeMillis = null,
        fetchedAtMillis = fetchedAt.toEpochMilli(),
    )

    private fun point(fetchedAt: Instant, day: Int) = CometEphemerisEntity(
        designation = "2026 A2",
        timeMillis = fetchedAt.plus(Duration.ofDays(day.toLong())).toEpochMilli(),
        rightAscensionDegrees = 292.5,
        declinationDegrees = 59.8,
        magnitude = 14.0,
        sunDistanceAu = 2.0,
        earthDistanceAu = 1.7,
        fetchedAtMillis = fetchedAt.toEpochMilli(),
    )
}
