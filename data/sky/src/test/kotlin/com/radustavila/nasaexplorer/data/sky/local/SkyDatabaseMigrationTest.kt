package com.radustavila.nasaexplorer.data.sky.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Version 2 (more satellites) migrates automatically. The ISS's cached elements must survive it:
 * a rebuilt database would leave the ISS card empty until its next due refresh. Opening through Room
 * also checks the migrated schema against the entities.
 */
@RunWith(RobolectricTestRunner::class)
class SkyDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: SkyDatabase? = null

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
    fun `migrating from 1 to 2 keeps the ISS elements`() = runTest {
        createVersion1Database()

        val dao = Room.databaseBuilder(context, SkyDatabase::class.java, TEST_DB_NAME)
            .allowMainThreadQueries()
            .build()
            .also { database = it }
            .orbitalElementsDao()

        val iss = requireNotNull(dao.observe(ISS).first())
        assertEquals("ISS (ZARYA)", iss.name)
        assertEquals(15.48934931, iss.meanMotion, 0.0)
        assertNull(iss.objectId)
        assertEquals(emptyList<OrbitalElementsEntity>(), dao.observeSet("BRIGHT_SATELLITES").first())
    }

    /** The schema exactly as version 1 shipped it (`schemas/…/1.json`), with Room's identity row. */
    private fun createVersion1Database() {
        val file = context.getDatabasePath(TEST_DB_NAME)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `orbital_elements` (`norad_id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                    "`epoch_millis` INTEGER NOT NULL, `mean_motion` REAL NOT NULL, `eccentricity` REAL NOT NULL, " +
                    "`inclination_degrees` REAL NOT NULL, `raan_degrees` REAL NOT NULL, " +
                    "`arg_of_pericenter_degrees` REAL NOT NULL, `mean_anomaly_degrees` REAL NOT NULL, " +
                    "`bstar` REAL NOT NULL, `fetched_at_millis` INTEGER NOT NULL, PRIMARY KEY(`norad_id`))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)",
                arrayOf(VERSION_1_IDENTITY_HASH),
            )
            db.execSQL(
                "INSERT INTO orbital_elements VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    ISS, "ISS (ZARYA)", 1_792_383_285_011L, 15.48934931, 0.0006763, 51.6325, 46.5189, 282.0427,
                    77.9804, 0.00013772, 1_792_400_000_000L,
                ),
            )
            db.version = 1
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "sky-migration-test.db"
        const val VERSION_1_IDENTITY_HASH = "12f67857835f9d16cef95023adeeac28"
        const val ISS = 25544
    }
}
