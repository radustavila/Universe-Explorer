package com.radustavila.nasaexplorer.feature.webb

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveWebbNowUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveWebbScheduleUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshWebbScheduleUseCase
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.model.WebbNow
import com.radustavila.nasaexplorer.core.model.WebbVisit
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
data class WebbUiState(
    val isLoaded: Boolean = false,
    /** What is scheduled now and next; null before the first download. */
    val now: WebbNow? = null,
    /** Every stored visit, in start order, for the week's timeline. */
    val visits: List<WebbVisit> = emptyList(),
    val freshness: DataFreshness = DataFreshness(),
    val isRefreshing: Boolean = false,
    /** A refresh the user asked for failed; shown once. */
    val refreshFailed: Boolean = false,
) {
    val hasSchedule: Boolean get() = now != null
}

/**
 * The "Webb right now" card and the week's schedule screen. Both read the stored schedule only;
 * opening either asks for a refresh, which [RefreshWebbScheduleUseCase] skips while the daily
 * download is fresh, and pull-to-refresh forces one.
 */
@HiltViewModel
class WebbViewModel @Inject constructor(
    observeNow: ObserveWebbNowUseCase,
    observeSchedule: ObserveWebbScheduleUseCase,
    observeFreshness: ObserveDataFreshnessUseCase,
    private val refreshSchedule: RefreshWebbScheduleUseCase,
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<WebbUiState> = combine(
        observeNow(),
        observeSchedule(),
        observeFreshness(SyncSource.JWST_SCHEDULE),
        transient,
    ) { now, schedule, freshness, transient ->
        WebbUiState(
            isLoaded = true,
            now = now,
            visits = schedule?.visits.orEmpty(),
            freshness = freshness,
            isRefreshing = transient.isRefreshing,
            refreshFailed = transient.refreshFailed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = WebbUiState(),
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
            val outcome = refreshSchedule(force = force)
            val failed = outcome is SourceRefreshResult.Completed && outcome.result is DataResult.Failure
            // Opening offline is normal and the freshness line says so; only a refresh the user
            // asked for deserves a notice.
            transient.update { Transient(isRefreshing = false, refreshFailed = failed && force) }
        }
    }

    private data class Transient(val isRefreshing: Boolean = false, val refreshFailed: Boolean = false)

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
