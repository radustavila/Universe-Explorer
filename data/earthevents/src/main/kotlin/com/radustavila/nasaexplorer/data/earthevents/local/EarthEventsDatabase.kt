package com.radustavila.nasaexplorer.data.earthevents.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The events cache lives in its own small database rather than in `NasaDatabase`: it is a mirror
 * of a public feed with no user data at all, so a schema change may simply rebuild it (one
 * refresh), and it never competes with the APOD schema's versions.
 *
 * Version 2 added the USGS earthquakes (F19); the migration only creates their tables.
 */
@Database(
    entities = [
        EarthEventEntity::class,
        ImageryLayerEntity::class,
        EarthquakeEntity::class,
        EarthquakeFeedEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class EarthEventsDatabase : RoomDatabase() {
    abstract fun earthEventDao(): EarthEventDao

    abstract fun earthquakeDao(): EarthquakeDao

    companion object {
        const val NAME = "earth_events.db"
    }
}
