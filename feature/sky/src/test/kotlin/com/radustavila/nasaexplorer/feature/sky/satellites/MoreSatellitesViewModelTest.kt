package com.radustavila.nasaexplorer.feature.sky.satellites

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEnabledSatelliteSetsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSatellitePassRemindersUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSatellitePassesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.PredictPassGroundTrackUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSatelliteSetsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RescheduleSatellitePassReminderUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetSatellitePassReminderEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetSatelliteSetEnabledUseCase
import com.radustavila.nasaexplorer.core.model.SatelliteSet
import com.radustavila.nasaexplorer.core.testing.data.TestSatellites
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatellitePassPredictor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatellitePassReminderScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatelliteRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyPreferencesRepository
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

class MoreSatellitesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-21T18:00:00Z"))
    private val network = FakeNetworkMonitor()
    private val metadata = FakeSyncMetadataRepository()
    private val location = FakeObserverLocationRepository(TestSky.location())
    private val preferences = FakeSkyPreferencesRepository()
    private val satellites = FakeSatelliteRepository().apply {
        setsOnRefresh = mapOf(SatelliteSet.HUBBLE to listOf(TestSatellites.hubbleElements()))
    }
    private val hubblePass = TestSky.pass(start = clock.currentInstant.plus(Duration.ofHours(2)))
    private val predictor = FakeSatellitePassPredictor(
        passesByNoradId = mapOf(20580 to listOf(hubblePass)),
        track = listOf(TestSky.position(), TestSky.position(longitude = 30.0)),
    )
    private val scheduler = FakeSatellitePassReminderScheduler()

    @Test
    fun `opening fetches the picked sets that are due, at most once a day`() = runTest {
        preferences.setSatelliteSetEnabled(SatelliteSet.TIANGONG, true)

        createViewModel()
        createViewModel()

        assertEquals(listOf(SatelliteSet.TIANGONG), satellites.refreshedSets)
    }

    @Test
    fun `picking a set fetches it and lists its passes`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onSetToggle(SatelliteSet.HUBBLE, true)

            val state = awaitUntil { it.forecast?.passes?.isNotEmpty() == true }
            assertEquals(setOf(SatelliteSet.HUBBLE), state.enabledSets)
            assertEquals(listOf(hubblePass), state.forecast?.passes?.map { it.pass })
            assertEquals(listOf(SatelliteSet.HUBBLE), satellites.refreshedSets)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping a pass opens its ground track, tapping it again closes it`() = runTest {
        preferences.setSatelliteSetEnabled(SatelliteSet.HUBBLE, true)
        satellites.setSet(SatelliteSet.HUBBLE, listOf(TestSatellites.hubbleElements()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val pass = awaitUntil { it.forecast?.passes?.isNotEmpty() == true }.forecast!!.passes.single()

            viewModel.onPassClick(pass)
            assertEquals(predictor.track, awaitUntil { it.selected != null }.selected?.track)

            viewModel.onPassClick(pass)
            assertNull(awaitUntil { it.selected == null }.selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a reminder switch arms the wake-up before the set's next pass`() = runTest {
        preferences.setSatelliteSetEnabled(SatelliteSet.HUBBLE, true)
        satellites.setSet(SatelliteSet.HUBBLE, listOf(TestSatellites.hubbleElements()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onReminderChange(SatelliteSet.HUBBLE, true)

            assertEquals(setOf(SatelliteSet.HUBBLE), awaitUntil { it.reminders.isNotEmpty() }.reminders)
            assertEquals(hubblePass.start.minus(Duration.ofMinutes(10)), scheduler.scheduledAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<MoreSatellitesUiState>.awaitUntil(
        predicate: (MoreSatellitesUiState) -> Boolean,
    ): MoreSatellitesUiState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }

    private fun createViewModel(): MoreSatellitesViewModel {
        val dispatcher = mainDispatcherRule.testDispatcher
        val refreshSets = RefreshSatelliteSetsUseCase(preferences, satellites, RefreshSourceUseCase(metadata, clock))
        val reschedule =
            RescheduleSatellitePassReminderUseCase(preferences, location, satellites, predictor, scheduler, clock)
        return MoreSatellitesViewModel(
            observeLocation = ObserveObserverLocationUseCase(location),
            observeEnabledSets = ObserveEnabledSatelliteSetsUseCase(preferences),
            observePasses = ObserveSatellitePassesUseCase(preferences, satellites, predictor, clock, dispatcher),
            observeReminders = ObserveSatellitePassRemindersUseCase(preferences),
            observeConnectivity = ObserveConnectivityUseCase(network),
            setSetEnabled = SetSatelliteSetEnabledUseCase(preferences, refreshSets, reschedule),
            setReminderEnabled = SetSatellitePassReminderEnabledUseCase(preferences, reschedule),
            refreshSets = refreshSets,
            rescheduleReminder = reschedule,
            predictGroundTrack = PredictPassGroundTrackUseCase(predictor, dispatcher),
        )
    }
}
