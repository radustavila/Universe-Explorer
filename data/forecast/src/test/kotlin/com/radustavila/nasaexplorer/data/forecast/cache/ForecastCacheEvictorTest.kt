package com.radustavila.nasaexplorer.data.forecast.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.data.forecast.local.ForecastDatabase
import com.radustavila.nasaexplorer.data.forecast.local.ForecastPlaceEntity
import com.radustavila.nasaexplorer.data.forecast.local.ForecastStepEntity
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForecastCacheEvictorTest {

    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ForecastDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = database.forecastDao()
    private val now = Instant.parse("2026-10-22T20:00:00Z")

    @After
    fun tearDown() = database.close()

    @Test
    fun `old places and past steps go, the current forecast stays`() = runTest {
        store(latitudeE2 = 4443, fetchedAt = now.minus(Duration.ofHours(1)))
        store(latitudeE2 = 5991, fetchedAt = now.minus(Duration.ofDays(8)))

        val evicted = ForecastCacheEvictor(dao, Dispatchers.Unconfined).evict(now)

        // Oslo's place and its 3 steps, then Bucharest's step from 2 days ago.
        assertEquals(5, evicted)
        assertNull(dao.place(5991, 1075))
        assertNotNull(dao.place(4443, 1075))
    }

    @Test
    fun `a device offline for weeks keeps its last forecast`() = runTest {
        store(latitudeE2 = 4443, fetchedAt = now.minus(Duration.ofDays(30)))

        ForecastCacheEvictor(dao, Dispatchers.Unconfined).evict(now)

        assertNotNull(dao.place(4443, 1075))
    }

    @Test
    fun `clear cache empties everything`() = runTest {
        store(latitudeE2 = 4443, fetchedAt = now)

        ForecastCacheCleaner(dao, Dispatchers.Unconfined).clear()

        assertNull(dao.place(4443, 1075))
    }

    private suspend fun store(latitudeE2: Int, fetchedAt: Instant) {
        val place = ForecastPlaceEntity(latitudeE2, 1075, fetchedAt.toEpochMilli(), fetchedAt.toEpochMilli(), null, null)
        val steps = listOf(Duration.ofDays(-2), Duration.ZERO, Duration.ofHours(1)).map { offset ->
            ForecastStepEntity(latitudeE2, 1075, now.plus(offset).toEpochMilli(), 0.0, null, null, null, null, null, null, null)
        }
        dao.replaceForecast(place, steps)
    }
}
