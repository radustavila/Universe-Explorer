package com.radustavila.nasaexplorer.data.webb.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The Webb schedule lives in its own database: a disposable copy of STScI's newest week with no user
 * data, so a schema change may simply rebuild it.
 */
@Database(entities = [WebbVisitEntity::class], version = 1, exportSchema = true)
abstract class WebbDatabase : RoomDatabase() {
    abstract fun webbDao(): WebbDao

    companion object {
        const val NAME = "webb.db"
    }
}
