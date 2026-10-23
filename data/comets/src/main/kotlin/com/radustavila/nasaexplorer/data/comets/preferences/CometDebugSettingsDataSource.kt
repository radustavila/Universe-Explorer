package com.radustavila.nasaexplorer.data.comets.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.radustavila.nasaexplorer.core.domain.repository.CometDebugSettingsRepository
import com.radustavila.nasaexplorer.data.comets.di.CometDebugStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The debug screen's comets switch, in a DataStore of its own. A testing aid rather than a user
 * choice, so it stays out of the backup rules.
 */
@Singleton
class CometDebugSettingsDataSource @Inject constructor(
    @CometDebugStore private val dataStore: DataStore<Preferences>,
) : CometDebugSettingsRepository {

    override fun observeShowFaintComets(): Flow<Boolean> =
        dataStore.data.catch { emit(emptyPreferences()) }.map { it[SHOW_FAINT_COMETS] ?: false }.distinctUntilChanged()

    override suspend fun setShowFaintComets(enabled: Boolean) {
        try {
            dataStore.edit { it[SHOW_FAINT_COMETS] = enabled }
        } catch (_: IOException) {
            // Keeps the previous value; the switch can be flipped again.
        }
    }

    private companion object {
        val SHOW_FAINT_COMETS = booleanPreferencesKey("show_faint_comets")
    }
}
