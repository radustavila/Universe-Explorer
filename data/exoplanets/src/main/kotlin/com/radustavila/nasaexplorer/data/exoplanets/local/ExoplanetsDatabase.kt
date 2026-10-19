package com.radustavila.nasaexplorer.data.exoplanets.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The exoplanet cache lives in its own database rather than in `NasaDatabase`: it is a disposable
 * mirror of the archive with no user data (favourites live in the shared `favorite` table), so a
 * schema change may simply rebuild it, and it never competes with the APOD schema's versions.
 */
@Database(
    entities = [ExoplanetEntity::class, ExoplanetCatalogStatsEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ExoplanetsDatabase : RoomDatabase() {
    abstract fun exoplanetDao(): ExoplanetDao

    companion object {
        const val NAME = "exoplanets.db"
    }
}
