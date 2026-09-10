package com.radustavila.nasaexplorer.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.usecase.LoadOlderApodsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveApodFeedUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSyncStateUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshFeedUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ToggleFavoriteUseCase
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.model.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Feed state holder.
 *
 * The database is the source of truth, so the state is *derived*: cached entries, connectivity and
 * synchronisation state are combined into one immutable [FeedUiState]. The ViewModel never fetches
 * anything itself - it asks the domain layer to refresh and waits for Room to emit.
 */
@HiltViewModel
class FeedViewModel @Inject constructor(
    observeApodFeed: ObserveApodFeedUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
    observeSyncState: ObserveSyncStateUseCase,
    private val refreshFeed: RefreshFeedUseCase,
    private val loadOlderApods: LoadOlderApodsUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<FeedUiState> = combine(
        observeApodFeed(),
        observeConnectivity(),
        observeSyncState(),
        localState,
    ) { items, isOnline, syncState, local ->
        // A sync that is enqueued while offline cannot run: WorkManager holds it until the
        // connectivity constraint is met. Reporting it as "refreshing" would spin forever, so
        // progress is only shown for work that can actually make progress.
        val isSyncMakingProgress = syncState == SyncState.Syncing ||
            (syncState == SyncState.Enqueued && isOnline)

        FeedUiState(
            items = items,
            isLoading = items.isEmpty() && (isSyncMakingProgress || local.isLoadingMore),
            isRefreshing = items.isNotEmpty() && isSyncMakingProgress,
            isLoadingMore = local.isLoadingMore,
            isOffline = !isOnline,
            hasReachedArchiveStart = local.hasReachedArchiveStart,
            error = local.error ?: (syncState as? SyncState.Failed)?.error?.toUiError(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = FeedUiState(isLoading = true),
    )

    init {
        // Cached content is displayed immediately by the flow above; this only asks the sync
        // pipeline for fresher data. It is a no-op when a sync is already scheduled.
        refreshFeed()
    }

    fun onRefresh() {
        localState.update { it.copy(error = null) }
        refreshFeed()
    }

    fun onRetry() = onRefresh()

    fun onErrorDismissed() {
        localState.update { it.copy(error = null) }
    }

    fun onLoadMore() {
        val current = localState.value
        if (current.isLoadingMore || current.hasReachedArchiveStart) return

        localState.update { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch {
            when (val result = loadOlderApods()) {
                is DataResult.Success -> localState.update {
                    it.copy(
                        isLoadingMore = false,
                        hasReachedArchiveStart = result.data == 0,
                    )
                }

                is DataResult.Failure -> localState.update {
                    it.copy(isLoadingMore = false, error = result.error.toUiError())
                }
            }
        }
    }

    fun onToggleFavorite(apod: Apod) {
        viewModelScope.launch {
            val result = toggleFavorite(apod)
            if (result is DataResult.Failure) {
                localState.update { it.copy(error = result.error.toUiError()) }
            }
        }
    }

    /** State owned by the screen rather than by the database. */
    private data class LocalState(
        val isLoadingMore: Boolean = false,
        val hasReachedArchiveStart: Boolean = false,
        val error: UiError? = null,
    )

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
