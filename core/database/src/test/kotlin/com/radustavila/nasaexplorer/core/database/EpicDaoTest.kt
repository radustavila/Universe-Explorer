package com.radustavila.nasaexplorer.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.database.dao.EpicDao
import com.radustavila.nasaexplorer.core.database.entity.EpicAvailableDateEntity
import com.radustavila.nasaexplorer.core.database.entity.EpicImageEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpicDaoTest {

    private lateinit var database: NasaDatabase
    private lateinit var epicDao: EpicDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NasaDatabase::class.java,
        ).allowMainThreadQueries().build()
        epicDao = database.epicDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the latest day is the newest cached day of that collection, in capture order`() = runTest {
        epicDao.replaceDays(
            collection = NATURAL,
            images = listOf(
                image("2026-10-16", hour = 9),
                image("2026-10-15", hour = 1),
                image("2026-10-16", hour = 0),
            ),
            keepFrom = null,
        )
        epicDao.replaceDays(ENHANCED, listOf(image("2026-10-17", hour = 0, collection = ENHANCED)), keepFrom = null)

        val latest = epicDao.observeLatestDay(NATURAL).first()

        assertEquals(listOf("2026-10-16" to 0L, "2026-10-16" to 9L), latest.map { it.date to it.hour })
    }

    @Test
    fun `replacing a day drops rows the fresh listing no longer contains`() = runTest {
        epicDao.replaceDays(NATURAL, listOf(image("2026-10-16", hour = 0), image("2026-10-16", hour = 2)), null)

        epicDao.replaceDays(NATURAL, listOf(image("2026-10-16", hour = 2)), null)

        assertEquals(listOf(2L), epicDao.observeDay(NATURAL, "2026-10-16").first().map { it.hour })
    }

    @Test
    fun `replacing days trims older days of the same collection only`() = runTest {
        epicDao.replaceDays(NATURAL, listOf(image("2026-10-01", hour = 0)), null)
        epicDao.replaceDays(ENHANCED, listOf(image("2026-10-01", hour = 0, collection = ENHANCED)), null)

        epicDao.replaceDays(NATURAL, listOf(image("2026-10-16", hour = 0)), keepFrom = "2026-10-10")

        assertTrue(epicDao.observeDay(NATURAL, "2026-10-01").first().isEmpty())
        assertEquals(1, epicDao.observeDay(ENHANCED, "2026-10-01").first().size)
    }

    @Test
    fun `available dates are replaced per collection and listed newest first`() = runTest {
        epicDao.replaceAvailableDates(NATURAL, listOf(date("2026-10-14"), date("2026-10-16")))
        epicDao.replaceAvailableDates(ENHANCED, listOf(date("2026-10-13", ENHANCED)))

        epicDao.observeAvailableDates(NATURAL).test {
            assertEquals(listOf("2026-10-16", "2026-10-14"), awaitItem())

            epicDao.replaceAvailableDates(NATURAL, listOf(date("2026-10-17")))
            assertEquals(listOf("2026-10-17"), awaitItem())
        }
        assertTrue(epicDao.isDateAvailable(ENHANCED, "2026-10-13"))
        assertFalse(epicDao.isDateAvailable(ENHANCED, "2026-10-14"))
    }

    private val EpicImageEntity.hour: Long get() = (capturedAtEpochMillis / HOUR_MILLIS) % HOURS_PER_DAY

    private fun image(date: String, hour: Int, collection: String = NATURAL): EpicImageEntity {
        val dayStart = LocalDate.parse(date).toEpochDay() * HOURS_PER_DAY * HOUR_MILLIS
        val stamp = date.replace("-", "") + "%02d".format(hour)
        return EpicImageEntity(
            collection = collection,
            identifier = stamp,
            date = date,
            capturedAtEpochMillis = dayStart + hour * HOUR_MILLIS,
            caption = "",
            centroidLatitude = 0.0,
            centroidLongitude = 0.0,
            thumbnailUrl = "https://epic.gsfc.nasa.gov/thumbs/$stamp.jpg",
            imageUrl = "https://epic.gsfc.nasa.gov/jpg/$stamp.jpg",
            cachedAtEpochMillis = 0L,
        )
    }

    private fun date(date: String, collection: String = NATURAL) = EpicAvailableDateEntity(collection, date)

    private companion object {
        const val NATURAL = "natural"
        const val ENHANCED = "enhanced"
        const val HOUR_MILLIS = 3_600_000L
        const val HOURS_PER_DAY = 24L
    }
}
