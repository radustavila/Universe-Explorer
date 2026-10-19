package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserPreferencesRepository(
    initialNewApodNotificationsEnabled: Boolean = false,
    initialDailyWallpaperEnabled: Boolean = false,
) : UserPreferencesRepository {

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
