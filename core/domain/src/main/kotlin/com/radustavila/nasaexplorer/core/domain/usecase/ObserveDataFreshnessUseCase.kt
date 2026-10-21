package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.SyncSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The [DataFreshness] of a screen fed by [sources]: their last successful sync as recorded by
 * [RefreshSourceUseCase] (or the APOD worker), and whether the device is online.
 *
 * A screen fed by several sources is as fresh as the stalest of those that synced at least once,
 * and its last refresh failed when any of theirs did.
 */
class ObserveDataFreshnessUseCase @Inject constructor(
    private val syncMetadataRepository: SyncMetadataRepository,
    private val networkMonitor: NetworkMonitor,
) {
    operator fun invoke(vararg sources: SyncSource): Flow<DataFreshness> {
        require(sources.isNotEmpty()) { "A screen's freshness needs at least one source" }
        val statuses = combine(sources.map(syncMetadataRepository::observeSourceStatus)) { it.toList() }
        return combine(statuses, networkMonitor.isOnline) { list, isOnline ->
            DataFreshness(
                lastUpdatedAt = list.mapNotNull { it.lastSuccessAt }.minOrNull(),
                isOffline = !isOnline,
                lastRefreshFailed = list.any { it.isFailing },
            )
        }.distinctUntilChanged()
    }
}
