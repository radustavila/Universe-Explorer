package com.radustavila.nasaexplorer.feature.mars.rail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveLatestMarsPhotosUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveMarsSyncStatusUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestMarsSyncUseCase
import com.radustavila.nasaexplorer.core.model.MarsPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The Universe tab's Mars rail. It only reads the cache; appearing asks the background job for a
 * refresh, which skips while the photos are less than a day old. Nothing here waits on the network.
 */
@HiltViewModel
class MarsRailViewModel @Inject constructor(
    observeLatest: ObserveLatestMarsPhotosUseCase,
    observeSyncStatus: ObserveMarsSyncStatusUseCase,
    requestSync: RequestMarsSyncUseCase,
) : ViewModel() {

    val uiState: StateFlow<MarsRailUiState> = combine(
        observeLatest(),
        observeSyncStatus(),
    ) { photos, status ->
        MarsRailUiState(
            photos = photos,
            isSyncing = status.isSyncing,
            lastAttemptFailed = status.lastAttemptFailed,
            isLoaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = MarsRailUiState(),
    )

    init {
        requestSync()
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}

data class MarsRailUiState(
    val photos: List<MarsPhoto> = emptyList(),
    val isSyncing: Boolean = false,
    val lastAttemptFailed: Boolean = false,
    /** False until the cache has been read once, so an empty rail does not flash a message. */
    val isLoaded: Boolean = false,
)
