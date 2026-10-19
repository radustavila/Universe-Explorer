package com.radustavila.nasaexplorer.data.launches.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One person in orbit. Like `launches`, the table is replaced wholesale on every refresh. */
@Entity(tableName = "people_in_space")
data class PersonInSpaceEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val agency: String?,
    val nationality: String?,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "launched_epoch_millis") val launchedEpochMillis: Long?,
)
