package com.radustavila.nasaexplorer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.domain.model.SourceSyncStatus
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
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

    private val preferences: Flow<Preferences> = dataStore.data.catch { emit(emptyPreferences()) }

    override val sourceStatuses: Flow<List<SourceSyncStatus>> = preferences
        .map { preferences ->
            SyncSource.entries.map { source -> preferences.statusOf(source) }.filter { it.hasRecord }
        }
        .distinctUntilChanged()

    override fun observeSourceStatus(source: SyncSource): Flow<SourceSyncStatus> = preferences
        .map { preferences -> preferences.statusOf(source) }
        .distinctUntilChanged()

    override suspend fun recordSyncSuccess(
        source: SyncSource,
        at: Instant,
        nextAllowedRefreshAt: Instant?,
    ) {
        val keys = SourceKeys(source)
        editSafely { preferences ->
            preferences[keys.lastSuccess] = at.toEpochMilli()
            preferences.setOrRemove(keys.nextAllowed, nextAllowedRefreshAt?.toEpochMilli())
        }
    }

    override suspend fun recordSyncFailure(
        source: SyncSource,
        at: Instant,
        error: AppError,
        nextAllowedRefreshAt: Instant?,
    ) {
        val keys = SourceKeys(source)
        editSafely { preferences ->
            preferences[keys.lastFailure] = at.toEpochMilli()
            preferences[keys.lastError] = error.toString()
            preferences.setOrRemove(keys.nextAllowed, nextAllowedRefreshAt?.toEpochMilli())
        }
    }

    override val oldestRequestedDate: Flow<LocalDate?> = observeDate(OLDEST_REQUESTED_DATE)

    override val lastAnnouncedApodDate: Flow<LocalDate?> = observeDate(LAST_ANNOUNCED_APOD_DATE)

    override suspend fun setOldestRequestedDate(date: LocalDate) {
        editSafely { preferences -> preferences[OLDEST_REQUESTED_DATE] = date.toString() }
    }

    override suspend fun setLastAnnouncedApodDate(date: LocalDate) {
        editSafely { preferences -> preferences[LAST_ANNOUNCED_APOD_DATE] = date.toString() }
    }

    private fun Preferences.statusOf(source: SyncSource): SourceSyncStatus {
        val keys = SourceKeys(source)
        return SourceSyncStatus(
            source = source,
            lastSuccessAt = this[keys.lastSuccess]?.let(Instant::ofEpochMilli),
            lastFailureAt = this[keys.lastFailure]?.let(Instant::ofEpochMilli),
            lastErrorDescription = this[keys.lastError],
            nextAllowedRefreshAt = this[keys.nextAllowed]?.let(Instant::ofEpochMilli),
        )
    }

    private fun observeDate(key: Preferences.Key<String>): Flow<LocalDate?> = preferences
        .map { preferences ->
            preferences[key]?.let { raw ->
                runCatching { LocalDate.parse(raw) }
                    .getOrElse { error -> if (error is DateTimeParseException) null else throw error }
            }
        }

    private suspend fun editSafely(block: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (_: IOException) {
            // Losing sync bookkeeping is recoverable: the next sync simply re-fetches the window.
        }
    }

    private fun MutablePreferences.setOrRemove(key: Preferences.Key<Long>, value: Long?) {
        if (value == null) remove(key) else this[key] = value
    }

    /** Preference keys of one source, namespaced by its persisted [SyncSource.key]. */
    private class SourceKeys(source: SyncSource) {
        val lastSuccess = longPreferencesKey(
            // APOD predates per-source bookkeeping; its original key keeps the recorded history.
            if (source == SyncSource.APOD) LEGACY_APOD_LAST_SUCCESS else "sync.${source.key}.last_success_ms",
        )
        val lastFailure = longPreferencesKey("sync.${source.key}.last_failure_ms")
        val lastError = stringPreferencesKey("sync.${source.key}.last_error")
        val nextAllowed = longPreferencesKey("sync.${source.key}.next_allowed_ms")
    }

    private companion object {
        const val LEGACY_APOD_LAST_SUCCESS = "last_successful_sync_epoch_millis"
        val OLDEST_REQUESTED_DATE = stringPreferencesKey("oldest_requested_date")
        val LAST_ANNOUNCED_APOD_DATE = stringPreferencesKey("last_announced_apod_date")
    }
}
