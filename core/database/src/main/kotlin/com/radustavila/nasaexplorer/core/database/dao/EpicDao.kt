package com.radustavila.nasaexplorer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.radustavila.nasaexplorer.core.database.entity.EpicAvailableDateEntity
import com.radustavila.nasaexplorer.core.database.entity.EpicImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpicDao {

    @Query(
        "SELECT * FROM epic_images WHERE collection = :collection AND date = " +
            "(SELECT MAX(date) FROM epic_images WHERE collection = :collection) " +
            "ORDER BY captured_at_epoch_millis",
    )
    fun observeLatestDay(collection: String): Flow<List<EpicImageEntity>>

    @Query(
        "SELECT * FROM epic_images WHERE collection = :collection AND date = :date " +
            "ORDER BY captured_at_epoch_millis",
    )
    fun observeDay(collection: String, date: String): Flow<List<EpicImageEntity>>

    @Query(
        "SELECT * FROM epic_images WHERE collection = :collection AND date = " +
            "(SELECT MAX(date) FROM epic_images WHERE collection = :collection) " +
            "ORDER BY captured_at_epoch_millis",
    )
    suspend fun latestDay(collection: String): List<EpicImageEntity>

    @Query("SELECT date FROM epic_available_dates WHERE collection = :collection ORDER BY date DESC")
    fun observeAvailableDates(collection: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM epic_available_dates WHERE collection = :collection AND date = :date)")
    suspend fun isDateAvailable(collection: String, date: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<EpicImageEntity>)

    @Query("DELETE FROM epic_images WHERE collection = :collection AND date = :date")
    suspend fun deleteDay(collection: String, date: String)

    @Query("DELETE FROM epic_images WHERE collection = :collection AND date < :date")
    suspend fun deleteDaysBefore(collection: String, date: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAvailableDates(dates: List<EpicAvailableDateEntity>)

    @Query("DELETE FROM epic_available_dates WHERE collection = :collection")
    suspend fun deleteAvailableDates(collection: String)

    /**
     * Replaces every day present in [images] and, when [keepFrom] is given, trims days of the
     * collection older than it, all in one transaction.
     *
     * EPIC re-processes and occasionally withdraws images, so a fresh listing of a day is
     * authoritative: rows it no longer contains go too.
     */
    @Transaction
    suspend fun replaceDays(collection: String, images: List<EpicImageEntity>, keepFrom: String?) {
        images.map(EpicImageEntity::date).distinct().forEach { date -> deleteDay(collection, date) }
        insertImages(images)
        if (keepFrom != null) deleteDaysBefore(collection, keepFrom)
    }

    /** Replaces the archive listing of one collection in one transaction, so readers never see it half-written. */
    @Transaction
    suspend fun replaceAvailableDates(collection: String, dates: List<EpicAvailableDateEntity>) {
        deleteAvailableDates(collection)
        insertAvailableDates(dates)
    }
}
