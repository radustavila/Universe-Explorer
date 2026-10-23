package com.radustavila.nasaexplorer.feature.comets

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCometsTonightUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshCometsUseCase
import com.radustavila.nasaexplorer.core.model.CometsTonight
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.SyncSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class CometsUiState(
    /** Null until the cache has been read and tonight computed once. */
    val comets: CometsTonight? = null,
    val freshness: DataFreshness = DataFreshness(),
    val isRefreshing: Boolean = false,
    /** A refresh the user asked for failed; shown once. */
    val refreshFailed: Boolean = false,
) {
    val isLoaded: Boolean get() = comets != null
    val hasPlace: Boolean get() = comets?.observer != null
    val isEmpty: Boolean get() = comets?.let { it.visible.isEmpty() && it.faint.isEmpty() } ?: false
}

/**
 * The comets screen and Sky tonight's comets card. Comets come from the cache only; opening either
 * asks for a refresh, which [RefreshCometsUseCase] skips while the daily download is fresh, and
 * pull-to-refresh forces one.
 */
@HiltViewModel
class CometsViewModel @Inject constructor(
    observeCometsTonight: ObserveCometsTonightUseCase,
    observeFreshness: ObserveDataFreshnessUseCase,
    private val refreshComets: RefreshCometsUseCase,
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<CometsUiState> = combine(
        observeCometsTonight(),
        observeFreshness(SyncSource.COMETS),
        transient,
    ) { comets, freshness, transient ->
        CometsUiState(
            comets = comets,
            freshness = freshness,
            isRefreshing = transient.isRefreshing,
            refreshFailed = transient.refreshFailed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = CometsUiState(),
    )

    init {
        refresh(force = false)
    }

    fun onRefresh() = refresh(force = true)

    fun onRefreshFailedShown() {
        transient.update { it.copy(refreshFailed = false) }
    }

    private fun refresh(force: Boolean) {
        if (transient.value.isRefreshing) return
        transient.update { it.copy(isRefreshing = force) }
        viewModelScope.launch {
            val outcome = refreshComets(force = force)
            val failed = outcome is SourceRefreshResult.Completed && outcome.result is DataResult.Failure
            // Opening the screen offline is normal and the freshness line says so; only a refresh
            // the user asked for deserves a notice.
            transient.update { Transient(isRefreshing = false, refreshFailed = failed && force) }
        }
    }

    private data class Transient(val isRefreshing: Boolean = false, val refreshFailed: Boolean = false)

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
