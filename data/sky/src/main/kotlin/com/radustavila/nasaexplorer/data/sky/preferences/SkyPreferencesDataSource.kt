package com.radustavila.nasaexplorer.data.sky.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.radustavila.nasaexplorer.core.domain.sky.SkyPreferencesRepository
import com.radustavila.nasaexplorer.core.model.SatelliteSet
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

    override val enabledSatelliteSets: Flow<Set<SatelliteSet>> =
        preferences.map { it.satelliteSets(ENABLED_SATELLITE_SETS) }.distinctUntilChanged()

    override suspend fun setSatelliteSetEnabled(set: SatelliteSet, enabled: Boolean) {
        edit { it.toggle(ENABLED_SATELLITE_SETS, set, enabled) }
    }

    override val satellitePassReminders: Flow<Set<SatelliteSet>> =
        preferences.map { it.satelliteSets(SATELLITE_PASS_REMINDERS) }.distinctUntilChanged()

    override suspend fun setSatellitePassRemindersEnabled(set: SatelliteSet, enabled: Boolean) {
        edit { it.toggle(SATELLITE_PASS_REMINDERS, set, enabled) }
    }

    override val lastRemindedSatellitePassStart: Flow<Instant?> =
        preferences.map { prefs -> prefs[LAST_REMINDED_SATELLITE_PASS_START]?.let(Instant::ofEpochMilli) }
            .distinctUntilChanged()

    override suspend fun setLastRemindedSatellitePassStart(start: Instant) {
        edit { it[LAST_REMINDED_SATELLITE_PASS_START] = start.toEpochMilli() }
    }

    /** Names this version does not know (from a newer backup) are skipped. */
    private fun Preferences.satelliteSets(key: Preferences.Key<Set<String>>): Set<SatelliteSet> =
        this[key].orEmpty().mapNotNullTo(mutableSetOf()) { name ->
            SatelliteSet.entries.firstOrNull { it.name == name }
        }

    private fun androidx.datastore.preferences.core.MutablePreferences.toggle(
        key: Preferences.Key<Set<String>>,
        set: SatelliteSet,
        enabled: Boolean,
    ) {
        val current = this[key].orEmpty()
        this[key] = if (enabled) current + set.name else current - set.name
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
        val ENABLED_SATELLITE_SETS = stringSetPreferencesKey("enabled_satellite_sets")
        val SATELLITE_PASS_REMINDERS = stringSetPreferencesKey("satellite_pass_reminders")
        val LAST_REMINDED_SATELLITE_PASS_START = longPreferencesKey("last_reminded_satellite_pass_start")
    }
}
