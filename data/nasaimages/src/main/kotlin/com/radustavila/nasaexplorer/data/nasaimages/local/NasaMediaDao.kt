package com.radustavila.nasaexplorer.data.nasaimages.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NasaMediaDao {

    /** Every rail's items, grouped by rail and in rail order. */
    @Query(
        """
        SELECT rail_item.rail AS rail, nasa_media.* FROM rail_item
        INNER JOIN nasa_media ON nasa_media.nasa_id = rail_item.nasa_id
        ORDER BY rail_item.rail, rail_item.position
        """,
    )
    abstract fun observeRailRows(): Flow<List<RailMediaRow>>

    @Query("SELECT * FROM nasa_media WHERE nasa_id = :nasaId")
    abstract fun observeMedia(nasaId: String): Flow<NasaMediaEntity?>

    @Upsert
    abstract suspend fun upsertMedia(items: List<NasaMediaEntity>)

    @Query("DELETE FROM rail_item WHERE rail = :rail")
    protected abstract suspend fun clearRail(rail: String)

    @Insert
    protected abstract suspend fun insertRailItems(items: List<RailItemEntity>)

    /**
     * Installs fresh rails in one transaction, so observers never see a half-refreshed tab. Rails
     * not in [rails] keep their rows.
     */
    @Transaction
    open suspend fun replaceRails(media: List<NasaMediaEntity>, rails: Map<String, List<RailItemEntity>>) {
        upsertMedia(media)
        rails.forEach { (rail, items) ->
            clearRail(rail)
            insertRailItems(items)
        }
    }

    /**
     * Deletes items last written before [beforeMillis] that are on no rail and not in [keepIds]
     * (the favourites, which live in another database).
     */
    @Query(
        """
        DELETE FROM nasa_media
        WHERE cached_at < :beforeMillis
        AND nasa_id NOT IN (SELECT nasa_id FROM rail_item)
        AND nasa_id NOT IN (:keepIds)
        """,
    )
    abstract suspend fun evictUnreferenced(beforeMillis: Long, keepIds: List<String>): Int

    @Query("SELECT COUNT(*) FROM nasa_media")
    abstract suspend fun count(): Int
}
