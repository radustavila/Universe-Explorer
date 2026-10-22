package com.radustavila.nasaexplorer.feature.settings.today

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveTodayCardsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetTodayCardsUseCase
import com.radustavila.nasaexplorer.core.model.TodayCardId
import com.radustavila.nasaexplorer.core.model.TodayCardId.DSN_NOW
import com.radustavila.nasaexplorer.core.model.TodayCardId.EARTH_EVENTS
import com.radustavila.nasaexplorer.core.model.TodayCardId.EARTH_TODAY
import com.radustavila.nasaexplorer.core.model.TodayCardId.ISS_NOW
import com.radustavila.nasaexplorer.core.model.TodayCardId.NEXT_LAUNCHES
import com.radustavila.nasaexplorer.core.model.TodayCardId.SKY_TONIGHT
import com.radustavila.nasaexplorer.core.model.TodayCardId.SPACE_WEATHER
import com.radustavila.nasaexplorer.core.testing.fake.FakeTodayCardsRepository
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CustomizeTodayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeTodayCardsRepository()
    // Built once the rule has installed the test Main dispatcher, which viewModelScope captures.
    private val viewModel by lazy {
        CustomizeTodayViewModel(ObserveTodayCardsUseCase(repository), SetTodayCardsUseCase(repository))
    }

    @Test
    fun `lists the shown cards in order and every other card as hidden`() = runTest {
        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(TodayCardId.DEFAULT, state.shown)
            assertEquals(listOf(ISS_NOW, SKY_TONIGHT, SPACE_WEATHER, DSN_NOW), state.hidden)
            assertTrue(state.isDefault)
        }
    }

    @Test
    fun `showing a card adds it at the bottom, hiding one keeps the others' order`() = runTest {
        viewModel.uiState.test {
            awaitLoaded()

            viewModel.onShownChange(ISS_NOW, shown = true)
            assertEquals(listOf(EARTH_TODAY, EARTH_EVENTS, NEXT_LAUNCHES, ISS_NOW), awaitItem().shown)

            viewModel.onShownChange(EARTH_EVENTS, shown = false)
            val state = awaitItem()
            assertEquals(listOf(EARTH_TODAY, NEXT_LAUNCHES, ISS_NOW), state.shown)
            assertEquals(listOf(EARTH_EVENTS, SKY_TONIGHT, SPACE_WEATHER, DSN_NOW), state.hidden)
            assertFalse(state.isDefault)
        }
        assertEquals(listOf(EARTH_TODAY, NEXT_LAUNCHES, ISS_NOW), repository.currentTodayCards)
    }

    @Test
    fun `a drag saves the new order, a step moves one card, reset restores the default`() = runTest {
        viewModel.uiState.test {
            awaitLoaded()

            viewModel.onReorder(listOf(NEXT_LAUNCHES, EARTH_TODAY, EARTH_EVENTS))
            assertEquals(listOf(NEXT_LAUNCHES, EARTH_TODAY, EARTH_EVENTS), awaitItem().shown)

            viewModel.onMove(EARTH_EVENTS, offset = -1)
            assertEquals(listOf(NEXT_LAUNCHES, EARTH_EVENTS, EARTH_TODAY), awaitItem().shown)

            // Past either end nothing moves.
            viewModel.onMove(NEXT_LAUNCHES, offset = -1)
            viewModel.onReset()
            assertEquals(TodayCardId.DEFAULT, awaitItem().shown)
        }
    }

    @Test
    fun `quick taps each apply, so every card can be hidden`() = runTest {
        TodayCardId.DEFAULT.forEach { viewModel.onShownChange(it, shown = false) }
        advanceUntilIdle()

        assertEquals(emptyList<TodayCardId>(), repository.currentTodayCards)
        viewModel.uiState.test {
            assertEquals(TodayCardId.entries, awaitLoaded().hidden)
        }
    }
}

private suspend fun ReceiveTurbine<CustomizeTodayUiState>.awaitLoaded(): CustomizeTodayUiState {
    var state = awaitItem()
    while (!state.isLoaded) state = awaitItem()
    return state
}
