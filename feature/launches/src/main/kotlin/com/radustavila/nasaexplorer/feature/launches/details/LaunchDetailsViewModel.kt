package com.radustavila.nasaexplorer.feature.launches.details

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveLaunchRemindersUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveLaunchUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetLaunchReminderUseCase
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.feature.launches.LaunchDetailsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class LaunchDetailsUiState(
    val id: String,
    val isLoaded: Boolean = false,
    /** Null once loaded when the launch is not cached (it dropped off both lists). */
    val launch: Launch? = null,
    val hasReminder: Boolean = false,
)

/**
 * One launch from the cache. Every detail the page shows came with the hourly list, so opening it
 * costs no call of Launch Library 2's budget.
 */
@HiltViewModel
class LaunchDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeLaunch: ObserveLaunchUseCase,
    observeReminders: ObserveLaunchRemindersUseCase,
    private val setLaunchReminder: SetLaunchReminderUseCase,
) : ViewModel() {

    private val id = savedStateHandle.toRoute<LaunchDetailsRoute>().id

    val uiState: StateFlow<LaunchDetailsUiState> = combine(observeLaunch(id), observeReminders()) { launch, reminders ->
        LaunchDetailsUiState(id = id, isLoaded = true, launch = launch, hasReminder = id in reminders)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = LaunchDetailsUiState(id = id),
    )

    fun onReminderChange(enabled: Boolean) {
        viewModelScope.launch { setLaunchReminder(id, enabled) }
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
