package com.radustavila.nasaexplorer.feature.earth

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.usecase.FetchEpicDayUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEpicAvailableDatesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEpicDayUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEpicFavoriteIdsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEpicSyncStateUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveLatestEpicDayUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshEpicUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SaveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ToggleEpicFavoriteUseCase
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.SyncState
import com.radustavila.nasaexplorer.core.testing.data.TestEpicImages
import com.radustavila.nasaexplorer.core.testing.fake.FakeEpicRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeEpicSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EarthTodayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeEpicRepository()
    private val scheduler = FakeEpicSyncScheduler()
    private val networkMonitor = FakeNetworkMonitor()
    private val favorites = FakeFavoritesRepository()

    private val latestDay = TestEpicImages.day(size = 3)
    private val archiveDate = LocalDate.of(2026, 9, 28)

    @Test
    fun `asks for a sync on arrival and shows the newest cached day`() = runTest {
        repository.setImages(latestDay + TestEpicImages.day(date = TestEpicImages.LATEST_DATE.minusDays(1)))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitUntil { it.hasContent }
            assertEquals(latestDay, state.images)
            assertEquals(TestEpicImages.LATEST_DATE, state.displayedDate)
            assertNull(state.selectedDate)
        }
        assertEquals(1, scheduler.requestSyncCallCount)
    }

    @Test
    fun `an empty cache shows loading while the sync runs, offline when it cannot`() = runTest {
        scheduler.setState(SyncState.Syncing)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertTrue(awaitUntil { !it.hasContent }.isLoading)

            // Offline, the queued sync cannot run: no endless spinner, an explanation instead.
            scheduler.setState(SyncState.Enqueued)
            networkMonitor.setOnline(false)
            val offline = awaitUntil { it.isOffline }
            assertTrue(offline.isOfflineEmpty)
        }
    }

    @Test
    fun `the date picker only knows the days EPIC published`() = runTest {
        val dates = listOf(TestEpicImages.LATEST_DATE, archiveDate)
        repository.setAvailableDates(EpicCollection.NATURAL, dates)

        createViewModel().uiState.test {
            assertEquals(dates.toSet(), awaitUntil { it.canPickDate }.availableDates)
        }
    }

    @Test
    fun `picking a day outside the cache downloads it and shows it`() = runTest {
        val archiveDay = TestEpicImages.day(date = archiveDate, size = 2)
        repository.setImages(latestDay)
        repository.fetchDayResult = DataResult.Success(2)
        repository.fetchDayImages = archiveDay
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitUntil { it.hasContent }
            viewModel.onDateSelected(archiveDate)

            val state = awaitUntil { it.images == archiveDay }
            assertEquals(archiveDate, state.selectedDate)
            assertEquals(archiveDate, state.displayedDate)
        }
        assertEquals(listOf(EpicCollection.NATURAL to archiveDate), repository.fetchedDays)
    }

    @Test
    fun `picking a cached day does not download it again`() = runTest {
        val cachedDate = TestEpicImages.LATEST_DATE.minusDays(2)
        repository.setImages(latestDay + TestEpicImages.day(date = cachedDate))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitUntil { it.hasContent }
            viewModel.onDateSelected(cachedDate)
            awaitUntil { it.displayedDate == cachedDate }
        }
        assertTrue(repository.fetchedDays.isEmpty())
    }

    @Test
    fun `a failed download is reported and offers a retry`() = runTest {
        repository.fetchDayResult = DataResult.Failure(AppError.Timeout)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onDateSelected(archiveDate)

            val state = awaitUntil { it.error != null }
            assertEquals(AppError.Timeout.toUiError(), state.error)
            assertTrue(state.isEmpty)

            repository.fetchDayResult = DataResult.Success(1)
            repository.fetchDayImages = TestEpicImages.day(date = archiveDate, size = 1)
            viewModel.onRetry()
            awaitUntil { it.hasContent && it.error == null }
        }
        assertEquals(2, repository.fetchedDays.size)
    }

    @Test
    fun `switching collection shows the other set and keeps a day it also has`() = runTest {
        val enhancedDay = TestEpicImages.day(date = archiveDate, collection = EpicCollection.ENHANCED)
        repository.setImages(latestDay + TestEpicImages.day(date = archiveDate) + enhancedDay)
        repository.setAvailableDates(EpicCollection.ENHANCED, listOf(archiveDate))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onDateSelected(archiveDate)
            awaitUntil { it.displayedDate == archiveDate }

            viewModel.onCollectionSelected(EpicCollection.ENHANCED)

            val state = awaitUntil { it.collection == EpicCollection.ENHANCED && it.hasContent }
            assertEquals(enhancedDay, state.images)
            assertEquals(archiveDate, state.selectedDate)
        }
    }

    @Test
    fun `switching to a collection without the picked day returns to its newest day`() = runTest {
        val enhancedLatest = TestEpicImages.day(date = TestEpicImages.LATEST_DATE.minusDays(2), collection = EpicCollection.ENHANCED)
        repository.setImages(latestDay + TestEpicImages.day(date = archiveDate) + enhancedLatest)
        repository.setAvailableDates(EpicCollection.ENHANCED, listOf(enhancedLatest.first().date))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onDateSelected(archiveDate)
            awaitUntil { it.displayedDate == archiveDate }

            viewModel.onCollectionSelected(EpicCollection.ENHANCED)

            val state = awaitUntil { it.collection == EpicCollection.ENHANCED && it.hasContent }
            assertNull(state.selectedDate)
            assertEquals(enhancedLatest, state.images)
        }
    }

    @Test
    fun `the selection survives process death`() = runTest {
        val enhancedLatest = TestEpicImages.day(collection = EpicCollection.ENHANCED)
        repository.setImages(latestDay + enhancedLatest)
        val savedState = SavedStateHandle()
        createViewModel(savedState).onCollectionSelected(EpicCollection.ENHANCED)

        createViewModel(savedState).uiState.test {
            assertEquals(enhancedLatest, awaitUntil { it.hasContent }.images)
        }
    }

    @Test
    fun `a failed sync is surfaced while showing the newest day`() = runTest {
        scheduler.setState(SyncState.Failed(AppError.NoConnection))

        createViewModel().uiState.test {
            assertEquals(AppError.NoConnection.toUiError(), awaitUntil { it.error != null }.error)
        }
    }

    @Test
    fun `a photo is saved as a favourite and its heart fills`() = runTest {
        repository.setImages(latestDay)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitUntil { it.hasContent }
            viewModel.onToggleFavorite(latestDay[1])

            val state = awaitUntil { it.favoriteIds.isNotEmpty() }
            assertTrue(state.isFavorite(latestDay[1]))
            assertFalse(state.isFavorite(latestDay[0]))

            viewModel.onToggleFavorite(latestDay[1])
            assertTrue(awaitUntil { it.favoriteIds.isEmpty() }.images.isNotEmpty())
        }
        assertTrue(favorites.current.isEmpty())
    }

    @Test
    fun `opened for one photo it shows that set and day, starting on that photo`() = runTest {
        val enhancedDay = TestEpicImages.day(date = archiveDate, collection = EpicCollection.ENHANCED)
        repository.setImages(latestDay + enhancedDay)
        val route = SavedStateHandle(
            mapOf("collection" to "enhanced", "date" to archiveDate.toString(), "imageId" to enhancedDay[2].identifier),
        )

        val viewModel = createViewModel(route)

        viewModel.uiState.test {
            val state = awaitUntil { it.hasContent }
            assertEquals(enhancedDay, state.images)
            assertEquals(enhancedDay[2].identifier, state.focusedImageId)

            // Moving on to another day is a new rotation, no longer about that photo.
            viewModel.onDateSelected(null)
            assertNull(awaitUntil { it.selectedDate == null }.focusedImageId)
        }
        assertTrue(repository.fetchedDays.isEmpty())
    }

    @Test
    fun `a favourite whose day has left the cache is downloaded again`() = runTest {
        val oldDay = TestEpicImages.day(date = LocalDate.of(2025, 1, 2), size = 2)
        repository.fetchDayResult = DataResult.Success(2)
        repository.fetchDayImages = oldDay
        val route = SavedStateHandle(mapOf("collection" to "natural", "date" to "2025-01-02", "imageId" to oldDay[1].identifier))

        createViewModel(route).uiState.test {
            assertEquals(oldDay, awaitUntil { it.hasContent }.images)
        }
        assertEquals(listOf(EpicCollection.NATURAL to LocalDate.of(2025, 1, 2)), repository.fetchedDays)
    }

    private suspend fun ReceiveTurbine<EarthTodayUiState>.awaitUntil(
        predicate: (EarthTodayUiState) -> Boolean,
    ): EarthTodayUiState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = EarthTodayViewModel(
        savedStateHandle = savedStateHandle,
        observeLatestEpicDay = ObserveLatestEpicDayUseCase(repository),
        observeEpicDay = ObserveEpicDayUseCase(repository),
        observeEpicAvailableDates = ObserveEpicAvailableDatesUseCase(repository),
        observeConnectivity = ObserveConnectivityUseCase(networkMonitor),
        observeEpicSyncState = ObserveEpicSyncStateUseCase(scheduler),
        observeEpicFavoriteIds = ObserveEpicFavoriteIdsUseCase(favorites),
        refreshEpic = RefreshEpicUseCase(scheduler),
        fetchEpicDay = FetchEpicDayUseCase(repository),
        toggleEpicFavorite = ToggleEpicFavoriteUseCase(
            saveFavorite = SaveFavoriteUseCase(favorites, FakeTimeProvider()),
            removeFavorite = RemoveFavoriteUseCase(favorites),
        ),
    )
}
