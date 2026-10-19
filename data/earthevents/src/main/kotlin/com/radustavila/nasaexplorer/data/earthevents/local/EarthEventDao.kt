package com.radustavila.nasaexplorer.data.earthevents.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EarthEventDao {

    @Query("SELECT * FROM earth_event ORDER BY last_update DESC, id")
    abstract fun observeEvents(): Flow<List<EarthEventEntity>>

    @Query("DELETE FROM earth_event")
    protected abstract suspend fun clearEvents()

    @Insert
    protected abstract suspend fun insertEvents(events: List<EarthEventEntity>)

    /**
     * Installs a fresh list in one transaction, so observers never see a half-refreshed map, and
     * events that closed since the last refresh disappear.
     */
    @Transaction
    open suspend fun replaceEvents(events: List<EarthEventEntity>) {
        clearEvents()
        insertEvents(events)
    }

    @Query("DELETE FROM earth_event WHERE last_update < :beforeMillis")
    abstract suspend fun deleteEventsUpdatedBefore(beforeMillis: Long): Int

    @Query("SELECT * FROM imagery_layer WHERE category = :category ORDER BY position")
    abstract suspend fun layersFor(category: String): List<ImageryLayerEntity>

    @Query("DELETE FROM imagery_layer WHERE category = :category")
    protected abstract suspend fun clearLayers(category: String)

    @Insert
    protected abstract suspend fun insertLayers(layers: List<ImageryLayerEntity>)

    @Transaction
    open suspend fun replaceLayers(category: String, layers: List<ImageryLayerEntity>) {
        clearLayers(category)
        insertLayers(layers)
    }

    @Query("DELETE FROM imagery_layer WHERE cached_at < :beforeMillis")
    abstract suspend fun deleteLayersCachedBefore(beforeMillis: Long): Int
}
