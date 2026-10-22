package com.radustavila.nasaexplorer.data.dsn.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.data.dsn.local.DsnDatabase
import com.radustavila.nasaexplorer.data.dsn.local.DsnDishEntity
import com.radustavila.nasaexplorer.data.dsn.local.DsnDishInfoEntity
import com.radustavila.nasaexplorer.data.dsn.local.DsnLinkEntity
import com.radustavila.nasaexplorer.data.dsn.local.DsnSignalEntity
import com.radustavila.nasaexplorer.data.dsn.local.DsnSpacecraftEntity
import com.radustavila.nasaexplorer.data.dsn.local.ProbeDistanceEntity
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DsnCacheEvictorTest {

    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private lateinit var database: DsnDatabase
    private val dao get() = database.dsnDao()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DsnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `names and distances no refresh returned for the retention period are trimmed`() = runTest {
        val latest = now.minus(Duration.ofDays(1)).toEpochMilli()
        val stale = now.minus(Duration.ofDays(90)).toEpochMilli()
        dao.storeConfig(
            listOf(DsnSpacecraftEntity("VGR2", "Voyager 2", latest), DsnSpacecraftEntity("CAS", "Cassini", stale)),
            listOf(DsnDishInfoEntity("DSS43", "cdscc", 70, latest), DsnDishInfoEntity("DSS61", "mdscc", 34, stale)),
        )
        dao.upsertProbeDistances(listOf(distance(-31, latest), distance(-61, stale)))
        storeLive(fetchedAt = stale)

        val deleted = evictor().evict(now)

        assertEquals(3, deleted)
        assertEquals(listOf("VGR2"), dao.observeSpacecraft().first().map { it.code })
        assertEquals(listOf("DSS43"), dao.observeDishInfo().first().map { it.dishName })
        assertEquals(listOf(-31), dao.observeProbeDistances().first().map { it.horizonsId })
        // The live snapshot is the last good state: never trimmed, however old.
        assertEquals(1, dao.observeLive().first().size)
    }

    @Test
    fun `a device offline for months keeps its last download`() = runTest {
        val old = now.minus(Duration.ofDays(200)).toEpochMilli()
        dao.storeConfig(listOf(DsnSpacecraftEntity("VGR2", "Voyager 2", old)), emptyList())
        dao.upsertProbeDistances(listOf(distance(-31, old)))

        assertEquals(0, evictor().evict(now))
    }

    @Test
    fun `clear cache empties every table`() = runTest {
        dao.storeConfig(
            listOf(DsnSpacecraftEntity("VGR2", "Voyager 2", 1)),
            listOf(DsnDishInfoEntity("DSS43", "cdscc", 70, 1)),
        )
        dao.upsertProbeDistances(listOf(distance(-31, 1)))
        storeLive(fetchedAt = 1)

        DsnCacheCleaner(dao, UnconfinedTestDispatcher()).clear()

        assertTrue(dao.observeLive().first().isEmpty())
        assertTrue(dao.observeSpacecraft().first().isEmpty())
        assertTrue(dao.observeDishInfo().first().isEmpty())
        assertTrue(dao.observeProbeDistances().first().isEmpty())
    }

    private fun evictor() = DsnCacheEvictor(dao, UnconfinedTestDispatcher())

    private suspend fun storeLive(fetchedAt: Long) = dao.replaceLive(
        dishes = listOf(
            DsnDishEntity("DSS43", "cdscc", "Canberra", 0, 0, 207.0, 17.0, "Tracking", fetchedAt, fetchedAt),
        ),
        links = listOf(DsnLinkEntity("DSS43", "VGR2", 0, -32, 2.16e10)),
        signals = listOf(
            DsnSignalEntity(
                dishName = "DSS43",
                spacecraftCode = "VGR2",
                isUplink = false,
                band = "X",
                dataRateBitsPerSecond = 160.0,
                isActive = true,
            ),
        ),
    )

    private fun distance(id: Int, fetchedAt: Long) =
        ProbeDistanceEntity(id, "Probe $id", 100.0, 30.0, epochMillis = fetchedAt, fetchedAtMillis = fetchedAt)
}
