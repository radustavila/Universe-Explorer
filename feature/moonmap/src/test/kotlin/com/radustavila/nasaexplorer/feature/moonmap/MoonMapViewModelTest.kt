package com.radustavila.nasaexplorer.feature.moonmap

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.usecase.DownloadMoonTilesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveLunarLandingSitesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveMoonIlluminationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveMoonTilesPackUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveMoonTilesUseCase
import com.radustavila.nasaexplorer.core.model.LunarProgramme
import com.radustavila.nasaexplorer.core.model.MoonTilesPack
import com.radustavila.nasaexplorer.core.model.SelenographicPoint
import com.radustavila.nasaexplorer.core.testing.data.TestMoonMap
import com.radustavila.nasaexplorer.core.testing.fake.FakeLunarLandingSiteRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeMoonIlluminationCalculator
import com.radustavila.nasaexplorer.core.testing.fake.FakeMoonTilesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MoonMapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-23T20:00:00Z"))
    private val calculator = FakeMoonIlluminationCalculator(SelenographicPoint(0.0, 0.0))
    private val tiles = FakeMoonTilesRepository()

    @Test
    fun `every site and programme shows, with the night side from the subsolar point`() = runTest {
        createViewModel().uiState.test {
            val state = awaitUntil { it.illumination != null && it.tiles != null }
            assertEquals(TestMoonMap.sites, state.sites)
            assertEquals(listOf(LunarProgramme.LUNA, LunarProgramme.APOLLO, LunarProgramme.CHANGE), state.programmes)
            assertEquals(state.programmes.toSet(), state.shownProgrammes)
            assertEquals(2, state.nightSide.size) // full Moon: the far side, split at ±180°
            assertEquals(MoonTilesPack.NotDownloaded(57_000_000L), state.tiles)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a programme chip hides and shows its sites`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onProgrammeToggled(LunarProgramme.APOLLO)
            val hidden = awaitUntil { LunarProgramme.APOLLO !in it.shownProgrammes }
            assertTrue(hidden.sites.none { it.programme == LunarProgramme.APOLLO })
            assertEquals(2, hidden.sites.size)

            viewModel.onProgrammeToggled(LunarProgramme.APOLLO)
            assertEquals(4, awaitUntil { LunarProgramme.APOLLO in it.shownProgrammes }.sites.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping a site selects it until the sheet is dismissed`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onSiteSelected("apollo-11")
            assertEquals(TestMoonMap.apollo11, awaitUntil { it.selectedSite != null }.selectedSite)
            viewModel.onSiteDismissed()
            assertNull(awaitUntil { it.selectedSite == null }.selectedSite)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the terminator is recomputed every ten minutes`() = runTest {
        createViewModel().uiState.test {
            awaitUntil { it.illumination != null }
            val before = calculator.instants.size
            clock.currentInstant = clock.currentInstant.plusSeconds(600)
            calculator.subsolarPoint = SelenographicPoint(0.0, 180.0)
            val interval = ObserveMoonIlluminationUseCase.UPDATE_INTERVAL.toMillis()
            mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(interval + 1)
            val state = awaitUntil { it.illumination?.subsolarPoint?.longitude == 180.0 }
            assertEquals(1, state.nightSide.size)
            assertTrue(calculator.instants.size > before)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `download and remove go to the pack, and its state follows`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitUntil { it.tiles != null }
            viewModel.onDownloadTiles()
            tiles.pack.value = MoonTilesPack.Downloading(10, 100)
            assertEquals(MoonTilesPack.Downloading(10, 100), awaitUntil { it.tiles is MoonTilesPack.Downloading }.tiles)
            tiles.pack.value = MoonTilesPack.Installed("/data/tiles", 57_000_000L)
            awaitUntil { it.tiles is MoonTilesPack.Installed }
            viewModel.onRemoveTiles()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, tiles.downloads)
        assertEquals(1, tiles.removals)
    }

    private suspend fun ReceiveTurbine<MoonMapUiState>.awaitUntil(
        predicate: (MoonMapUiState) -> Boolean,
    ): MoonMapUiState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }

    private fun createViewModel() = MoonMapViewModel(
        observeSites = ObserveLunarLandingSitesUseCase(FakeLunarLandingSiteRepository()),
        observeIllumination = ObserveMoonIlluminationUseCase(calculator, clock, mainDispatcherRule.testDispatcher),
        observeTiles = ObserveMoonTilesPackUseCase(tiles),
        downloadTiles = DownloadMoonTilesUseCase(tiles),
        removeTiles = RemoveMoonTilesUseCase(tiles),
    )
}
