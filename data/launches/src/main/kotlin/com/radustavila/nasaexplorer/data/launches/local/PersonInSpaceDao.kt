package com.radustavila.nasaexplorer.data.launches.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonInSpaceDao {

    /** Longest in orbit first, then by name. */
    @Query("SELECT * FROM people_in_space ORDER BY launched_epoch_millis IS NULL, launched_epoch_millis ASC, name ASC")
    fun observeAll(): Flow<List<PersonInSpaceEntity>>

    @Transaction
    suspend fun replaceAll(people: List<PersonInSpaceEntity>) {
        deleteAll()
        insertAll(people)
    }

    @Query("DELETE FROM people_in_space")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(people: List<PersonInSpaceEntity>)
}
