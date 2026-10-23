package com.radustavila.nasaexplorer.feature.comets

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCometTonightUseCase
import com.radustavila.nasaexplorer.core.model.CometTonight
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Immutable
data class CometDetailsUiState(
    val designation: String,
    val isLoaded: Boolean = false,
    /** Null once loaded when the comet is not cached (dropped from the list, or out of date). */
    val comet: CometTonight? = null,
)

/** One comet from the cache, for the saved place. */
@HiltViewModel
class CometDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeCometTonight: ObserveCometTonightUseCase,
) : ViewModel() {

    private val designation = savedStateHandle.toRoute<CometDetailsRoute>().designation

    val uiState: StateFlow<CometDetailsUiState> = observeCometTonight(designation)
        .map { CometDetailsUiState(designation = designation, isLoaded = true, comet = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
            initialValue = CometDetailsUiState(designation = designation),
        )

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
