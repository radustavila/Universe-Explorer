package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidget
import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidgetUpdater
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.sync.succeeded
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Refreshes the upcoming-launches cache as part of the regular sync.
 *
 * Runs from the sync worker alongside [SynchronizeApodsUseCase], never from the UI. Launch Library 2
 * allows only 15 anonymous calls an hour, so the source is refreshed at most once per
 * [MIN_REFRESH_INTERVAL] (and not before a 429's `Retry-After`), whatever triggered the sync. It is
 * best-effort: its failures never decide whether the APOD sync is retried, since a retry would only
 * spend more of that budget. A successful refresh re-renders the next-launch widget and re-arms
 * the launch reminders, whose NETs may have moved.
 *
 * The Launches screen calls it too when it opens: the same hourly limit applies, so opening the
 * screen never adds a call the sync would not have made.
 */
class SynchronizeUpcomingLaunchesUseCase @Inject constructor(
    private val launchRepository: LaunchRepository,
    private val networkMonitor: NetworkMonitor,
    private val refreshSource: RefreshSourceUseCase,
    private val widgetUpdater: HomeWidgetUpdater,
    private val rescheduleLaunchReminders: RescheduleLaunchRemindersUseCase,
) {
    suspend operator fun invoke(): SourceRefreshResult<Int> {
        if (!networkMonitor.isOnline.first()) {
            return SourceRefreshResult.Completed(DataResult.Failure(AppError.NoConnection))
        }
        return refreshSource(SyncSource.LAUNCHES, minInterval = MIN_REFRESH_INTERVAL) {
            launchRepository.refreshUpcomingLaunches()
        }.also { outcome ->
            if (outcome.succeeded()) {
                widgetUpdater.update(HomeWidget.NEXT_LAUNCH)
                rescheduleLaunchReminders()
            }
        }
    }

    companion object {
        val MIN_REFRESH_INTERVAL: Duration = Duration.ofMinutes(60)
    }
}
