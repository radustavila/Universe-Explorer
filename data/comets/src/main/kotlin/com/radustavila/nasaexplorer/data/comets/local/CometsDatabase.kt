package com.radustavila.nasaexplorer.data.comets.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The comets cache lives in its own database: a disposable mirror of SBDB and Horizons with no user
 * data, so a schema change may simply rebuild it.
 */
@Database(
    entities = [CometEntity::class, CometEphemerisEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CometsDatabase : RoomDatabase() {
    abstract fun cometsDao(): CometsDao

    companion object {
        const val NAME = "comets.db"
    }
}
