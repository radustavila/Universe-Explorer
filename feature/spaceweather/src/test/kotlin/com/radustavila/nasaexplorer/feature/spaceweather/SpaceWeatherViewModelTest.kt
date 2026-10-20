package com.radustavila.nasaexplorer.feature.spaceweather

import androidx.lifecycle.SavedStateHandle
import com.radustavila.nasaexplorer.core.domain.usecase.CheckAuroraAlertUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveAuroraAlertSettingsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveAuroraForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCurrentKpUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveKpForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSolarActivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSpaceWeatherSyncStatusUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestSpaceWeatherSyncUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetAuroraAlertEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetAuroraAlertLocationUseCase
import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import com.radustavila.nasaexplorer.core.testing.data.TestSpaceWeather
import com.radustavila.nasaexplorer.core.testing.fake.FakeAuroraAlertNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeAuroraAlertRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSpaceWeatherRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSpaceWeatherSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SpaceWeatherViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSpaceWeatherRepository()
    private val scheduler = FakeSpaceWeatherSyncScheduler()
    private val alerts = FakeAuroraAlertRepository()
    private val notifier = FakeAuroraAlertNotifier()
    private val network = FakeNetworkMonitor()
    private val clock = FakeTimeProvider(currentInstant = TestSpaceWeather.NOW)
    private val savedState = SavedStateHandle()

    private fun createViewModel(): SpaceWeatherViewModel {
        val check = CheckAuroraAlertUseCase(alerts, repository, notifier, clock)
        return SpaceWeatherViewModel(
            savedStateHandle = savedState,
            observeKpForecast = ObserveKpForecastUseCase(repository),
            observeCurrentKp = ObserveCurrentKpUseCase(repository),
            observeAuroraForecast = ObserveAuroraForecastUseCase(repository),
            observeSolarActivity = ObserveSolarActivityUseCase(repository),
            observeAlertSettings = ObserveAuroraAlertSettingsUseCase(alerts),
            observeSyncStatus = ObserveSpaceWeatherSyncStatusUseCase(scheduler, FakeSyncMetadataRepository()),
            observeConnectivity = ObserveConnectivityUseCase(network),
            requestSync = RequestSpaceWeatherSyncUseCase(scheduler),
            setAlertEnabled = SetAuroraAlertEnabledUseCase(alerts, scheduler, check),
            setAlertLocation = SetAuroraAlertLocationUseCase(alerts, check),
            timeProvider = clock,
            defaultDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun `opening the screen asks for one refresh with the aurora map and registers no background job`() = runTest {
        createViewModel()

        assertEquals(listOf(false to true), scheduler.requests)
        assertEquals(0, scheduler.periodicSyncRegistrations)
        assertFalse(scheduler.isPeriodicSyncScheduled)
    }

    @Test
    fun `cached Kp, forecast, aurora and the Sun render without the network`() = runTest {
        network.setOnline(false)
        repository.kpForecast.value = TestSpaceWeather.forecast(2.0, 3.0, 3.33, 4.67, 5.33)
        repository.currentKp.value = TestSpaceWeather.currentKp(3.0)
        repository.auroraForecast.value = TestSpaceWeather.auroraForecast()
        repository.latestFlare.value = TestSpaceWeather.flare()
        repository.latestCme.value = TestSpaceWeather.cme()
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertTrue(state.hasData)
        assertTrue(state.isOffline)
        assertEquals(3.0, state.currentKp!!, 0.0)
        // The two observed slots before the current one are history, not forecast.
        assertEquals(listOf(3.33, 4.67, 5.33), state.forecast.map { it.kp })
        assertEquals(3, state.aurora!!.overlays.size)
        assertEquals("M1.6", state.flare?.classType)
        assertNotNull(state.cme)
    }

    @Test
    fun `without the 1-minute estimate the gauge shows the current slot`() = runTest {
        repository.kpForecast.value = TestSpaceWeather.forecast(2.0, 3.0, 3.33, 4.67)
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(3.33, viewModel.uiState.value.currentKp!!, 0.0)
    }

    @Test
    fun `pull to refresh forces a refresh`() = runTest {
        val viewModel = createViewModel()

        viewModel.onRefresh()

        assertEquals(true to true, scheduler.requests.last())
    }

    @Test
    fun `the hemisphere survives in saved state`() = runTest {
        createViewModel().onHemisphereSelected(Hemisphere.South)

        val restored = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.uiState.collect {} }
        assertEquals(Hemisphere.South, restored.uiState.value.hemisphere)
    }

    @Test
    fun `turning alerts on without a place saves the place first, then alerts at once`() = runTest {
        repository.kpForecast.value = TestSpaceWeather.forecast(2.0, 3.0, 5.67)
        val viewModel = createViewModel()

        viewModel.onAlertLocationChange(latitude = 59.3, longitude = 18.1, enableAlerts = true)

        assertEquals(AuroraAlertSettings(enabled = true, latitude = 59.3, longitude = 18.1), alerts.settings.first())
        assertEquals(1, notifier.notified.size)
        assertTrue(scheduler.isPeriodicSyncScheduled)
    }

    @Test
    fun `turning alerts off stops them and the background job`() = runTest {
        alerts.setEnabled(true)
        scheduler.ensurePeriodicSync()
        val viewModel = createViewModel()

        viewModel.onAlertsEnabledChange(false)

        assertFalse(alerts.settings.first().enabled)
        assertFalse(scheduler.isPeriodicSyncScheduled)
    }

    @Test
    fun `coming back online asks for a refresh again`() = runTest {
        network.setOnline(false)
        createViewModel()

        network.setOnline(true)

        assertEquals(2, scheduler.requests.size)
    }
}
