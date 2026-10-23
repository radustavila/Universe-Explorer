package com.radustavila.nasaexplorer.data.earthevents.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.testing.data.TestEarthquakes
import com.radustavila.nasaexplorer.data.earthevents.mapper.toEntity
import com.radustavila.nasaexplorer.data.earthevents.remote.EarthquakeFeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Version 2 (earthquakes, F19) migrates automatically: the cached events must survive it, or the
 * map would be empty until the next refresh. Opening through Room also checks the migrated schema
 * against the entities.
 */
@RunWith(RobolectricTestRunner::class)
class EarthEventsDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: EarthEventsDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(TEST_DB_NAME)
    }

    @Test
    fun `migrating from 1 to 2 keeps the events and adds empty quake tables`() = runTest {
        createVersion1Database()

        val migrated = Room.databaseBuilder(context, EarthEventsDatabase::class.java, TEST_DB_NAME)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

        assertEquals(listOf("EONET_25310"), migrated.earthEventDao().observeEvents().first().map { it.id })
        val quakes = migrated.earthquakeDao()
        assertEquals(emptyList<EarthquakeEntity>(), quakes.observeEarthquakes().first())

        val quake = TestEarthquakes.earthquake().toEntity(EarthquakeFeed.MAGNITUDE_2_5_WEEK)
        quakes.replaceFeed(EarthquakeFeedEntity(quake.feed, lastModified = null, fetchedAtMillis = 0), listOf(quake))
        assertEquals(listOf(quake), quakes.observeEarthquakes().first())
    }

    /** The schema exactly as version 1 shipped it (`schemas/…/1.json`), with Room's identity row. */
    private fun createVersion1Database() {
        val file = context.getDatabasePath(TEST_DB_NAME)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `earth_event` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`category` TEXT NOT NULL, `closed_at` INTEGER, `last_update` INTEGER NOT NULL, " +
                    "`sources` TEXT NOT NULL, `geometry` TEXT NOT NULL, `cached_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_earth_event_last_update` ON `earth_event` (`last_update`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `imagery_layer` (`category` TEXT NOT NULL, `layer_id` TEXT NOT NULL, " +
                    "`position` INTEGER NOT NULL, `tile_matrix_set` TEXT NOT NULL, `file_extension` TEXT NOT NULL, " +
                    "`cached_at` INTEGER NOT NULL, PRIMARY KEY(`category`, `layer_id`))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)",
                arrayOf(VERSION_1_IDENTITY_HASH),
            )
            db.execSQL(
                "INSERT INTO earth_event VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "EONET_25310", "Prescribed Fire Newman Piles RX", "wildfires", null, 1_792_400_000_000L,
                    "[]", """[{"date":1792400000000,"point":[35.0,-111.6]}]""", 1_792_400_000_000L,
                ),
            )
            db.version = 1
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "earth-events-migration-test.db"
        const val VERSION_1_IDENTITY_HASH = "956086c0afe84c8974826face56770fb"
    }
}
