package com.radustavila.nasaexplorer.feature.earth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.usecase.FetchEpicDayUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEpicAvailableDatesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEpicDayUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEpicFavoriteIdsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEpicSyncStateUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveLatestEpicDayUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshEpicUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ToggleEpicFavoriteUseCase
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.core.model.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Earth today state holder.
 *
 * The photos on screen are whatever Room holds for the current selection (collection + day), so
 * the state is derived, never fetched into: the newest day comes from the EPIC sync, and a day
 * picked outside the cached week is downloaded into the cache and arrives through the same Flow.
 * The selection survives process death through [SavedStateHandle].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EarthTodayViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeLatestEpicDay: ObserveLatestEpicDayUseCase,
    private val observeEpicDay: ObserveEpicDayUseCase,
    private val observeEpicAvailableDates: ObserveEpicAvailableDatesUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
    observeEpicSyncState: ObserveEpicSyncStateUseCase,
    observeEpicFavoriteIds: ObserveEpicFavoriteIdsUseCase,
    private val refreshEpic: RefreshEpicUseCase,
    private val fetchEpicDay: FetchEpicDayUseCase,
    private val toggleEpicFavorite: ToggleEpicFavoriteUseCase,
) : ViewModel() {

    private val selection = MutableStateFlow(
        Selection(
            collection = savedStateHandle.get<String>(KEY_COLLECTION)
                ?.let(EpicCollection::fromApiValue)
                ?: EpicCollection.NATURAL,
            date = savedStateHandle.get<String>(KEY_DATE)?.let(::parseDateOrNull),
        ),
    )

    private val localState = MutableStateFlow(
        LocalState(focusedImageId = savedStateHandle.get<String>(KEY_IMAGE_ID)),
    )

    /**
     * The photos together with the selection they belong to. Rendering both from one emission keeps
     * the screen consistent: no frame shows the new collection's toggle over the old set's photos.
     */
    private val shownDay = selection.flatMapLatest { current ->
        val (collection, date) = current
        val images = if (date == null) observeLatestEpicDay(collection) else observeEpicDay(collection, date)
        images.map { current to it }
    }

    private val availableDates = selection
        .map { it.collection }
        .distinctUntilChanged()
        .flatMapLatest { collection -> observeEpicAvailableDates(collection) }
        .map { dates -> dates.toSet() }

    val uiState: StateFlow<EarthTodayUiState> = combine(
        combine(shownDay, availableDates, observeEpicFavoriteIds(), ::Triple),
        observeConnectivity(),
        observeEpicSyncState(),
        localState,
    ) { (day, dates, favoriteIds), isOnline, syncState, local ->
        val (current, images) = day
        // A sync waiting for connectivity cannot make progress; showing it as such would spin forever.
        val isSyncMakingProgress = syncState == SyncState.Syncing ||
            (syncState == SyncState.Enqueued && isOnline)
        // Only the newest day is what the sync brings in; an archive day only waits on its download.
        val isWaitingForSync = current.date == null && isSyncMakingProgress

        EarthTodayUiState(
            images = images,
            collection = current.collection,
            selectedDate = current.date,
            availableDates = dates,
            favoriteIds = favoriteIds,
            focusedImageId = local.focusedImageId,
            isLoading = images.isEmpty() && (isWaitingForSync || local.isFetchingDay),
            isRefreshing = images.isNotEmpty() && isWaitingForSync,
            isOffline = !isOnline,
            error = local.error ?: (syncState as? SyncState.Failed)?.error
                ?.takeIf { current.date == null }
                ?.toUiError(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = EarthTodayUiState(
            collection = selection.value.collection,
            selectedDate = selection.value.date,
            isLoading = true,
        ),
    )

    init {
        refreshEpic()
        downloadSelectedDayIfMissing()
    }

    /**
     * Keeps the picked day when the other collection has it too. The enhanced set is published
     * later and has its own gaps, so a day may exist in only one of them; the newest day is then
     * the closest thing to what was on screen.
     */
    fun onCollectionSelected(collection: EpicCollection) {
        val date = selection.value.date
        if (date == null) {
            updateSelection { it.copy(collection = collection) }
            return
        }
        viewModelScope.launch {
            val dates = observeEpicAvailableDates(collection).first()
            val keptDate = date.takeIf { dates.isEmpty() || it in dates }
            updateSelection { it.copy(collection = collection, date = keptDate) }
        }
    }

    /** `null` returns to the newest day. */
    fun onDateSelected(date: LocalDate?) {
        updateSelection { it.copy(date = date) }
    }

    fun onRetry() {
        localState.update { it.copy(error = null) }
        if (selection.value.date == null) refreshEpic() else downloadSelectedDayIfMissing()
    }

    fun onToggleFavorite(image: EpicImage) {
        val isFavorite = image.ref.favoriteId in uiState.value.favoriteIds
        viewModelScope.launch {
            val result = toggleEpicFavorite(image, isFavorite = isFavorite)
            if (result is DataResult.Failure) localState.update { it.copy(error = result.error.toUiError()) }
        }
    }

    fun onErrorDismissed() {
        localState.update { it.copy(error = null) }
    }

    private fun updateSelection(transform: (Selection) -> Selection) {
        // Cleared before the new selection is published: a different day or set is a new rotation,
        // and the photo the screen was opened on must not leak into it, not even for one frame.
        localState.update { it.copy(isFetchingDay = false, error = null, focusedImageId = null) }
        val updated = transform(selection.value)
        selection.value = updated
        savedStateHandle[KEY_COLLECTION] = updated.collection.apiValue
        savedStateHandle[KEY_DATE] = updated.date?.toString()
        savedStateHandle[KEY_IMAGE_ID] = null
        downloadSelectedDayIfMissing()
    }

    /**
     * The cache keeps the newest week; any other day is fetched once when selected. Whatever
     * arrives is written to Room and reaches the screen through [images].
     */
    private fun downloadSelectedDayIfMissing() {
        val (collection, date) = selection.value
        if (date == null) return

        localState.update { it.copy(isFetchingDay = true) }
        viewModelScope.launch {
            val isCached = observeEpicDay(collection, date).first().isNotEmpty()
            val result = if (isCached) null else fetchEpicDay(collection, date)
            // A later selection owns the state now; this answer is about a day no longer on screen.
            if (selection.value != Selection(collection, date)) return@launch

            localState.update {
                it.copy(
                    isFetchingDay = false,
                    error = (result as? DataResult.Failure)?.error?.toUiError(),
                )
            }
        }
    }

    private data class Selection(val collection: EpicCollection, val date: LocalDate?)

    private data class LocalState(
        val isFetchingDay: Boolean = false,
        val error: UiError? = null,
        /** The photo the screen was opened on (a favourite), until the selection changes. */
        val focusedImageId: String? = null,
    )

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
        // The names of EarthTodayRoute's arguments, which navigation puts in the SavedStateHandle.
        const val KEY_COLLECTION = "collection"
        const val KEY_DATE = "date"
        const val KEY_IMAGE_ID = "imageId"

        fun parseDateOrNull(raw: String): LocalDate? = try {
            LocalDate.parse(raw)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
