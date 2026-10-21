package com.radustavila.nasaexplorer.feature.sky

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.location.DeviceLocationResult
import com.radustavila.nasaexplorer.core.domain.usecase.LocateDeviceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIssPassRemindersEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIssPassesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIssTrackUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObservePeopleInSpaceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyTonightUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshOrbitalElementsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshPeopleInSpaceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RescheduleIssPassReminderUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SearchCitiesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetIssPassRemindersEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetManualLocationUseCase
import com.radustavila.nasaexplorer.core.model.City
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.LocationSource
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.fake.FakeCityDirectory
import com.radustavila.nasaexplorer.core.testing.fake.FakeDeviceLocationProvider
import com.radustavila.nasaexplorer.core.testing.fake.FakeHomeWidgetUpdater
import com.radustavila.nasaexplorer.core.testing.fake.FakeIssPassReminderScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakePeopleInSpaceRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatellitePassPredictor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatelliteRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyCalculator
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyPreferencesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SkyTonightViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-19T12:00:00Z"))
    private val network = FakeNetworkMonitor()
    private val metadata = FakeSyncMetadataRepository()
    private val location = FakeObserverLocationRepository()
    private val deviceLocation = FakeDeviceLocationProvider()
    private val cities = FakeCityDirectory(listOf(City("Bucharest", "Romania", TestSky.BUCHAREST)))
    private val calculator = FakeSkyCalculator()
    private val satellites = FakeSatelliteRepository().apply {
        elementsOnRefresh = TestSky.issElements(epoch = clock.currentInstant.minus(Duration.ofHours(8)))
    }
    private val predictor = FakeSatellitePassPredictor(
        passes = listOf(TestSky.pass()),
        position = TestSky.position(isLive = false),
    )
    private val people = FakePeopleInSpaceRepository().apply { refreshResult = DataResult.Success(1) }
    private val preferences = FakeSkyPreferencesRepository()
    private val scheduler = FakeIssPassReminderScheduler()

    @Test
    fun `without a saved location the screen asks for one, the crew still shows`() = runTest {
        people.setPeople(listOf(TestSky.person()))

        createViewModel().uiState.test {
            val state = awaitUntil { it.isLocationLoaded && it.people.isNotEmpty() }
            assertNull(state.location)
            assertNull(state.sky)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening the screen refreshes elements and crew once, then computes the sky for the place`() = runTest {
        location.setLocation(TestSky.location())

        createViewModel().uiState.test {
            val state = awaitUntil { it.sky != null && it.passes?.passes?.isNotEmpty() == true }
            assertEquals(calculator.sky, state.sky)
            assertEquals(listOf(TestSky.pass()), state.passes?.passes)
            assertEquals(TestSky.BUCHAREST, calculator.requests.last().first)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, satellites.refreshCallCount)
        assertEquals(1, people.refreshCallCount)
    }

    @Test
    fun `granting location saves the device fix`() = runTest {
        deviceLocation.permissionGranted = true
        deviceLocation.result = DeviceLocationResult.Located(GeoCoordinate(51.507, -0.128))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitUntil { it.isLocationLoaded }
            viewModel.onLocateDevice()

            val state = awaitUntil { it.location != null && !it.isLocating }
            assertEquals(GeoCoordinate(51.51, -0.13), state.location?.coordinate)
            assertEquals(LocationSource.DEVICE, state.location?.source)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a denied permission explains the city fallback, and a city can be picked`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitUntil { it.isLocationLoaded }
            viewModel.onLocationPermissionDenied()
            assertEquals(SkyMessage.LocationPermissionDenied, awaitUntil { it.message != null }.message)
            viewModel.onMessageShown()

            viewModel.onCityQueryChange("buch")
            val city = awaitUntil { it.citySearch.results.isNotEmpty() }.citySearch.results.single()
            viewModel.onCitySelected(city)

            val state = awaitUntil { it.location != null }
            assertEquals("Bucharest", state.location?.name)
            assertEquals(LocationSource.MANUAL, state.location?.source)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed pull-to-refresh is reported, the cached data stays`() = runTest {
        location.setLocation(TestSky.location())
        val viewModel = createViewModel()
        satellites.refreshResult = DataResult.Failure(AppError.Timeout)

        viewModel.uiState.test {
            awaitUntil { it.sky != null }
            viewModel.onRefresh()

            val state = awaitUntil { it.message is SkyMessage.RefreshFailed }
            assertTrue(state.sky != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `turning reminders on arms one before the next visible pass`() = runTest {
        location.setLocation(TestSky.location())
        satellites.setElements(TestSky.issElements(epoch = clock.currentInstant))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitUntil { it.isLocationLoaded }
            viewModel.onRemindersChange(true)

            assertTrue(awaitUntil { it.remindersEnabled }.remindersEnabled)
            assertEquals(TestSky.pass().start.minus(Duration.ofMinutes(10)), scheduler.scheduledAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<SkyTonightUiState>.awaitUntil(
        predicate: (SkyTonightUiState) -> Boolean,
    ): SkyTonightUiState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }

    private fun createViewModel(): SkyTonightViewModel {
        val dispatcher = mainDispatcherRule.testDispatcher
        val refreshSource = RefreshSourceUseCase(metadata, clock)
        val refreshElements = RefreshOrbitalElementsUseCase(satellites, refreshSource, FakeHomeWidgetUpdater())
        val reschedule = RescheduleIssPassReminderUseCase(preferences, location, satellites, predictor, scheduler, clock)
        return SkyTonightViewModel(
            observeLocation = ObserveObserverLocationUseCase(location),
            observeSkyTonight = ObserveSkyTonightUseCase(calculator, clock, dispatcher),
            observeIssPasses = ObserveIssPassesUseCase(satellites, predictor, clock, dispatcher),
            observeIssTrack = ObserveIssTrackUseCase(satellites, predictor, network, clock, dispatcher),
            observePeople = ObservePeopleInSpaceUseCase(people),
            observeReminders = ObserveIssPassRemindersEnabledUseCase(preferences),
            observeConnectivity = ObserveConnectivityUseCase(network),
            locateDevice = LocateDeviceUseCase(deviceLocation, location, FakeHomeWidgetUpdater()),
            setManualLocation = SetManualLocationUseCase(location, FakeHomeWidgetUpdater()),
            searchCities = SearchCitiesUseCase(cities),
            refreshElements = refreshElements,
            refreshPeople = RefreshPeopleInSpaceUseCase(people, metadata, clock, refreshSource),
            setReminders = SetIssPassRemindersEnabledUseCase(preferences, reschedule),
            rescheduleReminder = reschedule,
        )
    }
}
