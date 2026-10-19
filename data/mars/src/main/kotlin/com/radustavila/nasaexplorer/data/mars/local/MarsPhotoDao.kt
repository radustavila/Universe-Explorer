package com.radustavila.nasaexplorer.data.mars.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MarsPhotoDao {

    /** A rover's photos, newest sol first, then newest shot first; at most [limit]. */
    @Query(
        """
        SELECT * FROM mars_photo WHERE rover = :rover
        ORDER BY sol DESC, taken_at DESC, id
        LIMIT :limit
        """,
    )
    fun observeRover(rover: String, limit: Int = -1): Flow<List<MarsPhotoEntity>>

    @Query("SELECT * FROM mars_photo WHERE id = :id")
    fun observePhoto(id: String): Flow<MarsPhotoEntity?>

    @Upsert
    suspend fun upsert(photos: List<MarsPhotoEntity>)

    /**
     * Deletes photos no download has included since [beforeMillis], except favourites ([keepIds],
     * which live in another database) and each rover's newest sol, so a long time offline never
     * leaves the rail empty.
     */
    @Query(
        """
        DELETE FROM mars_photo
        WHERE cached_at < :beforeMillis
        AND id NOT IN (:keepIds)
        AND sol < (SELECT MAX(newest.sol) FROM mars_photo AS newest WHERE newest.rover = mars_photo.rover)
        """,
    )
    suspend fun evictStale(beforeMillis: Long, keepIds: List<String>): Int

    @Query("SELECT COUNT(*) FROM mars_photo")
    suspend fun count(): Int
}
