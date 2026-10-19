package com.radustavila.nasaexplorer.feature.spaceweather.neighbourhood

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCloseApproachesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveRecentFireballsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSmallBodiesSyncStatusUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestSmallBodiesSyncUseCase
import com.radustavila.nasaexplorer.core.model.CloseApproach
import com.radustavila.nasaexplorer.core.model.Fireball
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class NeighbourhoodUiState(
    val approaches: List<CloseApproach> = emptyList(),
    val fireballs: List<Fireball> = emptyList(),
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val lastUpdatedAt: Instant? = null,
    val lastRefreshFailed: Boolean = false,
    /** Whether anything is cached, so an empty list means "none", not "not loaded yet". */
    val hasSynced: Boolean = false,
) {
    val hasData: Boolean get() = approaches.isNotEmpty() || fireballs.isNotEmpty()
}

/**
 * Close approaches still ahead (or just passed) and recent fireballs, from the local cache. Opening
 * the screen asks the daily job for a refresh, which it skips while the lists are fresh.
 */
@HiltViewModel
class NeighbourhoodViewModel @Inject constructor(
    observeCloseApproaches: ObserveCloseApproachesUseCase,
    observeFireballs: ObserveRecentFireballsUseCase,
    observeSyncStatus: ObserveSmallBodiesSyncStatusUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
    private val requestSync: RequestSmallBodiesSyncUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val isOnline = observeConnectivity()

    val uiState: StateFlow<NeighbourhoodUiState> = combine(
        observeCloseApproaches(),
        observeFireballs(),
        observeSyncStatus(),
        isOnline,
    ) { approaches, fireballs, sync, online ->
        val cutoff = timeProvider.now().minus(PASSED_SHOWN_FOR)
        NeighbourhoodUiState(
            approaches = approaches.filter { it.time.isAfter(cutoff) },
            fireballs = fireballs,
            isRefreshing = sync.isSyncing,
            isOffline = !online,
            lastUpdatedAt = sync.lastSuccessAt,
            lastRefreshFailed = sync.lastAttemptFailed,
            hasSynced = sync.lastSuccessAt != null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = NeighbourhoodUiState(),
    )

    init {
        requestSync()
        viewModelScope.launch {
            isOnline.distinctUntilChanged().drop(1).filter { it }.collect { requestSync() }
        }
    }

    fun onRefresh() = requestSync(force = true)

    private companion object {
        /** An approach that happened this morning is still news; one from last week is not. */
        val PASSED_SHOWN_FOR: Duration = Duration.ofDays(1)
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
