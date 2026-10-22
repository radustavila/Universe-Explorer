package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.DeepSpaceNetworkRepository
import com.radustavila.nasaexplorer.core.model.DeepSpaceProbe
import com.radustavila.nasaexplorer.core.model.DsnRightNow
import com.radustavila.nasaexplorer.core.model.DsnSnapshot
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ObserveDsnSnapshotUseCase @Inject constructor(
    private val repository: DeepSpaceNetworkRepository,
) {
    operator fun invoke(): Flow<DsnSnapshot?> = repository.observeSnapshot()
}

class ObserveFarthestProbesUseCase @Inject constructor(
    private val repository: DeepSpaceNetworkRepository,
) {
    operator fun invoke(): Flow<List<DeepSpaceProbe>> = repository.observeFarthestProbes()
}

/**
 * "Right now: Voyager 2 is talking to Canberra": of the spacecraft with a signal on, the farthest
 * one (those without a known distance last). `null` before the first snapshot or while every dish
 * is quiet.
 */
class ObserveDsnRightNowUseCase @Inject constructor(
    private val repository: DeepSpaceNetworkRepository,
) {
    operator fun invoke(): Flow<DsnRightNow?> = repository.observeSnapshot().map { snapshot ->
        snapshot?.activeLinks
            ?.maxWithOrNull(compareBy(nullsFirst()) { it.link.distanceKm })
            ?.let { (site, _, link) ->
                DsnRightNow(
                    spacecraft = link.spacecraft,
                    siteName = site.name,
                    distanceKm = link.distanceKm,
                    observedAt = snapshot.observedAt,
                )
            }
    }.distinctUntilChanged()
}

/**
 * Keeps the DSN cache current for as long as the caller is collecting it, i.e. while a screen
 * showing it is visible; it never runs in the background. Every [interval] it refreshes the live
 * feed, and the site names and Horizons distances when they are due. Offline it only waits.
 *
 * Suspends until cancelled. Each source goes through [RefreshSourceUseCase], so two visible
 * callers (the screen and its Today card) do not fetch the feed twice within [LIVE_MIN_INTERVAL].
 */
class PollDeepSpaceNetworkUseCase @Inject constructor(
    private val repository: DeepSpaceNetworkRepository,
    private val refreshSource: RefreshSourceUseCase,
    private val networkMonitor: NetworkMonitor,
) {
    suspend operator fun invoke(interval: Duration = POLL_INTERVAL): Nothing {
        while (true) {
            if (networkMonitor.isOnline.first()) {
                refreshSource(SyncSource.DSN_NOW, minInterval = LIVE_MIN_INTERVAL) { repository.refreshLive() }
                refreshSource(SyncSource.DSN_CONFIG, minInterval = CONFIG_INTERVAL) { repository.refreshConfig() }
                refreshSource(SyncSource.HORIZONS, minInterval = DISTANCES_INTERVAL) { repository.refreshDistances() }
            }
            delay(interval.toMillis())
        }
    }

    companion object {
        /** NASA's own page refreshes every 5 s; the brief allows 10 to 30. */
        val POLL_INTERVAL: Duration = Duration.ofSeconds(15)

        /** A little under [POLL_INTERVAL], so a poll that wakes early is not skipped. */
        val LIVE_MIN_INTERVAL: Duration = Duration.ofSeconds(10)

        /** The site and spacecraft list changes a few times a year. */
        val CONFIG_INTERVAL: Duration = Duration.ofDays(14)

        /** Horizons is asked for one distance per spacecraft per day. */
        val DISTANCES_INTERVAL: Duration = Duration.ofHours(20)
    }
}
