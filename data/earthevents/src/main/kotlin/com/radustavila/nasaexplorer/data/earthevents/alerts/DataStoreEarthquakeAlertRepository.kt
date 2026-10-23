package com.radustavila.nasaexplorer.data.earthevents.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeAlertRepository
import com.radustavila.nasaexplorer.data.earthevents.di.EarthquakeAlertStore
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The strong-quake alert choice in its own DataStore file (backed up with the user's other
 * choices), apart from the events database, which may be rebuilt. The place is the shared observer
 * location.
 */
@Singleton
class DataStoreEarthquakeAlertRepository @Inject constructor(
    @EarthquakeAlertStore private val dataStore: DataStore<Preferences>,
) : EarthquakeAlertRepository {

    private val preferences: Flow<Preferences> = dataStore.data.catch { emit(emptyPreferences()) }

    // Opt-in: nothing is posted until the user asks for it.
    override val enabled: Flow<Boolean> = preferences.map { it[ENABLED] ?: false }

    override suspend fun setEnabled(enabled: Boolean) = edit { it[ENABLED] = enabled }

    override suspend fun alertedUntil(): Instant? = preferences.first()[ALERTED_UNTIL]?.let(Instant::ofEpochMilli)

    override suspend fun setAlertedUntil(until: Instant) = edit { it[ALERTED_UNTIL] = until.toEpochMilli() }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (_: IOException) {
            // The choice keeps its previous value; the user can make it again.
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val ALERTED_UNTIL = longPreferencesKey("alerted_until")
    }
}
