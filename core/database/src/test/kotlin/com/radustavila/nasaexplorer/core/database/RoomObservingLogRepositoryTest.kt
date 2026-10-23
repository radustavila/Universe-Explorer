package com.radustavila.nasaexplorer.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.database.entity.ObservingLogEntity
import com.radustavila.nasaexplorer.core.database.repository.RoomObservingLogRepository
import com.radustavila.nasaexplorer.core.model.ObservingConditions
import com.radustavila.nasaexplorer.core.testing.data.TestDeepSky
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomObservingLogRepositoryTest {

    private lateinit var database: NasaDatabase
    private lateinit var repository: RoomObservingLogRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NasaDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomObservingLogRepository(database.observingLogDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `marks round-trip, newest observation first, and a new mark replaces the old one`() = runTest {
        val andromeda = TestDeepSky.entry(objectId = "NGC0224", observedOn = LocalDate.of(2026, 10, 20), note = "10x50")
        val orion = TestDeepSky.entry(objectId = "NGC1976", observedOn = LocalDate.of(2026, 1, 12), conditions = null)
        repository.save(orion)
        repository.save(andromeda)

        assertEquals(listOf(andromeda, orion), repository.observeEntries().first())

        val again = andromeda.copy(observedOn = LocalDate.of(2026, 10, 22), conditions = ObservingConditions.POOR)
        repository.save(again)
        assertEquals(listOf(again, orion), repository.observeEntries().first())
    }

    @Test
    fun `removing a mark deletes only that one`() = runTest {
        repository.save(TestDeepSky.entry(objectId = "NGC0224"))
        repository.save(TestDeepSky.entry(objectId = "NGC1976"))

        repository.remove("NGC0224")

        assertEquals(listOf("NGC1976"), repository.observeEntries().first().map { it.objectId })
    }

    @Test
    fun `a row with an unreadable date or condition is skipped or read without it`() = runTest {
        val dao = database.observingLogDao()
        dao.upsert(ObservingLogEntity("NGC0224", "someday", null, null, 0))
        dao.upsert(ObservingLogEntity("NGC1976", "2026-01-12", "HAZY", null, 0))

        val entries = repository.observeEntries().first()

        assertEquals(listOf("NGC1976"), entries.map { it.objectId })
        assertEquals(null, entries.single().conditions)
    }
}
