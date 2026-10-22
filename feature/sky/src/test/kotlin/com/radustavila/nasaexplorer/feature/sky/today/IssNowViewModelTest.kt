package com.radustavila.nasaexplorer.feature.sky.today

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.sky.IssPassForecast
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIssPassesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIssPositionUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyTonightUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshOrbitalElementsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.fake.FakeHomeWidgetUpdater
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatellitePassPredictor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatelliteRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyCalculator
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class IssNowViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-19T20:00:00Z"))
    private val location = FakeObserverLocationRepository()
    private val satellites = FakeSatelliteRepository()
    private val predictor = FakeSatellitePassPredictor(
        passes = listOf(
            TestSky.pass(start = Instant.parse("2026-10-20T01:00:00Z"), isVisible = false),
            TestSky.pass(start = Instant.parse("2026-10-20T02:07:31Z"), isVisible = true),
        ),
        position = TestSky.position(isLive = false),
    )

    @Test
    fun `shows the position and the next visible pass over the saved place`() = runTest {
        satellites.setElements(TestSky.issElements(epoch = clock.currentInstant))
        location.setLocation(TestSky.location())

        createViewModel().uiState.test {
            val state = awaitUntil { it.position != null && it.nextPass is IssNextPass.Next }
            assertEquals(TestSky.position().coordinate, state.position?.coordinate)
            assertEquals(Instant.parse("2026-10-20T02:07:31Z"), (state.nextPass as IssNextPass.Next).pass.start)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `without a place it asks for one, without an orbit it has no position`() = runTest {
        createViewModel().uiState.test {
            val state = awaitUntil { it.isLoaded }
            assertNull(state.position)
            assertEquals(IssNextPass.NoPlace, state.nextPass)

            location.setLocation(TestSky.location())
            assertEquals(IssNextPass.NoOrbit, awaitUntil { it.nextPass != IssNextPass.NoPlace }.nextPass)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showing the card refreshes the orbit when it is due, no more than twice a day`() = runTest {
        satellites.elementsOnRefresh = TestSky.issElements(epoch = clock.currentInstant)

        createViewModel().uiState.test {
            awaitUntil { it.position != null }
            cancelAndIgnoreRemainingEvents()
        }
        createViewModel()

        assertEquals(1, satellites.refreshCallCount)
    }

    @Test
    fun `stale or missing elements and passes out of sight give the right explanation`() {
        val now = clock.currentInstant
        val fresh = TestSky.issElements(epoch = now)
        val forecast = IssPassForecast(
            elements = fresh,
            passes = listOf(TestSky.pass(isVisible = false)),
            isStale = false,
        )

        assertEquals(IssNextPass.NoneVisible, forecast.nextVisiblePass(now))
        assertEquals(IssNextPass.NoOrbit, forecast.copy(elements = null).nextVisiblePass(now))
        assertEquals(IssNextPass.NoOrbit, forecast.copy(isStale = true).nextVisiblePass(now))
        // A visible pass that is already over is not the next one.
        val over = forecast.copy(passes = listOf(TestSky.pass(start = now.minus(Duration.ofHours(1)))))
        assertEquals(IssNextPass.NoneVisible, over.nextVisiblePass(now))
    }

    private val metadata = FakeSyncMetadataRepository()

    private fun createViewModel(): IssNowViewModel {
        val dispatcher = mainDispatcherRule.testDispatcher
        return IssNowViewModel(
            observeLocation = ObserveObserverLocationUseCase(location),
            observeIssPasses = ObserveIssPassesUseCase(satellites, predictor, clock, dispatcher),
            observeIssPosition = ObserveIssPositionUseCase(satellites, predictor, clock, dispatcher),
            refreshElements = RefreshOrbitalElementsUseCase(
                satellites,
                RefreshSourceUseCase(metadata, clock),
                FakeHomeWidgetUpdater(),
            ),
            timeProvider = clock,
        )
    }
}

class SkyTonightSummaryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-19T12:00:00Z"))
    private val location = FakeObserverLocationRepository()
    private val calculator = FakeSkyCalculator()

    @Test
    fun `computes tonight for the saved place, and nothing without one`() = runTest {
        val viewModel = SkyTonightSummaryViewModel(
            observeLocation = ObserveObserverLocationUseCase(location),
            observeSkyTonight = ObserveSkyTonightUseCase(calculator, clock, mainDispatcherRule.testDispatcher),
        )

        viewModel.uiState.test {
            val empty = awaitUntil { it.isLoaded }
            assertNull(empty.sky)

            location.setLocation(TestSky.location(coordinate = GeoCoordinate(-33.87, 151.21)))
            val state = awaitUntil { it.sky != null }
            assertEquals(calculator.sky, state.sky)
            assertEquals(true, state.southernHemisphere)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    var state = awaitItem()
    while (!predicate(state)) state = awaitItem()
    return state
}
