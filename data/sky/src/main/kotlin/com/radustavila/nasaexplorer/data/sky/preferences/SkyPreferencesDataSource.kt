package com.radustavila.nasaexplorer.data.sky.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.radustavila.nasaexplorer.core.domain.sky.SkyPreferencesRepository
import com.radustavila.nasaexplorer.data.sky.di.SkyPreferencesStore
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class SkyPreferencesDataSource @Inject constructor(
    @SkyPreferencesStore private val dataStore: DataStore<Preferences>,
) : SkyPreferencesRepository {

    private val preferences: Flow<Preferences> = dataStore.data.catch { emit(emptyPreferences()) }

    override val issPassRemindersEnabled: Flow<Boolean> =
        preferences.map { it[ISS_PASS_REMINDERS_ENABLED] ?: false }.distinctUntilChanged()

    override suspend fun setIssPassRemindersEnabled(enabled: Boolean) {
        edit { it[ISS_PASS_REMINDERS_ENABLED] = enabled }
    }

    override val lastRemindedPassStart: Flow<Instant?> =
        preferences.map { prefs -> prefs[LAST_REMINDED_PASS_START]?.let(Instant::ofEpochMilli) }.distinctUntilChanged()

    override suspend fun setLastRemindedPassStart(start: Instant) {
        edit { it[LAST_REMINDED_PASS_START] = start.toEpochMilli() }
    }

    private suspend fun edit(transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            dataStore.edit(transform)
        } catch (_: IOException) {
            // Keeps the previous value; the toggle can be flipped again.
        }
    }

    private companion object {
        val ISS_PASS_REMINDERS_ENABLED = booleanPreferencesKey("iss_pass_reminders_enabled")
        val LAST_REMINDED_PASS_START = longPreferencesKey("last_reminded_pass_start")
    }
}
