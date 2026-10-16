package com.radustavila.nasaexplorer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Preferences DataStore implementation of the synchronisation bookkeeping contract.
 *
 * Corrupted or unreadable preferences degrade to "nothing recorded yet" instead of crashing the
 * observing screen.
 */
@Singleton
class SyncMetadataDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SyncMetadataRepository {

    override val lastSuccessfulSync: Flow<Instant?> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { preferences ->
            preferences[LAST_SUCCESSFUL_SYNC]?.let(Instant::ofEpochMilli)
        }

    override val oldestRequestedDate: Flow<LocalDate?> = observeDate(OLDEST_REQUESTED_DATE)

    override val lastAnnouncedApodDate: Flow<LocalDate?> = observeDate(LAST_ANNOUNCED_APOD_DATE)

    override suspend fun setLastSuccessfulSync(instant: Instant) {
        editSafely { preferences -> preferences[LAST_SUCCESSFUL_SYNC] = instant.toEpochMilli() }
    }

    override suspend fun setOldestRequestedDate(date: LocalDate) {
        editSafely { preferences -> preferences[OLDEST_REQUESTED_DATE] = date.toString() }
    }

    override suspend fun setLastAnnouncedApodDate(date: LocalDate) {
        editSafely { preferences -> preferences[LAST_ANNOUNCED_APOD_DATE] = date.toString() }
    }

    private fun observeDate(key: Preferences.Key<String>): Flow<LocalDate?> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { preferences ->
            preferences[key]?.let { raw ->
                runCatching { LocalDate.parse(raw) }
                    .getOrElse { error -> if (error is DateTimeParseException) null else throw error }
            }
        }

    private suspend fun editSafely(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (_: IOException) {
            // Losing sync bookkeeping is recoverable: the next sync simply re-fetches the window.
        }
    }

    private companion object {
        val LAST_SUCCESSFUL_SYNC = longPreferencesKey("last_successful_sync_epoch_millis")
        val OLDEST_REQUESTED_DATE = stringPreferencesKey("oldest_requested_date")
        val LAST_ANNOUNCED_APOD_DATE = stringPreferencesKey("last_announced_apod_date")
    }
}
