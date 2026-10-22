package com.radustavila.nasaexplorer.feature.spaceweather

import androidx.lifecycle.SavedStateHandle
import com.radustavila.nasaexplorer.core.domain.usecase.CanAutoPlaySunVideoUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.CheckAuroraAlertUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.GetSunImageAtUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveAuroraAlertSettingsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveAuroraForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCurrentKpUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveKpForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSolarActivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSpaceWeatherSyncStatusUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSunUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSunImageUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestSpaceWeatherSyncUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SelectSunChannelUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetAuroraAlertEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetAuroraAlertLocationUseCase
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import com.radustavila.nasaexplorer.core.model.SunChannel
import com.radustavila.nasaexplorer.core.testing.data.TestSpaceWeather
import com.radustavila.nasaexplorer.core.testing.fake.FakeAuroraAlertNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeAuroraAlertRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSpaceWeatherRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSpaceWeatherSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeSunImageRepository
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
    private val sun = FakeSunImageRepository()
    private val metadata = FakeSyncMetadataRepository()

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
            observeSun = ObserveSunUseCase(sun),
            selectSunChannel = SelectSunChannelUseCase(sun),
            refreshSunImage = RefreshSunImageUseCase(sun, RefreshSourceUseCase(metadata, clock), clock),
            canAutoPlaySunVideo = CanAutoPlaySunVideoUseCase(network),
            getSunImageAt = GetSunImageAtUseCase(sun),
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

    @Test
    fun `the chosen channel's picture is fetched on open and after each switch`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.onSunChannelSelected(SunChannel.FLARES)

        assertEquals(listOf(SunChannel.VISIBLE_LIGHT, SunChannel.FLARES), sun.refreshes)
        assertEquals(SunChannel.FLARES, viewModel.uiState.value.sun.channel)
        assertEquals(sun.videoUrl(SunChannel.FLARES), viewModel.uiState.value.sun.videoUrl)
    }

    @Test
    fun `offline, the cached picture shows with its age and a failed refresh is reported`() = runTest {
        network.setOnline(false)
        repository.kpForecast.value = TestSpaceWeather.forecast(2.0, 3.0, 3.33)
        val picture = FakeSunImageRepository.image(SunChannel.VISIBLE_LIGHT, TestSpaceWeather.NOW.minusSeconds(3_600))
        sun.images.value = mapOf(SunChannel.VISIBLE_LIGHT to picture)
        sun.refreshResult = DataResult.Failure(AppError.NoConnection)
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value.sun
        assertEquals(picture, state.image)
        assertTrue(state.lastRefreshFailed)
        assertEquals(picture.updatedAt, state.freshness(isOffline = true).lastUpdatedAt)
    }

    @Test
    fun `pull to refresh asks SDO again even for a fresh picture`() = runTest {
        sun.images.value = mapOf(
            SunChannel.VISIBLE_LIGHT to FakeSunImageRepository.image(SunChannel.VISIBLE_LIGHT, TestSpaceWeather.NOW),
        )
        val viewModel = createViewModel()
        assertTrue(sun.refreshes.isEmpty())

        viewModel.onRefresh()

        assertEquals(listOf(SunChannel.VISIBLE_LIGHT), sun.refreshes)
    }

    @Test
    fun `on mobile data the video waits for a tap, on Wi-Fi it plays`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        network.unmetered = false
        viewModel.onSunVideoSelected()
        assertEquals(SunVideo.AskFirst, viewModel.uiState.value.sun.video)
        viewModel.onSunVideoConfirmed()
        assertEquals(SunVideo.Playing, viewModel.uiState.value.sun.video)

        // Another channel is another download: asked again.
        viewModel.onSunChannelSelected(SunChannel.CORONA)
        assertEquals(SunVideo.AskFirst, viewModel.uiState.value.sun.video)

        viewModel.onSunVideoClosed()
        assertEquals(SunVideo.Closed, viewModel.uiState.value.sun.video)

        network.unmetered = true
        viewModel.onSunVideoSelected()
        assertEquals(SunVideo.Playing, viewModel.uiState.value.sun.video)
    }

    @Test
    fun `the latest flare links to Helioviewer's picture at its peak`() = runTest {
        val flare = TestSpaceWeather.flare()
        repository.latestFlare.value = flare
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(sun.imageUrlAt(flare.peakTime ?: flare.beginTime), viewModel.uiState.value.flareImageUrl)
    }
}
