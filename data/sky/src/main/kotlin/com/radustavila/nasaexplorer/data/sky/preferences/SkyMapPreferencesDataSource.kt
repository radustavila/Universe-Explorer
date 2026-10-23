package com.radustavila.nasaexplorer.data.sky.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.radustavila.nasaexplorer.core.domain.sky.SkyMapPreferencesRepository
import com.radustavila.nasaexplorer.data.sky.di.SkyPreferencesStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The sky map's choices, kept in Sky tonight's DataStore (already in the backup rules). */
@Singleton
class SkyMapPreferencesDataSource @Inject constructor(
    @SkyPreferencesStore private val dataStore: DataStore<Preferences>,
) : SkyMapPreferencesRepository {

    override fun observeNightMode(): Flow<Boolean> =
        dataStore.data.catch { emit(emptyPreferences()) }.map { it[NIGHT_MODE] ?: false }.distinctUntilChanged()

    override suspend fun setNightMode(enabled: Boolean) {
        try {
            dataStore.edit { it[NIGHT_MODE] = enabled }
        } catch (_: IOException) {
            // Keeps the previous value; the toggle can be flipped again.
        }
    }

    override fun observeDeepSkyLayer(): Flow<Boolean> =
        dataStore.data.catch { emit(emptyPreferences()) }.map { it[DEEP_SKY_LAYER] ?: true }.distinctUntilChanged()

    override suspend fun setDeepSkyLayer(enabled: Boolean) {
        try {
            dataStore.edit { it[DEEP_SKY_LAYER] = enabled }
        } catch (_: IOException) {
            // Keeps the previous value; the switch can be flipped again.
        }
    }

    private companion object {
        val NIGHT_MODE = booleanPreferencesKey("sky_map_night_mode")
        val DEEP_SKY_LAYER = booleanPreferencesKey("sky_map_deep_sky_layer")
    }
}
