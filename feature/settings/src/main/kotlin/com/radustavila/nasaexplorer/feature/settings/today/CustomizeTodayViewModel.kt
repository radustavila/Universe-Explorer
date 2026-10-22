package com.radustavila.nasaexplorer.feature.settings.today

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveTodayCardsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetTodayCardsUseCase
import com.radustavila.nasaexplorer.core.model.TodayCardId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Immutable
data class CustomizeTodayUiState(
    /** False until the saved choice has been read, so the lists do not jump. */
    val isLoaded: Boolean = false,
    /** On Today, top to bottom. */
    val shown: List<TodayCardId> = emptyList(),
    /** Every other card, in a fixed order. */
    val hidden: List<TodayCardId> = emptyList(),
) {
    val isDefault: Boolean get() = shown == TodayCardId.DEFAULT
}

/**
 * Which cards Today shows and in what order. Every change is saved at once, applied to the stored
 * list rather than to the last state shown, one at a time, so quick taps never undo each other.
 */
@HiltViewModel
class CustomizeTodayViewModel @Inject constructor(
    private val observeTodayCards: ObserveTodayCardsUseCase,
    private val setTodayCards: SetTodayCardsUseCase,
) : ViewModel() {

    private val edits = Mutex()

    val uiState: StateFlow<CustomizeTodayUiState> = observeTodayCards()
        .map { shown ->
            CustomizeTodayUiState(isLoaded = true, shown = shown, hidden = TodayCardId.entries - shown.toSet())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
            initialValue = CustomizeTodayUiState(),
        )

    /** Showing a card adds it at the bottom of Today; hiding one keeps the others' order. */
    fun onShownChange(card: TodayCardId, shown: Boolean) = edit { current ->
        if (shown) current - card + card else current - card
    }

    /** The shown cards in their new order, after a drag. */
    fun onReorder(order: List<TodayCardId>) = edit { current ->
        // Only a reordering: a card shown or hidden meanwhile keeps that choice.
        order.filter { it in current } + current.filter { it !in order }
    }

    /** Moves a shown card one step up ([offset] -1) or down (+1), for the accessibility actions. */
    fun onMove(card: TodayCardId, offset: Int) = edit { current ->
        val from = current.indexOf(card)
        val to = from + offset
        if (from < 0 || to !in current.indices) {
            current
        } else {
            current.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    fun onReset() = edit { TodayCardId.DEFAULT }

    private fun edit(change: (List<TodayCardId>) -> List<TodayCardId>) {
        viewModelScope.launch {
            edits.withLock {
                val current = observeTodayCards().first()
                val next = change(current)
                if (next != current) setTodayCards(next)
            }
        }
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
