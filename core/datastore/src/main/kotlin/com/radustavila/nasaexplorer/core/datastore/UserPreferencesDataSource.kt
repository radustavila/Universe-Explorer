package com.radustavila.nasaexplorer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.radustavila.nasaexplorer.core.datastore.di.UserPreferencesStore
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Preferences DataStore implementation of the user's choices. Kept in its own file, apart from the
 * sync bookkeeping, so that resetting one can never wipe the other.
 */
@Singleton
class UserPreferencesDataSource @Inject constructor(
    @UserPreferencesStore private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val newApodNotificationsEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        // Opt-in: nothing is posted until the user asks for it.
        .map { preferences -> preferences[NEW_APOD_NOTIFICATIONS_ENABLED] ?: false }

    override suspend fun setNewApodNotificationsEnabled(enabled: Boolean) {
        try {
            dataStore.edit { preferences -> preferences[NEW_APOD_NOTIFICATIONS_ENABLED] = enabled }
        } catch (_: IOException) {
            // The toggle simply keeps its previous value; the user can flip it again.
        }
    }

    private companion object {
        val NEW_APOD_NOTIFICATIONS_ENABLED = booleanPreferencesKey("new_apod_notifications_enabled")
    }
}
