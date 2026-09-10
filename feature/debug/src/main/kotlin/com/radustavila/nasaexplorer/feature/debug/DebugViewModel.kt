package com.radustavila.nasaexplorer.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDebugInfoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Read-only window into the offline-first pipeline. Exposes the scheduler directly because this
 * screen exists to drive and observe it.
 */
@HiltViewModel
class DebugViewModel @Inject constructor(
    observeDebugInfo: ObserveDebugInfoUseCase,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    val uiState: StateFlow<DebugUiState> = observeDebugInfo()
        .map { info -> DebugUiState(info = info) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
            initialValue = DebugUiState(),
        )

    fun onSyncNow() = syncScheduler.requestSync()

    fun onEnsurePeriodicSync() = syncScheduler.ensurePeriodicSync()

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
