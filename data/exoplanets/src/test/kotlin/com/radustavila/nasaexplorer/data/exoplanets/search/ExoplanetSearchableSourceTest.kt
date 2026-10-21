package com.radustavila.nasaexplorer.data.exoplanets.search

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.result.getOrNull
import com.radustavila.nasaexplorer.data.exoplanets.local.ExoplanetEntity
import com.radustavila.nasaexplorer.data.exoplanets.local.ExoplanetsDatabase
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExoplanetSearchableSourceTest {

    private lateinit var database: ExoplanetsDatabase
    private lateinit var source: ExoplanetSearchableSource

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ExoplanetsDatabase::class.java,
        ).allowMainThreadQueries().build()
        source = ExoplanetSearchableSource(database.exoplanetDao(), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `finds planets by name or host star, every word of the query matching`() = runTest {
        database.exoplanetDao().upsertAll(
            listOf(
                planet("Kepler-22 b", "Kepler-22"),
                planet("Kepler-186 f", "Kepler-186"),
                planet("TRAPPIST-1 e", "TRAPPIST-1"),
                planet("Proxima Cen b", "Proxima Cen"),
            ),
        )

        assertEquals(listOf("Kepler-186 f", "Kepler-22 b"), source.search("kepler", 10).getOrNull()?.map { it.id })
        assertEquals(listOf("Kepler-22 b"), source.search("kepler 22", 10).getOrNull()?.map { it.id })
        assertEquals(listOf("TRAPPIST-1"), source.search("trappist", 10).getOrNull()?.map { it.subtitle })
        assertEquals(emptyList<String>(), source.search("kepler proxima", 10).getOrNull())
    }

    private fun planet(name: String, host: String) = ExoplanetEntity(
        name = name,
        hostName = host,
        discoveryYear = 2011,
        discoveryPublished = null,
        discoveryMethod = "TRANSIT",
        discoveryFacility = null,
        radiusEarth = null,
        massEarth = null,
        orbitalPeriodDays = null,
        equilibriumTemperatureKelvin = null,
        distanceParsecs = null,
        fetchedAtMillis = 0,
    )
}
