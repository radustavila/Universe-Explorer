package com.radustavila.nasaexplorer.data.launches.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The Launch Library 2 cache (upcoming and past launches, people in space) lives in its own small
 * database rather than in `NasaDatabase`: it is a mirror of a third-party list with no user data,
 * and never competes with the APOD schema's versions. Versions before 4 are simply rebuilt; from 4
 * on it migrates, so the card keeps its launches across an update without spending a call of the
 * hourly budget to get them back.
 */
@Database(
    entities = [LaunchEntity::class, PersonInSpaceEntity::class],
    // 2: refresh bookkeeping moved to the shared per-source sync metadata.
    // 3: people in space (Sky tonight).
    // 4: launch service provider (next-launch widget).
    // 5: launch details, past launches and the timeline column (Launches screen, F27).
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 4, to = 5)],
)
abstract class LaunchesDatabase : RoomDatabase() {
    abstract fun launchDao(): LaunchDao
    abstract fun personInSpaceDao(): PersonInSpaceDao

    companion object {
        const val NAME = "launches.db"
    }
}
