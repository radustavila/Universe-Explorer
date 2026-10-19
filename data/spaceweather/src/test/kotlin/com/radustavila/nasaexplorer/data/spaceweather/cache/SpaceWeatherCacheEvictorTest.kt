package com.radustavila.nasaexplorer.data.spaceweather.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.data.spaceweather.local.KpReadingEntity
import com.radustavila.nasaexplorer.data.spaceweather.local.SolarFlareEntity
import com.radustavila.nasaexplorer.data.spaceweather.local.SpaceWeatherDatabase
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpaceWeatherCacheEvictorTest {

    private val now = Instant.parse("2026-10-19T14:20:00Z")
    private lateinit var database: SpaceWeatherDatabase
    private lateinit var evictor: SpaceWeatherCacheEvictor

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SpaceWeatherDatabase::class.java,
        ).allowMainThreadQueries().build()
        evictor = SpaceWeatherCacheEvictor(database.spaceWeatherDao(), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `old Kp slots go, recent ones stay`() = runTest {
        val dao = database.spaceWeatherDao()
        dao.upsertKpReadings(
            listOf(
                KpReadingEntity(now.minus(Duration.ofDays(20)).toEpochMilli(), 2.0, "OBSERVED"),
                KpReadingEntity(now.minus(Duration.ofDays(2)).toEpochMilli(), 3.0, "OBSERVED"),
                KpReadingEntity(now.plus(Duration.ofDays(1)).toEpochMilli(), 4.0, "PREDICTED"),
            ),
        )

        assertEquals(1, evictor.evict(now))
        assertEquals(2, dao.observeKpReadings().first().size)
    }

    @Test
    fun `the newest flare survives however old it is`() = runTest {
        val dao = database.spaceWeatherDao()
        dao.upsertFlares(listOf(flare("old", daysAgo = 200), flare("older", daysAgo = 300)))

        assertEquals(1, evictor.evict(now))
        assertEquals("old", dao.observeLatestFlare().first()?.id)
    }

    private fun flare(id: String, daysAgo: Long) = SolarFlareEntity(
        id = id,
        classType = "C1.0",
        beginTime = now.minus(Duration.ofDays(daysAgo)).toEpochMilli(),
        peakTime = null,
        endTime = null,
        sourceLocation = null,
        activeRegion = null,
        link = null,
    )
}
