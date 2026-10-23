package com.radustavila.nasaexplorer.data.earthevents.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EarthquakeDao {

    @Query("SELECT * FROM earthquake ORDER BY time DESC, id")
    abstract fun observeEarthquakes(): Flow<List<EarthquakeEntity>>

    @Query("SELECT * FROM earthquake_feed WHERE feed = :feed")
    abstract suspend fun feed(feed: String): EarthquakeFeedEntity?

    @Query("SELECT COUNT(*) FROM earthquake WHERE feed = :feed")
    abstract suspend fun countIn(feed: String): Int

    @Query("DELETE FROM earthquake WHERE feed = :feed")
    protected abstract suspend fun clearFeed(feed: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertEarthquakes(quakes: List<EarthquakeEntity>)

    @Upsert
    protected abstract suspend fun upsertFeed(feed: EarthquakeFeedEntity)

    /**
     * Installs a feed's fresh list and its `Last-Modified` in one transaction, so observers never
     * see a half-refreshed map and quakes that left the feed's window disappear.
     */
    @Transaction
    open suspend fun replaceFeed(feed: EarthquakeFeedEntity, quakes: List<EarthquakeEntity>) {
        clearFeed(feed.feed)
        insertEarthquakes(quakes)
        upsertFeed(feed)
    }

    @Query("DELETE FROM earthquake WHERE time < :beforeMillis")
    abstract suspend fun deleteEarthquakesBefore(beforeMillis: Long): Int
}
