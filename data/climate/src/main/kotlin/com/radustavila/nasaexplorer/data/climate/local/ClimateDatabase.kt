package com.radustavila.nasaexplorer.data.climate.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The climate records live in their own database: a disposable copy of four public files with no
 * user data, so a schema change may simply rebuild it.
 */
@Database(entities = [ClimatePointEntity::class], version = 1, exportSchema = true)
abstract class ClimateDatabase : RoomDatabase() {
    abstract fun climateDao(): ClimateDao

    companion object {
        const val NAME = "climate.db"
    }
}
