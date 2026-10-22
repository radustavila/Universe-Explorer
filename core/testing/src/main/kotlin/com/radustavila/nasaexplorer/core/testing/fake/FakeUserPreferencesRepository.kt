package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.TimeFormat
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserPreferencesRepository(
    initialNewApodNotificationsEnabled: Boolean = false,
    initialDailyWallpaperEnabled: Boolean = false,
    initialUnitPreferences: UnitPreferences = UnitPreferences(),
) : UserPreferencesRepository {

    private val units = MutableStateFlow(initialUnitPreferences)
    override val unitPreferences: Flow<UnitPreferences> = units
    val currentUnitPreferences: UnitPreferences get() = units.value

    override suspend fun setMeasurementSystem(system: MeasurementSystem) {
        units.value = units.value.copy(measurementSystem = system)
    }

    override suspend fun setTimeFormat(format: TimeFormat) {
        units.value = units.value.copy(timeFormat = format)
    }

    private val notificationsEnabled = MutableStateFlow(initialNewApodNotificationsEnabled)
    private val wallpaperEnabled = MutableStateFlow(initialDailyWallpaperEnabled)

    override val newApodNotificationsEnabled: Flow<Boolean> = notificationsEnabled
    override val dailyWallpaperEnabled: Flow<Boolean> = wallpaperEnabled

    val currentNewApodNotificationsEnabled: Boolean get() = notificationsEnabled.value
    val currentDailyWallpaperEnabled: Boolean get() = wallpaperEnabled.value

    override suspend fun setNewApodNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled.value = enabled
    }

    override suspend fun setDailyWallpaperEnabled(enabled: Boolean) {
        wallpaperEnabled.value = enabled
    }
}
