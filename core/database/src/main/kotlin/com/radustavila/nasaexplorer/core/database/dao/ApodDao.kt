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

    /**
     * Newest entry in the whole cache. Not limited to the feed window: a day the calendar fetched on
     * its own is still the latest one if nothing newer exists.
     */
    @Query("SELECT * FROM apods ORDER BY date DESC LIMIT 1")
    fun observeLatest(): Flow<ApodEntity?>

    @Query("SELECT * FROM apods WHERE date = :date")
    fun observeApod(date: String): Flow<ApodEntity?>

    /**
     * Cached entries whose title or explanation matches [match], newest first.
     *
     * [match] is an FTS4 `MATCH` expression; callers build it from user input rather than passing
     * that input through, so stray quotes or operators cannot break the query.
     */
    @Query(
        """
        SELECT apods.* FROM apods
        JOIN apods_fts ON apods.rowid = apods_fts.rowid
        WHERE apods_fts MATCH :match
        ORDER BY apods.date DESC
        LIMIT :limit
        """,
    )
    fun observeSearch(match: String, limit: Int): Flow<List<ApodEntity>>

    /**
     * Entries published on the same day of the year as [monthDay] (`MM-dd`) in other years, newest
     * first, excluding [excludedDate] itself. Dates are ISO-8601 text, so the month and day are its
     * last five characters.
     */
    @Query(
        """
        SELECT * FROM apods
        WHERE substr(date, 6) = :monthDay AND date < :excludedDate
        ORDER BY date DESC
        """,
    )
    fun observeSameDayBefore(monthDay: String, excludedDate: String): Flow<List<ApodEntity>>

    @Query("SELECT COUNT(*) FROM apods")
    fun observeCount(): Flow<Int>

    @Query("SELECT MIN(date) FROM apods")
    fun observeOldestDate(): Flow<String?>

    @Query("SELECT MIN(date) FROM apods")
    suspend fun oldestDate(): String?

    @Query("SELECT MAX(date) FROM apods")
    suspend fun newestDate(): String?

    @Query("SELECT * FROM apods WHERE date = :date")
    suspend fun findByDate(date: String): ApodEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(entities: List<ApodEntity>): List<Long>

    /** Updates only the remote-owned columns; `cached_at_epoch_millis` is not part of the projection. */
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
     * the locally owned columns. The result: no duplicates, remote edits are picked up, and the
     * first-cached timestamp survives every sync.
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

    /**
     * Takes favourites published before [cutoffDate] out of the feed window, so trimming the window
     * below cannot leave them behind as isolated rows in it. Paging re-admits them on its way back.
     */
    @Query(
        "UPDATE apods SET is_in_feed = 0 WHERE is_in_feed = 1 AND date < :cutoffDate " +
            "AND date IN (SELECT item_id FROM favorite WHERE source = :favoriteSource)",
    )
    suspend fun detachFavoritesFromFeedBefore(cutoffDate: String, favoriteSource: String)

    /**
     * Deletes non-favourite rows nobody needs offline any more: feed rows published before
     * [cutoffDate] (by date, so the feed window stays contiguous), and one-off days not refreshed
     * since [staleBeforeMillis].
     *
     * @return the number of deleted rows.
     */
    @Query(
        "DELETE FROM apods " +
            "WHERE date NOT IN (SELECT item_id FROM favorite WHERE source = :favoriteSource) " +
            "AND ((is_in_feed = 1 AND date < :cutoffDate) " +
            "OR (is_in_feed = 0 AND last_synced_at_epoch_millis < :staleBeforeMillis))",
    )
    suspend fun deleteEvictable(cutoffDate: String, staleBeforeMillis: Long, favoriteSource: String): Int

    @Transaction
    suspend fun evict(cutoffDate: String, staleBeforeMillis: Long, favoriteSource: String): Int {
        detachFavoritesFromFeedBefore(cutoffDate, favoriteSource)
        return deleteEvictable(cutoffDate, staleBeforeMillis, favoriteSource)
    }

    private companion object {
        const val NOT_INSERTED = -1L
    }
}
