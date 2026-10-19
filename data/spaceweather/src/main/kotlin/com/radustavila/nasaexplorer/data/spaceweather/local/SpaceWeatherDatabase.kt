package com.radustavila.nasaexplorer.data.spaceweather.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Space weather lives in its own small database, apart from `NasaDatabase`: a disposable mirror of
 * NOAA and DONKI with no user data (alert settings are kept in DataStore), so a schema change may
 * simply rebuild it and never competes with the APOD schema's versions.
 */
@Database(
    entities = [
        KpReadingEntity::class,
        CurrentKpEntity::class,
        AuroraForecastEntity::class,
        SolarFlareEntity::class,
        CoronalMassEjectionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SpaceWeatherDatabase : RoomDatabase() {
    abstract fun spaceWeatherDao(): SpaceWeatherDao

    companion object {
        const val NAME = "space_weather.db"
    }
}
