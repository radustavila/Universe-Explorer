package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.announcement.LatestApodWidgetUpdater
import com.radustavila.nasaexplorer.core.domain.announcement.NewApodNotifier
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Runs after every successful sync and pushes the newest cached entry to the surfaces that live
 * outside the app: the home screen widget is always refreshed, and a notification is posted when the
 * entry is newer than the last one announced and the user opted in.
 *
 * The very first run only records a baseline. Announcing whatever happens to be newest on a fresh
 * install - or right after the cache was cleared - would notify about a picture that is not new.
 */
class AnnounceLatestApodUseCase @Inject constructor(
    private val apodRepository: ApodRepository,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val widgetUpdater: LatestApodWidgetUpdater,
    private val notifier: NewApodNotifier,
) {
    suspend operator fun invoke() {
        widgetUpdater.updateAll()

        val latest = apodRepository.observeLatestApod().first() ?: return
        val lastAnnounced = syncMetadataRepository.lastAnnouncedApodDate.first()
        if (lastAnnounced != null && !latest.date.isAfter(lastAnnounced)) return

        // Recorded before notifying, and whether or not the user opted in: switching notifications
        // on later must not replay an entry that was published while they were off.
        syncMetadataRepository.setLastAnnouncedApodDate(latest.date)

        if (lastAnnounced != null && userPreferencesRepository.newApodNotificationsEnabled.first()) {
            notifier.notifyNewApod(latest)
        }
    }
}
