package com.radustavila.nasaexplorer.feature.skymap

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.usecase.FindConstellationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCometMagnitudeLimitUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.LoadSkyCatalogUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyMapBodiesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyMapNightModeUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshOrbitalElementsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetSkyMapNightModeUseCase
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.skymap.SkyVector
import com.radustavila.nasaexplorer.core.testing.data.TestComets
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.data.TestSkyMap
import com.radustavila.nasaexplorer.core.testing.fake.FakeCometDebugSettingsRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeCometRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeCometSkyCalculator
import com.radustavila.nasaexplorer.core.testing.fake.FakeHomeWidgetUpdater
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatelliteRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyCatalogRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyMapEphemeris
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyMapPreferencesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Duration
import java.time.Instant
import kotlin.math.acos
import kotlin.math.cos
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SkyMapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-22T20:00:00Z"))
    private val location = FakeObserverLocationRepository()
    private val catalog = FakeSkyCatalogRepository()
    private val ephemeris = FakeSkyMapEphemeris()
    private val satellites = FakeSatelliteRepository()
    private val preferences = FakeSkyMapPreferencesRepository()
    private val comets = FakeCometRepository()
    private val cometCalculator = FakeCometSkyCalculator()

    @Test
    fun `without a saved location there is no sky, only the question where`() = runTest {
        createViewModel().uiState.test {
            val state = awaitUntil { it.isLocationLoaded }
            assertNull(state.location)
            assertNull(state.sky)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the catalogue is turned to the saved place and the current time`() = runTest {
        location.setLocation(TestSky.location())

        createViewModel().uiState.test {
            val state = awaitUntil { it.sky != null && it.bodies != null }
            assertEquals(clock.currentInstant, state.instant)
            assertTrue(state.isLive)
            val sky = checkNotNull(state.sky)
            assertEquals(TestSkyMap.catalog(), sky.chart.catalog)
            // The pole star stands at the latitude, a little raised by refraction.
            val polaris = sky.chart.catalog.stars.indexOf(TestSkyMap.POLARIS)
            assertEquals(TestSky.BUCHAREST.latitude, sky.star(polaris).toHorizontal().altitudeDegrees, 0.8)
            assertEquals(TestSkyMap.solarSystem(), state.bodies?.solarSystem)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, catalog.readCount)
        assertEquals(1, satellites.refreshCallCount)
    }

    @Test
    fun `the time slider moves the sky and back to now returns`() = runTest {
        location.setLocation(TestSky.location())
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val live = awaitUntil { it.sky != null }
            viewModel.onTimeOffsetChange(Duration.ofHours(3))
            val inThreeHours = clock.currentInstant.plus(Duration.ofHours(3))
            val later = awaitUntil { it.instant == inThreeHours && it.sky !== live.sky }
            assertEquals(Duration.ofHours(3), later.timeOffset)
            val polaris = later.sky!!.chart.catalog.stars.indexOf(TestSkyMap.POLARIS)
            // Polaris barely moves, Sirius does: the sky has turned 45 degrees.
            val sirius = later.sky.chart.catalog.stars.indexOf(TestSkyMap.SIRIUS)
            val turned = angleBetween(live.sky!!.star(sirius), later.sky.star(sirius))
            assertEquals(45.0 * cos(Math.toRadians(16.7)), turned, 1.5)
            assertEquals(0.0, angleBetween(live.sky.star(polaris), later.sky.star(polaris)), 1.5)

            viewModel.onBackToNow()
            assertTrue(awaitUntil { it.isLive && it.instant == clock.currentInstant }.isLive)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(ephemeris.instants.contains(clock.currentInstant.plus(Duration.ofHours(3))))
    }

    @Test
    fun `a selection is placed in its constellation`() = runTest {
        location.setLocation(TestSky.location())
        ephemeris.constellation = "Tau"
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitUntil { it.sky != null && it.bodies != null }
            viewModel.onSelect(SkyTarget.Body(SolarSystemBody.MOON))
            val state = awaitUntil { it.selected != null && it.selectedConstellation != null }
            assertEquals(SkyTarget.Body(SolarSystemBody.MOON), state.selected)
            assertEquals("Tau", state.selectedConstellation)

            viewModel.onSelect(SkyTarget.Constellation(0))
            assertNull(awaitUntil { it.selected is SkyTarget.Constellation }.selectedConstellation)

            viewModel.onSelect(null)
            assertNull(awaitUntil { it.selected == null }.selectedConstellation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a bright comet is on the map and placed in its constellation, a faint one is not`() = runTest {
        location.setLocation(TestSky.location())
        ephemeris.constellation = "Cyg"
        val bright = TestComets.comet(designation = "2026 B1")
        val faint = TestComets.comet()
        comets.comets.value = listOf(bright, faint)
        cometCalculator.skyPositions = mapOf(
            "2026 B1" to TestComets.skyPosition(bright, magnitude = 8.4),
            "2026 A2" to TestComets.skyPosition(faint, magnitude = 14.2),
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitUntil { it.sky != null && it.bodies != null }
            assertEquals(listOf("2026 B1"), state.bodies?.comets?.map { it.designation })

            viewModel.onSelect(SkyTarget.Comet("2026 B1"))
            assertEquals("Cyg", awaitUntil { it.selectedConstellation != null }.selectedConstellation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with no comet bright enough the map simply has none`() = runTest {
        location.setLocation(TestSky.location())
        comets.comets.value = listOf(TestComets.comet())
        cometCalculator.skyPositions = mapOf("2026 A2" to TestComets.skyPosition(magnitude = 14.2))

        createViewModel().uiState.test {
            val state = awaitUntil { it.sky != null && it.bodies != null }
            assertTrue(state.bodies!!.comets.isEmpty())
            assertEquals(TestSkyMap.solarSystem(), state.bodies.solarSystem)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `night mode is kept`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitUntil { it.isLocationLoaded }
            viewModel.onNightModeChange(true)
            assertTrue(awaitUntil { it.isNightMode }.isNightMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun angleBetween(a: SkyVector, b: SkyVector): Double =
        Math.toDegrees(acos((a dot b).coerceIn(-1.0, 1.0)))

    private suspend fun ReceiveTurbine<SkyMapUiState>.awaitUntil(predicate: (SkyMapUiState) -> Boolean): SkyMapUiState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }

    private fun createViewModel(): SkyMapViewModel {
        val dispatcher = mainDispatcherRule.testDispatcher
        val metadata = FakeSyncMetadataRepository()
        return SkyMapViewModel(
            observeLocation = ObserveObserverLocationUseCase(location),
            observeNightMode = ObserveSkyMapNightModeUseCase(preferences),
            loadCatalog = LoadSkyCatalogUseCase(catalog, dispatcher),
            observeBodies = ObserveSkyMapBodiesUseCase(
                ephemeris,
                satellites,
                comets,
                cometCalculator,
                ObserveCometMagnitudeLimitUseCase(FakeCometDebugSettingsRepository()),
                dispatcher,
            ),
            findConstellation = FindConstellationUseCase(ephemeris),
            setNightMode = SetSkyMapNightModeUseCase(preferences),
            refreshElements = RefreshOrbitalElementsUseCase(
                satellites,
                RefreshSourceUseCase(metadata, clock),
                FakeHomeWidgetUpdater(),
            ),
            timeProvider = clock,
            defaultDispatcher = dispatcher,
        )
    }
}
