package com.radustavila.nasaexplorer.feature.sky.location

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.location.DeviceLocationResult
import com.radustavila.nasaexplorer.core.domain.usecase.ClearObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.LocateDeviceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RescheduleIssPassReminderUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SearchCitiesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetManualLocationUseCase
import com.radustavila.nasaexplorer.core.model.City
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.LocationSource
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.fake.FakeCityDirectory
import com.radustavila.nasaexplorer.core.testing.fake.FakeDeviceLocationProvider
import com.radustavila.nasaexplorer.core.testing.fake.FakeIssPassReminderScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatellitePassPredictor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatelliteRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyPreferencesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ObserverLocationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-19T12:00:00Z"))
    private val location = FakeObserverLocationRepository()
    private val device = FakeDeviceLocationProvider()
    private val cities = FakeCityDirectory(listOf(City("Bucharest", "Romania", TestSky.BUCHAREST)))
    private val preferences = FakeSkyPreferencesRepository()
    private val satellites = FakeSatelliteRepository().apply { setElements(TestSky.issElements(epoch = clock.currentInstant)) }
    private val pass = TestSky.pass(start = clock.currentInstant.plus(Duration.ofHours(14)))
    private val scheduler = FakeIssPassReminderScheduler()

    private fun createViewModel(): ObserverLocationViewModel {
        val reschedule = RescheduleIssPassReminderUseCase(
            preferences = preferences,
            locationRepository = location,
            satelliteRepository = satellites,
            predictor = FakeSatellitePassPredictor(passes = listOf(pass)),
            scheduler = scheduler,
            timeProvider = clock,
        )
        return ObserverLocationViewModel(
            observeLocation = ObserveObserverLocationUseCase(location),
            locateDevice = LocateDeviceUseCase(device, location),
            setManualLocation = SetManualLocationUseCase(location),
            clearLocation = ClearObserverLocationUseCase(location, reschedule),
            searchCities = SearchCitiesUseCase(cities),
            rescheduleReminder = reschedule,
        )
    }

    @Test
    fun `shows the saved place once read, and searches cities as the user types`() = runTest {
        location.setLocation(TestSky.location())
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.isLoaded) state = awaitItem()
            assertEquals(TestSky.location(), state.location)

            viewModel.onCityQueryChange("buch")
            while (state.citySearch.results.isEmpty()) state = awaitItem()
            assertEquals(listOf("Bucharest"), state.citySearch.results.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `picking a city saves it and re-arms the ISS reminder for the new place`() = runTest {
        preferences.setIssPassRemindersEnabled(true)
        val viewModel = createViewModel()

        viewModel.onCitySelected(City("Bucharest", "Romania", TestSky.BUCHAREST))

        assertEquals("Bucharest", location.current?.name)
        assertEquals(LocationSource.MANUAL, location.current?.source)
        assertEquals(pass.start.minus(RescheduleIssPassReminderUseCase.REMINDER_LEAD), scheduler.scheduledAt)
    }

    @Test
    fun `coordinates can be entered instead of a city`() = runTest {
        createViewModel().onCoordinatesEntered(GeoCoordinate(51.5, -0.12))

        assertEquals(GeoCoordinate(51.5, -0.12), location.current?.coordinate)
        assertNull(location.current?.name)
    }

    @Test
    fun `forgetting the place clears it and leaves the reminder waiting for a new one`() = runTest {
        preferences.setIssPassRemindersEnabled(true)
        location.setLocation(TestSky.location())
        val viewModel = createViewModel()

        viewModel.onClear()

        assertNull(location.current)
        assertEquals(clock.currentInstant.plus(RescheduleIssPassReminderUseCase.RECHECK_INTERVAL), scheduler.scheduledAt)
    }

    @Test
    fun `a refused or missing device fix is explained, a found one is saved`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onLocationPermissionDenied()
            var state = awaitItem()
            while (state.message == null) state = awaitItem()
            assertEquals(ObserverLocationMessage.PERMISSION_DENIED, state.message)

            device.permissionGranted = true
            device.result = DeviceLocationResult.Unavailable
            viewModel.onLocateDevice()
            while (state.message != ObserverLocationMessage.UNAVAILABLE || state.isLocating) state = awaitItem()

            device.result = DeviceLocationResult.Located(TestSky.BUCHAREST)
            viewModel.onLocateDevice()
            while (state.message != null || state.isLocating || state.location == null) state = awaitItem()
            assertEquals(LocationSource.DEVICE, state.location?.source)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
