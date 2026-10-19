package com.radustavila.nasaexplorer.data.smallbodies.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * JPL's close approaches, Sentry list, fireballs and looked-up objects, in their own database apart
 * from `NasaDatabase`: a disposable mirror with no user data, rebuilt on a schema change.
 */
@Database(
    entities = [
        CloseApproachEntity::class,
        SentryObjectEntity::class,
        FireballEntity::class,
        SmallBodyDetailsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SmallBodiesDatabase : RoomDatabase() {
    abstract fun smallBodiesDao(): SmallBodiesDao

    companion object {
        const val NAME = "small_bodies.db"
    }
}
