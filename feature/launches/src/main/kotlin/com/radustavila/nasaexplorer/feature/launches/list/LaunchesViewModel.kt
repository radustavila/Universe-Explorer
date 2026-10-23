package com.radustavila.nasaexplorer.feature.launches.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveLaunchRemindersUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObservePastLaunchesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveUpcomingLaunchesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshPastLaunchesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetLaunchReminderUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeUpcomingLaunchesUseCase
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.Launch
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

enum class LaunchesTab { UPCOMING, PAST }

/** What the user picked on the screen: survives recomposition, not process death. */
@Immutable
data class LaunchFilters(
    val tab: LaunchesTab = LaunchesTab.UPCOMING,
    /** Only this provider's launches; null for all. */
    val provider: String? = null,
    val crewedOnly: Boolean = false,
)

@Immutable
data class LaunchesUiState(
    /** Null until the cache has been read. */
    val upcoming: List<Launch>? = null,
    val past: List<Launch>? = null,
    val filters: LaunchFilters = LaunchFilters(),
    /** Ids of the launches with a reminder on. */
    val reminders: Set<String> = emptySet(),
    val upcomingFreshness: DataFreshness = DataFreshness(),
    val pastFreshness: DataFreshness = DataFreshness(),
) {
    /** The selected tab's launches before filtering; null while loading. */
    val tabLaunches: List<Launch>? get() = if (filters.tab == LaunchesTab.UPCOMING) upcoming else past

    val freshness: DataFreshness get() = if (filters.tab == LaunchesTab.UPCOMING) upcomingFreshness else pastFreshness

    /** The providers on the selected tab, for the filter's menu. */
    val providers: List<String> get() = tabLaunches.orEmpty().mapNotNull { it.provider }.distinct().sorted()

    val shown: List<Launch>
        get() = tabLaunches.orEmpty().filter { launch ->
            val providerMatches = filters.provider == null || launch.provider == filters.provider
            providerMatches && (!filters.crewedOnly || launch.isCrewed)
        }
}

/**
 * The Launches screen: both lists from the cache. Opening it refreshes what is due, the upcoming
 * list at most hourly (the same limit the sync uses, so no extra call) and the past one at most
 * every six hours; there is no pull-to-refresh, which would only spend Launch Library 2's small
 * hourly budget.
 */
@HiltViewModel
class LaunchesViewModel @Inject constructor(
    observeUpcomingLaunches: ObserveUpcomingLaunchesUseCase,
    observePastLaunches: ObservePastLaunchesUseCase,
    observeReminders: ObserveLaunchRemindersUseCase,
    observeFreshness: ObserveDataFreshnessUseCase,
    synchronizeUpcomingLaunches: SynchronizeUpcomingLaunchesUseCase,
    refreshPastLaunches: RefreshPastLaunchesUseCase,
    private val setLaunchReminder: SetLaunchReminderUseCase,
) : ViewModel() {

    private val filters = MutableStateFlow(LaunchFilters())

    val uiState: StateFlow<LaunchesUiState> = combine(
        combine(observeUpcomingLaunches(), observePastLaunches(), ::Pair),
        filters,
        observeReminders(),
        observeFreshness(SyncSource.LAUNCHES),
        observeFreshness(SyncSource.LAUNCHES_PAST),
    ) { (upcoming, past), filters, reminders, upcomingFreshness, pastFreshness ->
        LaunchesUiState(
            upcoming = upcoming,
            past = past,
            filters = filters,
            reminders = reminders,
            upcomingFreshness = upcomingFreshness,
            pastFreshness = pastFreshness,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = LaunchesUiState(),
    )

    init {
        viewModelScope.launch { synchronizeUpcomingLaunches() }
        viewModelScope.launch { refreshPastLaunches() }
    }

    fun onTabSelected(tab: LaunchesTab) {
        // A provider picked on one tab may not fly on the other.
        filters.update { if (it.tab == tab) it else it.copy(tab = tab, provider = null) }
    }

    fun onProviderSelected(provider: String?) {
        filters.update { it.copy(provider = provider) }
    }

    fun onCrewedOnlyChange(crewedOnly: Boolean) {
        filters.update { it.copy(crewedOnly = crewedOnly) }
    }

    fun onReminderChange(launchId: String, enabled: Boolean) {
        viewModelScope.launch { setLaunchReminder(launchId, enabled) }
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
