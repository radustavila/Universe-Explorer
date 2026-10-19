package com.radustavila.nasaexplorer.data.sky.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.data.sky.local.OrbitalElementsEntity
import com.radustavila.nasaexplorer.data.sky.local.SkyDatabase
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
class OrbitalElementsCacheEvictorTest {

    private val now = Instant.parse("2026-10-19T12:00:00Z")
    private lateinit var database: SkyDatabase
    private lateinit var evictor: OrbitalElementsCacheEvictor

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SkyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        evictor = OrbitalElementsCacheEvictor(database.orbitalElementsDao(), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `element sets older than the retention are removed, recent ones stay`() = runTest {
        val dao = database.orbitalElementsDao()
        dao.upsert(
            listOf(
                elements(noradId = 1, epoch = now.minus(OrbitalElementsCacheEvictor.RETENTION).minusSeconds(1)),
                elements(noradId = 2, epoch = now.minus(Duration.ofDays(3))),
            ),
        )

        assertEquals(1, evictor.evict(now))
        assertNull(dao.observe(1).first())
        assertNotNull(dao.observe(2).first())
    }

    @Test
    fun `nothing to evict is a no-op`() = runTest {
        assertEquals(0, evictor.evict(now))
    }

    private fun elements(noradId: Int, epoch: Instant) = OrbitalElementsEntity(
        noradId = noradId,
        name = "SAT $noradId",
        epochMillis = epoch.toEpochMilli(),
        meanMotion = 15.5,
        eccentricity = 0.0007,
        inclinationDegrees = 51.6,
        raanDegrees = 46.5,
        argOfPericenterDegrees = 282.0,
        meanAnomalyDegrees = 78.0,
        bstar = 0.0001,
        fetchedAtMillis = epoch.toEpochMilli(),
    )
}
