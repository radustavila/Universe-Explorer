package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import javax.inject.Inject

/**
 * Turns the daily wallpaper on or off.
 *
 * The wallpaper is set by the sync worker, like everything else that leaves the app, so switching
 * it on requests a sync rather than setting it here. Switching it off forgets which entry was last
 * applied: switching back on later must apply the current picture again, even if it is the same one.
 */
class SetDailyWallpaperEnabledUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val syncScheduler: SyncScheduler,
) {
    suspend operator fun invoke(enabled: Boolean) {
        userPreferencesRepository.setDailyWallpaperEnabled(enabled)
        if (enabled) {
            syncScheduler.requestSync()
        } else {
            syncMetadataRepository.setLastWallpaperApodDate(null)
        }
    }
}
