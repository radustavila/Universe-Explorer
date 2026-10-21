package com.radustavila.nasaexplorer.feature.mars.photos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveMarsRoverPhotosUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveMarsSyncStatusUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestMarsSyncUseCase
import com.radustavila.nasaexplorer.core.model.MarsRover
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Every cached photo of the selected rover, grouped by sol and camera. Pull-to-refresh asks the
 * background job for a forced refresh; the screen keeps showing the cache meanwhile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MarsPhotosViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeRoverPhotos: ObserveMarsRoverPhotosUseCase,
    observeSyncStatus: ObserveMarsSyncStatusUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
    private val requestSync: RequestMarsSyncUseCase,
) : ViewModel() {

    private val rover = savedStateHandle.getStateFlow(KEY_ROVER, MarsRover.CURIOSITY.key)
        .map { key -> MarsRover.fromKey(key) ?: MarsRover.CURIOSITY }

    val uiState: StateFlow<MarsPhotosUiState> = combine(
        rover.flatMapLatest { selected -> observeRoverPhotos(selected).map { photos -> selected to photos } },
        observeSyncStatus(),
        observeConnectivity(),
    ) { (selected, photos), status, online ->
        MarsPhotosUiState(
            rover = selected,
            sols = photos.groupBySolAndCamera(),
            isSyncing = status.isSyncing,
            lastAttemptFailed = status.lastAttemptFailed,
            isOffline = !online,
            isLoaded = true,
            lastUpdatedAt = status.lastSuccessAt,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = MarsPhotosUiState(),
    )

    init {
        requestSync()
    }

    fun onRoverSelected(rover: MarsRover) {
        savedStateHandle[KEY_ROVER] = rover.key
    }

    fun onRefresh() {
        requestSync(force = true)
    }

    private companion object {
        const val KEY_ROVER = "rover"
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
