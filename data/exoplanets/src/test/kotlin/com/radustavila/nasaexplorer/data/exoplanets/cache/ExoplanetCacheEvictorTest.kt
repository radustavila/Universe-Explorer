package com.radustavila.nasaexplorer.data.exoplanets.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.data.exoplanets.local.ExoplanetEntity
import com.radustavila.nasaexplorer.data.exoplanets.local.ExoplanetsDatabase
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
class ExoplanetCacheEvictorTest {

    private val now = Instant.parse("2026-10-19T12:00:00Z")
    private val favorites = FakeFavoritesRepository()
    private lateinit var database: ExoplanetsDatabase
    private lateinit var evictor: ExoplanetCacheEvictor

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ExoplanetsDatabase::class.java,
        ).allowMainThreadQueries().build()
        evictor = ExoplanetCacheEvictor(database.exoplanetDao(), favorites, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `planets no refresh has returned for the retention period are deleted`() = runTest {
        val latestRefresh = now.minus(Duration.ofDays(2))
        database.exoplanetDao().upsertAll(
            listOf(
                planet("Current b", fetchedAt = latestRefresh),
                planet("Aged out b", fetchedAt = now.minus(Duration.ofDays(40))),
                planet("Recently dropped b", fetchedAt = now.minus(Duration.ofDays(10))),
            ),
        )

        val deleted = evictor.evict(now)

        assertEquals(1, deleted)
        assertEquals(listOf("Current b", "Recently dropped b"), cachedNames())
    }

    @Test
    fun `favourites are never deleted`() = runTest {
        database.exoplanetDao().upsertAll(
            listOf(
                planet("Current b", fetchedAt = now),
                planet("Saved b", fetchedAt = now.minus(Duration.ofDays(400))),
            ),
        )
        favorites.save(FavoriteItem(FavoriteSource.EXOPLANET, "Saved b", "Saved b", imageUrl = null))
        // Same id under another source must not protect anything.
        favorites.save(FavoriteItem(FavoriteSource.APOD, "Current b", "x", imageUrl = null))

        assertEquals(0, evictor.evict(now))
        assertEquals(listOf("Current b", "Saved b"), cachedNames())
    }

    @Test
    fun `a device offline for months keeps its last catalogue`() = runTest {
        val lastRefresh = now.minus(Duration.ofDays(200))
        database.exoplanetDao().upsertAll(
            listOf(planet("A b", fetchedAt = lastRefresh), planet("B b", fetchedAt = lastRefresh)),
        )

        assertEquals(0, evictor.evict(now))
        assertEquals(2, database.exoplanetDao().count())
    }

    @Test
    fun `an empty cache is a no-op`() = runTest {
        assertEquals(0, evictor.evict(now))
    }

    private suspend fun cachedNames(): List<String> =
        database.exoplanetDao().observeByRadius(0.0, Double.MAX_VALUE).first().map { it.name }.sorted()

    private fun planet(name: String, fetchedAt: Instant) = ExoplanetEntity(
        name = name,
        hostName = name.substringBeforeLast(' '),
        discoveryYear = 2026,
        discoveryPublished = "2026-09",
        discoveryMethod = "TRANSIT",
        discoveryFacility = null,
        radiusEarth = 1.0,
        massEarth = null,
        orbitalPeriodDays = null,
        equilibriumTemperatureKelvin = null,
        distanceParsecs = 10.0,
        fetchedAtMillis = fetchedAt.toEpochMilli(),
    )
}
