package com.radustavila.nasaexplorer.data.dsn.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The DSN cache lives in its own database: a disposable mirror of DSN Now and Horizons with no
 * user data, so a schema change may simply rebuild it.
 */
@Database(
    entities = [
        DsnDishEntity::class,
        DsnLinkEntity::class,
        DsnSignalEntity::class,
        DsnSpacecraftEntity::class,
        DsnDishInfoEntity::class,
        ProbeDistanceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DsnDatabase : RoomDatabase() {
    abstract fun dsnDao(): DsnDao

    companion object {
        const val NAME = "dsn.db"
    }
}
