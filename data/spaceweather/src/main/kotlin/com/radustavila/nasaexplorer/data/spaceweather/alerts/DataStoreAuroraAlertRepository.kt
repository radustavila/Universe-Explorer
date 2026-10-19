package com.radustavila.nasaexplorer.data.spaceweather.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.radustavila.nasaexplorer.core.domain.repository.AuroraAlertRepository
import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import com.radustavila.nasaexplorer.data.spaceweather.di.AuroraAlertStore
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Aurora alert choices in their own DataStore file, not in the space weather database: they are the
 * user's, and the database may be rebuilt on a schema change.
 *
 * The place is stored here until the app has a shared observer location (Sky tonight, F5); then
 * alerts should read that one instead.
 */
@Singleton
class DataStoreAuroraAlertRepository @Inject constructor(
    @AuroraAlertStore private val dataStore: DataStore<Preferences>,
) : AuroraAlertRepository {

    private val preferences: Flow<Preferences> = dataStore.data.catch { emit(emptyPreferences()) }

    override val settings: Flow<AuroraAlertSettings> = preferences.map { preferences ->
        AuroraAlertSettings(
            // Opt-in: nothing is posted until the user asks for it.
            enabled = preferences[ENABLED] ?: false,
            latitude = preferences[LATITUDE],
            longitude = preferences[LONGITUDE],
        )
    }

    override suspend fun setEnabled(enabled: Boolean) = edit { it[ENABLED] = enabled }

    override suspend fun setLocation(latitude: Double, longitude: Double) = edit {
        it[LATITUDE] = latitude
        it[LONGITUDE] = longitude
    }

    override suspend fun alertedUntil(): Instant? = preferences.first()[ALERTED_UNTIL]?.let(Instant::ofEpochMilli)

    override suspend fun setAlertedUntil(until: Instant?) = edit {
        if (until == null) it.remove(ALERTED_UNTIL) else it[ALERTED_UNTIL] = until.toEpochMilli()
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (_: IOException) {
            // The choice keeps its previous value; the user can make it again.
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val ALERTED_UNTIL = longPreferencesKey("alerted_until")
    }
}
