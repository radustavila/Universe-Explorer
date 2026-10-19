package com.radustavila.nasaexplorer.feature.earthevents

import androidx.lifecycle.SavedStateHandle
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.GetEventImageryLayerUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveOpenEarthEventsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshEarthEventsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.map.gibs.GibsImagery
import com.radustavila.nasaexplorer.core.map.gibs.GibsLayer
import com.radustavila.nasaexplorer.core.map.gibs.GibsTileMatrixSet
import com.radustavila.nasaexplorer.core.map.gibs.GibsTime
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EventImageryLayer
import com.radustavila.nasaexplorer.core.testing.data.TestEarthEvents
import com.radustavila.nasaexplorer.core.testing.fake.FakeEarthEventRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EarthEventsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 19)
    private val yesterday = today.minusDays(1)
    private val clock = FakeTimeProvider(currentInstant = TestEarthEvents.NOW, currentDate = today)
    private val repository = FakeEarthEventRepository()
    private val metadata = FakeSyncMetadataRepository()
    private val networkMonitor = FakeNetworkMonitor()

    private val storm = TestEarthEvents.storm()
    private val fire = TestEarthEvents.wildfire()
    private val volcano = TestEarthEvents.volcano()

    private fun TestScope.createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): EarthEventsViewModel {
        val viewModel = EarthEventsViewModel(
            savedStateHandle = savedStateHandle,
            observeOpenEvents = ObserveOpenEarthEventsUseCase(repository),
            refreshEarthEvents = RefreshEarthEventsUseCase(repository, RefreshSourceUseCase(metadata, clock)),
            getEventImageryLayer = GetEventImageryLayerUseCase(repository),
            timeProvider = clock,
            networkMonitor = networkMonitor,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun `shows the cached events over yesterday's true colour`() = runTest {
        repository.setEvents(listOf(fire, storm, volcano))

        val state = createViewModel().uiState.value

        assertTrue(state.isLoaded)
        assertEquals(listOf(storm, fire, volcano), state.events)
        assertEquals(
            listOf(EarthEventCategory.SEVERE_STORMS to 1, EarthEventCategory.VOLCANOES to 1, EarthEventCategory.WILDFIRES to 1),
            state.categoryCounts,
        )
        assertEquals(GibsImagery(GibsLayer.ViirsSnppTrueColor, yesterday), state.imagery)
    }

    @Test
    fun `opening the screen refreshes once, then not again while fresh`() = runTest {
        createViewModel()
        createViewModel()

        assertEquals(1, repository.refreshCallCount)
    }

    @Test
    fun `the refresh button forces a refresh`() = runTest {
        val viewModel = createViewModel()

        viewModel.onRefresh()

        assertEquals(2, repository.refreshCallCount)
    }

    @Test
    fun `a failed refresh the user asked for shows a message, one on open does not`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)
        val viewModel = createViewModel()
        assertNull(viewModel.uiState.value.message)

        viewModel.onRefresh()

        assertEquals(EarthEventsMessage.RefreshFailed, viewModel.uiState.value.message)
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `the filter hides and shows categories`() = runTest {
        repository.setEvents(listOf(fire, storm))
        val viewModel = createViewModel()

        viewModel.onCategoryToggled(EarthEventCategory.WILDFIRES)
        assertEquals(setOf(EarthEventCategory.WILDFIRES), viewModel.uiState.value.hiddenCategories)
        assertEquals(listOf(storm), viewModel.uiState.value.visibleEvents)

        viewModel.onCategoryToggled(EarthEventCategory.WILDFIRES)
        assertTrue(viewModel.uiState.value.hiddenCategories.isEmpty())
    }

    @Test
    fun `selecting an event opens its sheet, dismissing closes it`() = runTest {
        repository.setEvents(listOf(fire, storm))
        val viewModel = createViewModel()

        viewModel.onEventSelected(storm.id)
        assertEquals(storm, viewModel.uiState.value.selectedEvent)

        viewModel.onEventDismissed()
        assertNull(viewModel.uiState.value.selectedEvent)
    }

    @Test
    fun `a selected event that is no longer open is dropped`() = runTest {
        repository.setEvents(listOf(fire, storm))
        val viewModel = createViewModel()
        viewModel.onEventSelected(storm.id)

        repository.setEvents(listOf(fire))

        assertNull(viewModel.uiState.value.selectedEventId)
    }

    @Test
    fun `show imagery switches to the event's day and EONET's layer for its category`() = runTest {
        val layer = EventImageryLayer("MODIS_Terra_CorrectedReflectance_TrueColor", "GoogleMapsCompatible_Level9", "jpg")
        repository.imageryLayers = mapOf(EarthEventCategory.WILDFIRES to layer)
        repository.setEvents(listOf(fire))
        val viewModel = createViewModel()
        viewModel.onEventSelected(fire.id)

        viewModel.onShowImagery()

        val state = viewModel.uiState.value
        val fireDay = LocalDate.of(2026, 10, 14)
        assertEquals(EventImagery(fire.id, layer, fireDay), state.eventImagery)
        assertEquals(
            GibsImagery(
                GibsLayer(layer.id, GibsTileMatrixSet.GoogleMapsCompatibleLevel9, "jpg", GibsTime.Daily()),
                fireDay,
            ),
            state.imagery,
        )
        assertFalse(state.isLoadingImagery)

        viewModel.onHideImagery()
        assertEquals(GibsImagery(GibsLayer.ViirsSnppTrueColor, yesterday), viewModel.uiState.value.imagery)
    }

    @Test
    fun `an event reported today shows yesterday's imagery, today's mosaic being incomplete`() = runTest {
        val fresh = TestEarthEvents.wildfire(at = Instant.parse("2026-10-19T06:00:00Z"))
        repository.setEvents(listOf(fresh))
        val viewModel = createViewModel()
        viewModel.onEventSelected(fresh.id)

        viewModel.onShowImagery()

        assertEquals(yesterday, viewModel.uiState.value.eventImagery?.date)
    }

    @Test
    fun `without EONET's layers the event's day is shown in true colour, with a notice`() = runTest {
        repository.imageryFailure = DataResult.Failure(AppError.NoConnection)
        repository.setEvents(listOf(storm))
        val viewModel = createViewModel()
        viewModel.onEventSelected(storm.id)

        viewModel.onShowImagery()

        val state = viewModel.uiState.value
        val stormDay = LocalDate.of(2026, 10, 19).minusDays(1)
        assertEquals(GibsImagery(GibsLayer.ViirsSnppTrueColor, stormDay), state.imagery)
        assertEquals(EarthEventsMessage.ImageryFallback, state.message)
    }

    @Test
    fun `filter, selection and imagery survive process death`() = runTest {
        repository.imageryLayers = mapOf(EarthEventCategory.SEVERE_STORMS to TestEarthEvents.TRUE_COLOR_LAYER)
        repository.setEvents(listOf(fire, storm))
        val handle = SavedStateHandle()
        val first = createViewModel(handle)
        first.onCategoryToggled(EarthEventCategory.WILDFIRES)
        first.onEventSelected(storm.id)
        first.onShowImagery()

        val restored = createViewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })).uiState.value

        assertEquals(setOf(EarthEventCategory.WILDFIRES), restored.hiddenCategories)
        assertEquals(storm.id, restored.selectedEventId)
        assertEquals(first.uiState.value.eventImagery, restored.eventImagery)
    }

    @Test
    fun `reports connectivity for the offline banner`() = runTest {
        val viewModel = createViewModel()

        networkMonitor.setOnline(false)

        assertTrue(viewModel.uiState.value.isOffline)
    }
}
