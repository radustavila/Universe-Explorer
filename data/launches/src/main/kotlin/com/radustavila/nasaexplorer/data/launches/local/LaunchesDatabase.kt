package com.radustavila.nasaexplorer.data.launches.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The launches cache lives in its own small database rather than in `NasaDatabase`: it is a
 * disposable mirror of a third-party list with no user data, so it needs no migrations of its own
 * (a schema change may simply rebuild it) and never competes with the APOD schema's versions.
 */
@Database(
    entities = [LaunchEntity::class, LaunchRefreshEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LaunchesDatabase : RoomDatabase() {
    abstract fun launchDao(): LaunchDao

    companion object {
        const val NAME = "launches.db"
    }
}
