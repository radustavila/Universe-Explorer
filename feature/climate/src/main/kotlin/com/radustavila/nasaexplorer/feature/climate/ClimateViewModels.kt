package com.radustavila.nasaexplorer.feature.climate

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveClimateUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshClimateUseCase
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.ClimateSeries
import com.radustavila.nasaexplorer.core.model.DataFreshness
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STATE_TIMEOUT_MILLIS = 5_000L

@Immutable
data class VitalSignsUiState(
    val isLoaded: Boolean = false,
    /** The stored indicators, in [ClimateIndicator] order; empty before the first download. */
    val series: List<ClimateSeries> = emptyList(),
    val freshness: DataFreshness = DataFreshness(),
)

/**
 * The Earth tab's "Vital signs" section. Reads the stored records only; opening it asks for a
 * refresh, which [RefreshClimateUseCase] skips while the weekly download is fresh.
 */
@HiltViewModel
class VitalSignsViewModel @Inject constructor(
    observeClimate: ObserveClimateUseCase,
    observeFreshness: ObserveDataFreshnessUseCase,
    private val refreshClimate: RefreshClimateUseCase,
) : ViewModel() {

    val uiState: StateFlow<VitalSignsUiState> = combine(
        observeClimate(),
        observeFreshness(*ClimateIndicator.entries.map { it.syncSource }.toTypedArray()),
    ) { series, freshness ->
        VitalSignsUiState(isLoaded = true, series = series, freshness = freshness)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = VitalSignsUiState(),
    )

    init {
        viewModelScope.launch { refreshClimate() }
    }
}

@Immutable
data class ClimateUiState(
    val indicator: ClimateIndicator,
    val isLoaded: Boolean = false,
    /** Null before the indicator's first download. */
    val series: ClimateSeries? = null,
    val freshness: DataFreshness = DataFreshness(),
    val isRefreshing: Boolean = false,
    /** A refresh the user asked for failed; shown once. */
    val refreshFailed: Boolean = false,
)

/** One indicator's full chart; pull-to-refresh downloads its file again. */
@HiltViewModel
class ClimateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeClimate: ObserveClimateUseCase,
    observeFreshness: ObserveDataFreshnessUseCase,
    private val refreshClimate: RefreshClimateUseCase,
) : ViewModel() {

    private val indicator =
        ClimateIndicator.fromKey(savedStateHandle.toRoute<ClimateRoute>().indicator) ?: ClimateIndicator.CO2

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<ClimateUiState> = combine(
        observeClimate(indicator),
        observeFreshness(indicator.syncSource),
        transient,
    ) { series, freshness, transient ->
        ClimateUiState(
            indicator = indicator,
            isLoaded = true,
            series = series,
            freshness = freshness,
            isRefreshing = transient.isRefreshing,
            refreshFailed = transient.refreshFailed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = ClimateUiState(indicator),
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
            val outcome = refreshClimate(force = force, indicators = listOf(indicator))[indicator]
            val failed = outcome is SourceRefreshResult.Completed && outcome.result is DataResult.Failure
            // Opening offline is normal and the freshness line says so; only a refresh the user
            // asked for deserves a notice.
            transient.update { Transient(isRefreshing = false, refreshFailed = failed && force) }
        }
    }

    private data class Transient(val isRefreshing: Boolean = false, val refreshFailed: Boolean = false)
}
