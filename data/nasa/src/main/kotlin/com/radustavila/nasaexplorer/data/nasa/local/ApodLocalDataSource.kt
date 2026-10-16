package com.radustavila.nasaexplorer.data.nasa.local

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.database.entity.ApodEntity
import com.radustavila.nasaexplorer.core.model.Apod
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Local boundary of the NASA data module: the single source of truth the UI observes.
 *
 * Reads are hot [Flow]s; writes return [DataResult] so database failures are surfaced as
 * application errors rather than thrown SQLite exceptions.
 */
interface ApodLocalDataSource {
    fun observeFeed(): Flow<List<Apod>>
    fun observeLatest(): Flow<Apod?>
    fun observeApod(date: LocalDate): Flow<Apod?>
    fun observeFavorites(): Flow<List<Apod>>
    fun observeCount(): Flow<Int>
    fun observeFavoritesCount(): Flow<Int>
    fun observeOldestDate(): Flow<LocalDate?>

    suspend fun upsertPreservingLocalState(entities: List<ApodEntity>): DataResult<Int>
    suspend fun setFavorite(date: LocalDate, isFavorite: Boolean): DataResult<Unit>
}
