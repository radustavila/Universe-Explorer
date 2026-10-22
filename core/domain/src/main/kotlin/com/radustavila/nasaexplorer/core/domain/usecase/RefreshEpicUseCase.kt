package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.sync.EpicSyncScheduler
import javax.inject.Inject

/** Asks for fresh EPIC photos through the scheduler, never by calling the repository directly. */
class RefreshEpicUseCase @Inject constructor(
    private val epicSyncScheduler: EpicSyncScheduler,
) {
    /** @param force ignore the refresh interval, for a pull-to-refresh. */
    operator fun invoke(force: Boolean = false) = epicSyncScheduler.requestSync(force)
}
