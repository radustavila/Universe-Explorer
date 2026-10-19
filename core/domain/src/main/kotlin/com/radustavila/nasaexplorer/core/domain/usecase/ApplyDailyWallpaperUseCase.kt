package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import com.radustavila.nasaexplorer.core.domain.wallpaper.WallpaperSetter
import com.radustavila.nasaexplorer.core.model.MediaType
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Daily wallpaper: after a successful sync, the newest picture becomes the home screen wallpaper -
 * when the user opted in, when it is a still image (a video has nothing to set), and only once per
 * entry, so the twice-daily sync does not redraw the same wallpaper or undo a change the user made
 * by hand in between.
 */
class ApplyDailyWallpaperUseCase @Inject constructor(
    private val apodRepository: ApodRepository,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val wallpaperSetter: WallpaperSetter,
) {
    suspend operator fun invoke() {
        if (!userPreferencesRepository.dailyWallpaperEnabled.first()) return

        val latest = apodRepository.observeLatestApod().first() ?: return
        if (latest.mediaType != MediaType.IMAGE) return
        if (syncMetadataRepository.lastWallpaperApodDate.first() == latest.date) return

        if (wallpaperSetter.setWallpaper(latest)) {
            syncMetadataRepository.setLastWallpaperApodDate(latest.date)
        }
    }
}
