package com.radustavila.nasaexplorer.feature.exoplanets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveExoplanetCatalogStatsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveExoplanetSyncStatusUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveExoplanetsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestExoplanetSyncUseCase
import com.radustavila.nasaexplorer.core.model.Exoplanet
import com.radustavila.nasaexplorer.core.model.ExoplanetList
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ExoplanetsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeExoplanets: ObserveExoplanetsUseCase,
    observeCatalogStats: ObserveExoplanetCatalogStatsUseCase,
    observeSyncStatus: ObserveExoplanetSyncStatusUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
    private val requestSync: RequestExoplanetSyncUseCase,
    timeProvider: TimeProvider,
) : ViewModel() {

    /** Survives rotation and process death with the rest of the back stack entry. */
    private val selectedList = savedStateHandle.getStateFlow(KEY_SELECTED_LIST, ExoplanetList.NEWEST)

    /** Tagged with its list, so a tab switch reads as loading until the new list arrives. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val loadedList = selectedList.flatMapLatest { list ->
        observeExoplanets(list).map { planets -> LoadedList(list, planets) }
    }

    private val newestSinceYear = timeProvider.today().year - 1

    val uiState: StateFlow<ExoplanetsUiState> = combine(
        selectedList,
        loadedList,
        observeCatalogStats(),
        observeSyncStatus(),
        observeConnectivity(),
    ) { selected, loaded, stats, sync, isOnline ->
        val isCurrent = loaded.list == selected
        ExoplanetsUiState(
            selectedList = selected,
            planets = if (isCurrent) loaded.planets else emptyList(),
            isLoading = !isCurrent,
            isRefreshing = sync.isSyncing,
            isOffline = !isOnline,
            neverSynced = sync.lastSuccessAt == null,
            lastRefreshFailed = sync.lastAttemptFailed,
            confirmedPlanets = stats?.confirmedPlanets,
            newestSinceYear = newestSinceYear,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = ExoplanetsUiState(newestSinceYear = newestSinceYear),
    )

    init {
        // Downloads the catalogue on first open; afterwards the job skips until it is a week old.
        requestSync(force = false)
    }

    fun onListSelected(list: ExoplanetList) {
        savedStateHandle[KEY_SELECTED_LIST] = list
    }

    fun onRefresh() {
        requestSync(force = true)
    }

    private data class LoadedList(val list: ExoplanetList, val planets: List<Exoplanet>)

    private companion object {
        const val KEY_SELECTED_LIST = "selected_list"
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
