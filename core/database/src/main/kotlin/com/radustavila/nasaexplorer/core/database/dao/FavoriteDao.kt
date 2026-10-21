package com.radustavila.nasaexplorer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.radustavila.nasaexplorer.core.database.entity.FavoriteEntity
import com.radustavila.nasaexplorer.core.database.entity.FavoriteLocalImage
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorite ORDER BY saved_at_epoch_millis DESC, item_id DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT item_id FROM favorite WHERE source = :source")
    fun observeIds(source: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE source = :source AND item_id = :itemId)")
    fun observeIsFavorite(source: String, itemId: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM favorite")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM favorite WHERE source = :source AND item_id = :itemId")
    suspend fun find(source: String, itemId: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(entity: FavoriteEntity): Long

    /** Refreshes the snapshot; a different image invalidates the offline copy of the old one. */
    @Query(
        "UPDATE favorite SET title = :title, " +
            "local_image_path = CASE WHEN image_url IS :imageUrl THEN local_image_path ELSE NULL END, " +
            "local_image_bytes = CASE WHEN image_url IS :imageUrl THEN local_image_bytes ELSE 0 END, " +
            "image_url = :imageUrl " +
            "WHERE source = :source AND item_id = :itemId",
    )
    suspend fun updateSnapshot(source: String, itemId: String, title: String, imageUrl: String?)

    /** Saves a new favourite, or refreshes the snapshot of an existing one without moving it. */
    @Transaction
    suspend fun save(entity: FavoriteEntity) {
        if (insertIgnoringExisting(entity) == NOT_INSERTED) {
            updateSnapshot(entity.source, entity.itemId, entity.title, entity.imageUrl)
        }
    }

    @Query("DELETE FROM favorite WHERE source = :source AND item_id = :itemId")
    suspend fun delete(source: String, itemId: String): Int

    /**
     * Records the offline copy of a favourite's image. Matches on [imageUrl] too, so a copy of an
     * image the snapshot no longer shows is never recorded.
     *
     * @return 1 when recorded, 0 when the favourite is gone or now shows another image.
     */
    @Query(
        "UPDATE favorite SET local_image_path = :localImagePath, local_image_bytes = :localImageBytes " +
            "WHERE source = :source AND item_id = :itemId AND image_url = :imageUrl",
    )
    suspend fun setLocalImage(
        source: String,
        itemId: String,
        imageUrl: String,
        localImagePath: String,
        localImageBytes: Long,
    ): Int

    /** Favourites with an image but no offline copy of it yet. */
    @Query("SELECT * FROM favorite WHERE image_url IS NOT NULL AND local_image_path IS NULL")
    suspend fun findWithoutLocalImage(): List<FavoriteEntity>

    /** Every recorded offline copy, by the remote image it stands in for. */
    @Query(
        "SELECT image_url, local_image_path FROM favorite " +
            "WHERE image_url IS NOT NULL AND local_image_path IS NOT NULL",
    )
    fun observeLocalImages(): Flow<List<FavoriteLocalImage>>

    @Query("SELECT COALESCE(SUM(local_image_bytes), 0) FROM favorite WHERE local_image_path IS NOT NULL")
    fun observeLocalImageBytes(): Flow<Long>

    private companion object {
        const val NOT_INSERTED = -1L
    }
}
