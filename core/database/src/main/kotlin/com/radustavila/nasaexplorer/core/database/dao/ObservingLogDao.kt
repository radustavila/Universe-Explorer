package com.radustavila.nasaexplorer.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.radustavila.nasaexplorer.core.database.entity.ObservingLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservingLogDao {

    @Query("SELECT * FROM observing_log ORDER BY observed_on DESC, object_id")
    fun observeAll(): Flow<List<ObservingLogEntity>>

    @Upsert
    suspend fun upsert(entity: ObservingLogEntity)

    @Query("DELETE FROM observing_log WHERE object_id = :objectId")
    suspend fun delete(objectId: String): Int
}
