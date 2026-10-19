package com.radustavila.nasaexplorer.feature.spaceweather.smallbody

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.usecase.FetchSmallBodyDetailsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCloseApproachUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSmallBodyDetailsUseCase
import com.radustavila.nasaexplorer.core.model.CloseApproach
import com.radustavila.nasaexplorer.core.model.SmallBodyDetails
import com.radustavila.nasaexplorer.feature.spaceweather.R
import com.radustavila.nasaexplorer.feature.spaceweather.SmallBodyRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SmallBodyUiState(
    val designation: String,
    val approach: CloseApproach? = null,
    val details: SmallBodyDetails? = null,
    val isLoadingDetails: Boolean = false,
    /** Why the details could not be fetched; the cached approach is still shown. */
    val detailsError: UiError? = null,
)

/**
 * One object: its approach and Sentry entry from the cached list, its orbit from the Small-Body
 * Database. Details are fetched once and cached, so the screen reopens offline.
 */
@HiltViewModel
class SmallBodyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeApproach: ObserveCloseApproachUseCase,
    observeDetails: ObserveSmallBodyDetailsUseCase,
    private val fetchDetails: FetchSmallBodyDetailsUseCase,
) : ViewModel() {

    private val designation: String = savedStateHandle.toRoute<SmallBodyRoute>().designation
    private val fetchState = MutableStateFlow(FetchState())
    private val details = observeDetails(designation)

    val uiState: StateFlow<SmallBodyUiState> = combine(
        observeApproach(designation),
        details,
        fetchState,
    ) { approach, details, fetch ->
        SmallBodyUiState(
            designation = designation,
            approach = approach,
            details = details,
            isLoadingDetails = fetch.isLoading,
            detailsError = fetch.error.takeIf { details == null },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = SmallBodyUiState(designation = designation),
    )

    init {
        viewModelScope.launch {
            if (details.first() == null) load()
        }
    }

    fun onRetry() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (fetchState.value.isLoading) return
        fetchState.value = FetchState(isLoading = true)
        fetchState.value = when (val result = fetchDetails(designation)) {
            is DataResult.Success -> FetchState()
            is DataResult.Failure -> FetchState(
                // The shared offline text speaks of cached content, which a never-opened object lacks.
                error = if (result.error == AppError.NoConnection) {
                    UiError(R.string.small_body_details_offline)
                } else {
                    result.error.toUiError()
                },
            )
        }
    }

    private data class FetchState(val isLoading: Boolean = false, val error: UiError? = null)

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
