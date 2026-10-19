package com.radustavila.nasaexplorer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.radustavila.nasaexplorer.core.database.entity.FavoriteEntity
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

    @Query(
        "UPDATE favorite SET title = :title, image_url = :imageUrl " +
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

    private companion object {
        const val NOT_INSERTED = -1L
    }
}
