package com.radustavila.nasaexplorer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.radustavila.nasaexplorer.core.database.entity.ApodEntity
import com.radustavila.nasaexplorer.core.database.entity.ApodRemoteContent
import com.radustavila.nasaexplorer.core.database.entity.toRemoteContent
import kotlinx.coroutines.flow.Flow

@Dao
interface ApodDao {

    /** Only the contiguous window the feed pages through; see [ApodEntity.isInFeed]. */
    @Query("SELECT * FROM apods WHERE is_in_feed = 1 ORDER BY date DESC")
    fun observeFeed(): Flow<List<ApodEntity>>

    @Query("SELECT * FROM apods WHERE date = :date")
    fun observeApod(date: String): Flow<ApodEntity?>

    @Query("SELECT * FROM apods WHERE is_favorite = 1 ORDER BY date DESC")
    fun observeFavorites(): Flow<List<ApodEntity>>

    @Query("SELECT COUNT(*) FROM apods")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM apods WHERE is_favorite = 1")
    fun observeFavoritesCount(): Flow<Int>

    @Query("SELECT MIN(date) FROM apods")
    fun observeOldestDate(): Flow<String?>

    @Query("SELECT MIN(date) FROM apods")
    suspend fun oldestDate(): String?

    @Query("SELECT MAX(date) FROM apods")
    suspend fun newestDate(): String?

    @Query("SELECT * FROM apods WHERE date = :date")
    suspend fun findByDate(date: String): ApodEntity?

    @Query("UPDATE apods SET is_favorite = :isFavorite WHERE date = :date")
    suspend fun setFavorite(date: String, isFavorite: Boolean): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(entities: List<ApodEntity>): List<Long>

    /** Updates only the remote-owned columns; `is_favorite` is not part of the projection. */
    @Update(entity = ApodEntity::class)
    suspend fun updateRemoteContent(content: List<ApodRemoteContent>)

    /**
     * Promotes rows into the feed window. Deliberately one-way: a day the calendar pulled in on its
     * own must join the feed once paging reaches it, but must never drop back out of it.
     */
    @Query("UPDATE apods SET is_in_feed = 1 WHERE date IN (:dates)")
    suspend fun markInFeed(dates: List<String>)

    /**
     * Merges a freshly fetched window into the cache.
     *
     * Insert-ignore first, then update the rows that already existed, using a projection without
     * `is_favorite`. The result: no duplicates, remote edits are picked up, and local favourites
     * survive every sync.
     *
     * Rows arriving with [ApodEntity.isInFeed] set also join the feed window, which is how a day
     * cached on its own from the calendar is absorbed once paging catches up with it.
     *
     * @return number of newly inserted rows.
     */
    @Transaction
    suspend fun upsertPreservingLocalState(entities: List<ApodEntity>): Int {
        if (entities.isEmpty()) return 0

        val insertedRowIds = insertIgnoringExisting(entities)
        val alreadyPresent = entities.filterIndexed { index, _ ->
            insertedRowIds.getOrElse(index) { NOT_INSERTED } == NOT_INSERTED
        }
        if (alreadyPresent.isNotEmpty()) {
            updateRemoteContent(alreadyPresent.map(ApodEntity::toRemoteContent))

            val feedDates = alreadyPresent.filter(ApodEntity::isInFeed).map(ApodEntity::date)
            if (feedDates.isNotEmpty()) markInFeed(feedDates)
        }
        return insertedRowIds.count { it != NOT_INSERTED }
    }

    @Query("DELETE FROM apods")
    suspend fun clear()

    private companion object {
        const val NOT_INSERTED = -1L
    }
}
