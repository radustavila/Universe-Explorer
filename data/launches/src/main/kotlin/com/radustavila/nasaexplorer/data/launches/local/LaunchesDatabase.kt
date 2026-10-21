package com.radustavila.nasaexplorer.data.launches.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The Launch Library 2 cache (upcoming launches, people in space) lives in its own small database rather than in `NasaDatabase`: it is a
 * disposable mirror of a third-party list with no user data, so it needs no migrations of its own
 * (a schema change may simply rebuild it) and never competes with the APOD schema's versions.
 */
@Database(
    entities = [LaunchEntity::class, PersonInSpaceEntity::class],
    // 2: refresh bookkeeping moved to the shared per-source sync metadata.
    // 3: people in space (Sky tonight).
    // 4: launch service provider (next-launch widget).
    version = 4,
    exportSchema = true,
)
abstract class LaunchesDatabase : RoomDatabase() {
    abstract fun launchDao(): LaunchDao
    abstract fun personInSpaceDao(): PersonInSpaceDao

    companion object {
        const val NAME = "launches.db"
    }
}
