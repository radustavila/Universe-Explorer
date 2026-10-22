package com.radustavila.nasaexplorer.data.forecast.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The MET Norway cache lives in its own database: a disposable mirror with no user data, so a
 * schema change may simply rebuild it.
 */
@Database(
    entities = [ForecastPlaceEntity::class, ForecastStepEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ForecastDatabase : RoomDatabase() {
    abstract fun forecastDao(): ForecastDao

    companion object {
        const val NAME = "forecast.db"
    }
}
