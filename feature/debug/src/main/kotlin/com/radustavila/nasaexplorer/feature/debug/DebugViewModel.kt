package com.radustavila.nasaexplorer.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDebugInfoUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveShowFaintCometsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetShowFaintCometsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Read-only window into the offline-first pipeline, plus testing switches. Exposes the scheduler
 * directly because this screen exists to drive and observe it.
 */
@HiltViewModel
class DebugViewModel @Inject constructor(
    observeDebugInfo: ObserveDebugInfoUseCase,
    observeShowFaintComets: ObserveShowFaintCometsUseCase,
    private val setShowFaintComets: SetShowFaintCometsUseCase,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    val uiState: StateFlow<DebugUiState> = combine(observeDebugInfo(), observeShowFaintComets()) { info, faint ->
        DebugUiState(info = info, showFaintComets = faint)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
            initialValue = DebugUiState(),
        )

    fun onSyncNow() = syncScheduler.requestSync()

    fun onEnsurePeriodicSync() = syncScheduler.ensurePeriodicSync()

    fun onShowFaintCometsChange(enabled: Boolean) {
        viewModelScope.launch { setShowFaintComets(enabled) }
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
