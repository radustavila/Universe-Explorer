package com.radustavila.nasaexplorer.core.location

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.radustavila.nasaexplorer.core.domain.location.ObserverLocationRepository
import com.radustavila.nasaexplorer.core.location.di.ObserverLocationStore
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.LocationSource
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The observer's location in its own small DataStore file, apart from preferences and sync state. */
@Singleton
class DataStoreObserverLocationRepository @Inject constructor(
    @ObserverLocationStore private val dataStore: DataStore<Preferences>,
) : ObserverLocationRepository {

    override val location: Flow<ObserverLocation?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> preferences.toLocation() }
        .distinctUntilChanged()

    override suspend fun setLocation(location: ObserverLocation) {
        edit { preferences ->
            preferences[LATITUDE] = location.coordinate.latitude
            preferences[LONGITUDE] = location.coordinate.longitude
            preferences[SOURCE] = location.source.name
            val name = location.name
            if (name == null) preferences.remove(NAME) else preferences[NAME] = name
        }
    }

    override suspend fun clearLocation() {
        edit { preferences -> preferences.clear() }
    }

    private suspend fun edit(transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            dataStore.edit(transform)
        } catch (_: IOException) {
            // The previous location stays; the user can pick it again.
        }
    }

    private fun Preferences.toLocation(): ObserverLocation? {
        val latitude = this[LATITUDE] ?: return null
        val longitude = this[LONGITUDE] ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return ObserverLocation(
            coordinate = GeoCoordinate(latitude, longitude),
            name = this[NAME],
            source = this[SOURCE]?.let { stored -> LocationSource.entries.firstOrNull { it.name == stored } }
                ?: LocationSource.MANUAL,
        )
    }

    private companion object {
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val NAME = stringPreferencesKey("name")
        val SOURCE = stringPreferencesKey("source")
    }
}
