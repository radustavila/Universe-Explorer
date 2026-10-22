package com.radustavila.nasaexplorer.data.forecast.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingAlertRepository
import com.radustavila.nasaexplorer.core.model.StargazingAlertSettings
import com.radustavila.nasaexplorer.data.forecast.di.StargazingAlertStore
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Clear-night alert choices in their own DataStore file (backed up with the user's other choices),
 * apart from the forecast database, which may be rebuilt. The place is the shared observer location.
 */
@Singleton
internal class DataStoreStargazingAlertRepository @Inject constructor(
    @StargazingAlertStore private val dataStore: DataStore<Preferences>,
) : StargazingAlertRepository {

    private val preferences: Flow<Preferences> = dataStore.data.catch { emit(emptyPreferences()) }

    override val settings: Flow<StargazingAlertSettings> = preferences.map { preferences ->
        StargazingAlertSettings(
            // Opt-in: nothing is fetched or posted in the background until the user asks for it.
            enabled = preferences[ENABLED] ?: false,
            minimumScore = preferences[MINIMUM_SCORE] ?: StargazingAlertSettings.DEFAULT_MINIMUM_SCORE,
        )
    }

    override suspend fun setEnabled(enabled: Boolean) = edit { it[ENABLED] = enabled }

    override suspend fun setMinimumScore(score: Int) = edit { it[MINIMUM_SCORE] = score }

    override suspend fun alertedUntil(): Instant? = preferences.first()[ALERTED_UNTIL]?.let(Instant::ofEpochMilli)

    override suspend fun setAlertedUntil(until: Instant?) = edit {
        if (until == null) it.remove(ALERTED_UNTIL) else it[ALERTED_UNTIL] = until.toEpochMilli()
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (_: IOException) {
            // The choice keeps its previous value; the user can make it again.
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val MINIMUM_SCORE = intPreferencesKey("minimum_score")
        val ALERTED_UNTIL = longPreferencesKey("alerted_until")
    }
}
