package com.radustavila.nasaexplorer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.radustavila.nasaexplorer.core.datastore.di.UserPreferencesStore
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.TimeFormat
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
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

    override val dailyWallpaperEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        // Opt-in: the wallpaper is the user's, and nothing replaces it until they ask.
        .map { preferences -> preferences[DAILY_WALLPAPER_ENABLED] ?: false }

    override suspend fun setDailyWallpaperEnabled(enabled: Boolean) {
        try {
            dataStore.edit { preferences -> preferences[DAILY_WALLPAPER_ENABLED] = enabled }
        } catch (_: IOException) {
            // As above: the toggle keeps its previous value.
        }
    }

    override val unitPreferences: Flow<UnitPreferences> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            UnitPreferences(
                // Stored by name; an unknown name (a value from a newer version) falls back to the default.
                measurementSystem = preferences[MEASUREMENT_SYSTEM].toEnumOr(MeasurementSystem.AUTOMATIC),
                timeFormat = preferences[TIME_FORMAT].toEnumOr(TimeFormat.SYSTEM),
            )
        }
        .distinctUntilChanged()

    override suspend fun setMeasurementSystem(system: MeasurementSystem) {
        try {
            dataStore.edit { preferences -> preferences[MEASUREMENT_SYSTEM] = system.name }
        } catch (_: IOException) {
            // As above: the choice keeps its previous value.
        }
    }

    override suspend fun setTimeFormat(format: TimeFormat) {
        try {
            dataStore.edit { preferences -> preferences[TIME_FORMAT] = format.name }
        } catch (_: IOException) {
            // As above: the choice keeps its previous value.
        }
    }

    private companion object {
        val NEW_APOD_NOTIFICATIONS_ENABLED = booleanPreferencesKey("new_apod_notifications_enabled")
        val DAILY_WALLPAPER_ENABLED = booleanPreferencesKey("daily_wallpaper_enabled")
        val MEASUREMENT_SYSTEM = stringPreferencesKey("measurement_system")
        val TIME_FORMAT = stringPreferencesKey("time_format")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default
