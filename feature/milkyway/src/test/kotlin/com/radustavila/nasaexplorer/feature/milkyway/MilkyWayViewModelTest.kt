package com.radustavila.nasaexplorer.feature.milkyway

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveMilkyWayOutlookUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.model.LocationSource
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.testing.data.TestMilkyWay
import com.radustavila.nasaexplorer.core.testing.fake.FakeMilkyWayCalculator
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MilkyWayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-07-10T12:00:00Z"))
    private val location = FakeObserverLocationRepository()
    private val calculator = FakeMilkyWayCalculator(TestMilkyWay.nights(), TestMilkyWay.SEASON)
    private val madrid = ObserverLocation(TestMilkyWay.MADRID, "Madrid", LocationSource.MANUAL)

    @Test
    fun `without a saved place there is nothing to compute`() = runTest {
        createViewModel().uiState.test {
            val state = awaitUntil { it.isLocationLoaded }
            assertNull(state.location)
            assertNull(state.outlook)
            assertEquals(emptyList<Any>(), calculator.requests)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the next 30 nights are computed for the saved place, again when it moves`() = runTest {
        location.setLocation(madrid)

        createViewModel().uiState.test {
            val state = awaitUntil { it.outlook != null }
            assertEquals(madrid, state.location)
            assertEquals(30, state.outlook?.nights?.size)
            assertEquals(TestMilkyWay.SEASON, state.outlook?.season)

            val sydney = ObserverLocation(TestMilkyWay.MADRID.copy(latitude = -33.87), "Sydney", LocationSource.MANUAL)
            location.setLocation(sydney)
            awaitUntil { it.location == sydney && it.outlook != null }
            assertEquals(sydney.coordinate, calculator.requests.last().first)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<MilkyWayUiState>.awaitUntil(
        predicate: (MilkyWayUiState) -> Boolean,
    ): MilkyWayUiState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }

    private fun createViewModel() = MilkyWayViewModel(
        observeLocation = ObserveObserverLocationUseCase(location),
        observeOutlook = ObserveMilkyWayOutlookUseCase(calculator, clock, mainDispatcherRule.testDispatcher),
    )
}
