package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import javax.inject.Inject

/**
 * Manual refresh. Deliberately routed through the [SyncScheduler] rather than calling the repository
 * directly: pull-to-refresh and background sync then share one execution path, one set of
 * constraints and one observable state.
 */
class RefreshFeedUseCase @Inject constructor(
    private val syncScheduler: SyncScheduler,
) {
    operator fun invoke() = syncScheduler.requestSync()
}
