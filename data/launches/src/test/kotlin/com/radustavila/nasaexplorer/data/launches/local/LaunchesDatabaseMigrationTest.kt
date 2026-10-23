package com.radustavila.nasaexplorer.data.launches.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.core.model.LaunchTimeline
import com.radustavila.nasaexplorer.core.model.NetPrecision
import com.radustavila.nasaexplorer.data.launches.mapper.toDomain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Version 5 (Launches screen, F27) migrates automatically: the card keeps its cached launches
 * across the update instead of spending a call of the hourly budget to get them back, and the crew
 * list keeps its people. Opening through Room also checks the migrated schema against the entities.
 */
@RunWith(RobolectricTestRunner::class)
class LaunchesDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: LaunchesDatabase? = null

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
    fun `migrating from 4 to 5 keeps the launches as upcoming ones without details`() = runTest {
        createVersion4Database()

        val migrated = Room.databaseBuilder(context, LaunchesDatabase::class.java, TEST_DB_NAME)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

        val launch = migrated.launchDao().observeUpcoming().first().single().toDomain()
        assertEquals("Falcon 9 Block 5 | Bandwagon 5", launch.name)
        assertEquals(LaunchStatus.GO, launch.status)
        assertEquals(LaunchTimeline.UPCOMING, launch.timeline)
        assertEquals(NetPrecision.UNKNOWN, launch.netPrecision)
        assertTrue(launch.boosters.isEmpty())
        assertTrue(launch.webcasts.isEmpty())
        assertNull(launch.mission)
        assertEquals(0, launch.crewCount)
        assertTrue(migrated.launchDao().observePast().first().isEmpty())
        assertEquals(1, migrated.personInSpaceDao().observeAll().first().size)
    }

    /** The schema exactly as version 4 shipped it (`schemas/…/4.json`), with Room's identity row. */
    private fun createVersion4Database() {
        val file = context.getDatabasePath(TEST_DB_NAME)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `launches` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`net_epoch_millis` INTEGER NOT NULL, `status_id` INTEGER NOT NULL, `rocket` TEXT NOT NULL, " +
                    "`pad` TEXT NOT NULL, `provider` TEXT, `image_url` TEXT, `livestream_url` TEXT, " +
                    "`page_url` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `people_in_space` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                    "`agency` TEXT, `nationality` TEXT, `image_url` TEXT, `launched_epoch_millis` INTEGER, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)",
                arrayOf(VERSION_4_IDENTITY_HASH),
            )
            db.execSQL(
                "INSERT INTO launches VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "3b430f81-0ff1-4d85-8a1a-cef0de326198", "Falcon 9 Block 5 | Bandwagon 5", 1_792_405_800_000L, 1,
                    "Falcon 9 Block 5", "SLC-40, Cape Canaveral SFS, FL, USA", "SpaceX", null, null,
                    "https://spacelaunchnow.app/launch/falcon-9-block-5-bandwagon-5/",
                ),
            )
            db.execSQL(
                "INSERT INTO people_in_space VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(24, "Jessica Watkins", "NASA", "American", null, null),
            )
            db.version = 4
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "launches-migration-test.db"
        const val VERSION_4_IDENTITY_HASH = "40aec5e1008db4d306818459ea5beea3"
    }
}
