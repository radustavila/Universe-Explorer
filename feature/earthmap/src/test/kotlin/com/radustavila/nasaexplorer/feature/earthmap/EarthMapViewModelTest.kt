package com.radustavila.nasaexplorer.feature.earthmap

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.map.gibs.GibsImagery
import com.radustavila.nasaexplorer.core.map.gibs.GibsLayer
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EarthMapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 19)
    private val yesterday = today.minusDays(1)
    private val networkMonitor = FakeNetworkMonitor()

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = EarthMapViewModel(
        savedStateHandle = savedStateHandle,
        timeProvider = FakeTimeProvider(currentDate = today),
        networkMonitor = networkMonitor,
    )

    @Test
    fun `opens on yesterday's true colour`() = runTest {
        createViewModel().uiState.test {
            val state = awaitItem()
            assertEquals(EarthMapMode.TrueColor, state.mode)
            assertEquals(yesterday, state.date)
            assertEquals(yesterday, state.maxDate)
            assertEquals(LocalDate.of(2015, 1, 1), state.minDate)
            assertEquals(GibsImagery.trueColor(yesterday), state.imagery)
            assertFalse(state.isImageryPending)
            assertTrue(state.isDateSelectable)
        }
    }

    @Test
    fun `switches layers`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItem()

            viewModel.onModeSelected(EarthMapMode.NightLights)
            val night = awaitItem()
            assertEquals(GibsLayer.ViirsBlackMarble, night.imagery.layer)
            assertFalse(night.isDateSelectable)

            viewModel.onModeSelected(EarthMapMode.BlueMarble)
            assertEquals(GibsLayer.BlueMarble, awaitItem().imagery.layer)
        }
    }

    @Test
    fun `changes the day within 2015 and yesterday`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItem()

            viewModel.onPreviousDay()
            assertEquals(yesterday.minusDays(1), awaitItem().date)

            viewModel.onDateSelected(LocalDate.of(2015, 6, 1))
            assertEquals(LocalDate.of(2015, 6, 1), awaitItem().date)

            viewModel.onDateSelected(LocalDate.of(2010, 1, 1))
            assertEquals(LocalDate.of(2015, 1, 1), awaitItem().date)

            viewModel.onDateSelected(today.plusDays(3))
            assertEquals(yesterday, awaitItem().date)

            viewModel.onNextDay()
            expectNoEvents()
        }
    }

    @Test
    fun `the map loads a picked day only once the picker rests`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItem()

            viewModel.onPreviousDay()
            viewModel.onPreviousDay()
            viewModel.onPreviousDay()
            val picked = yesterday.minusDays(3)
            var state = awaitItem()
            while (state.date != picked) state = awaitItem()
            assertTrue(state.isImageryPending)
            assertEquals(GibsImagery.trueColor(yesterday), state.imagery)

            advanceTimeBy(EarthMapViewModel.IMAGERY_DEBOUNCE_MILLIS + 1)
            val loaded = awaitItem()
            assertFalse(loaded.isImageryPending)
            assertEquals(GibsImagery.trueColor(picked), loaded.imagery)
            expectNoEvents()
        }
    }

    @Test
    fun `days before Suomi NPP use Terra, with Aqua filling the gaps`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onDateSelected(LocalDate.of(2015, 6, 1))
            advanceTimeBy(EarthMapViewModel.IMAGERY_DEBOUNCE_MILLIS + 1)

            val imagery = expectMostRecentItem().imagery
            assertEquals(GibsLayer.ModisTerraTrueColor, imagery.layer)
            assertEquals(GibsLayer.ModisAquaTrueColor, imagery.gapFill)
        }
    }

    @Test
    fun `selection survives process death through the saved state`() = runTest {
        val handle = SavedStateHandle()
        createViewModel(handle).run {
            onModeSelected(EarthMapMode.NightLights)
            onDateSelected(LocalDate.of(2020, 3, 4))
        }

        createViewModel(handle).uiState.test {
            val state = awaitItem()
            assertEquals(EarthMapMode.NightLights, state.mode)
            assertEquals(LocalDate.of(2020, 3, 4), state.date)
        }
    }

    @Test
    fun `reports when the device goes offline`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertFalse(awaitItem().isOffline)

            networkMonitor.setOnline(false)
            assertTrue(awaitItem().isOffline)
        }
    }
}
