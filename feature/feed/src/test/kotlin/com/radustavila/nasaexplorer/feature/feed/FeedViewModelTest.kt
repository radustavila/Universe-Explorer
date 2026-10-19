package com.radustavila.nasaexplorer.feature.feed

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.R
import com.radustavila.nasaexplorer.core.domain.usecase.LoadOlderApodsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveApodFeedUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDailyWallpaperEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveNewApodNotificationsEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveOnThisDayUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSyncStateUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshFeedUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SearchCachedApodsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetDailyWallpaperEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetNewApodNotificationsEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ToggleFavoriteUseCase
import com.radustavila.nasaexplorer.core.model.SyncState
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.fake.FakeUserPreferencesRepository
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FeedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeApodRepository()
    private val networkMonitor = FakeNetworkMonitor()
    private val syncScheduler = FakeSyncScheduler()
    private val timeProvider = FakeTimeProvider()
    private val syncMetadata = FakeSyncMetadataRepository()
    private val userPreferences = FakeUserPreferencesRepository()

    @Test
    fun `asks for a refresh as soon as it is created`() = runTest {
        createViewModel()

        assertEquals(1, syncScheduler.requestSyncCallCount)
    }

    @Test
    fun `shows cached entries immediately, before any sync completes`() = runTest {
        repository.setEntries(TestApods.feed(size = 3))

        createViewModel().uiState.test {
            val state = awaitItem().awaitContent(this)

            assertEquals(3, state.items.size)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `an in-flight sync with no cache is initial loading, with a cache it is refreshing`() = runTest {
        syncScheduler.setState(SyncState.Syncing)

        createViewModel().uiState.test {
            var state = awaitItem().awaitLoading(this)
            assertTrue(state.isLoading)
            assertFalse(state.isRefreshing)

            repository.setEntries(TestApods.feed(size = 2))

            state = awaitItem()
            while (state.items.isEmpty()) state = awaitItem()
            assertFalse(state.isLoading)
            assertTrue(state.isRefreshing)
        }
    }

    @Test
    fun `losing connectivity marks the feed as offline without clearing the cache`() = runTest {
        repository.setEntries(TestApods.feed(size = 2))

        createViewModel().uiState.test {
            var state = awaitItem().awaitContent(this)
            assertFalse(state.isOffline)

            networkMonitor.setOnline(false)

            state = awaitItem()
            while (!state.isOffline) state = awaitItem()
            assertEquals(2, state.items.size)
            assertTrue(state.isOffline)
        }
    }

    @Test
    fun `an empty cache while offline is an offline-empty state`() = runTest {
        networkMonitor.setOnline(false)

        createViewModel().uiState.test {
            var state = awaitItem()
            while (!state.isOfflineEmpty) state = awaitItem()

            assertTrue(state.isEmpty)
            assertTrue(state.isOfflineEmpty)
        }
    }

    @Test
    fun `a sync blocked by the connectivity constraint is not shown as progress`() = runTest {
        // WorkManager keeps the request enqueued until the network returns; spinning forever would
        // be a lie.
        networkMonitor.setOnline(false)
        syncScheduler.setState(SyncState.Enqueued)
        repository.setEntries(TestApods.feed(size = 2))

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.items.isEmpty() || !state.isOffline) state = awaitItem()

            assertFalse(state.isRefreshing)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `a failed sync surfaces a presentable error`() = runTest {
        syncScheduler.setState(SyncState.Failed(AppError.RateLimited()))

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.error == null) state = awaitItem()

            assertEquals(R.string.error_rate_limited, state.error?.messageRes)
        }
    }

    @Test
    fun `retry clears the error and requests another sync`() = runTest {
        val viewModel = createViewModel()

        viewModel.onRetry()

        assertEquals(2, syncScheduler.requestSyncCallCount)
    }

    @Test
    fun `loading more pages the archive and stops at its start`() = runTest {
        repository.setEntries(TestApods.feed(size = 7))
        repository.loadOlderResult = DataResult.Success(7)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem().awaitContent(this)

            viewModel.onLoadMore()
            var state = awaitItem()
            while (state.isLoadingMore) state = awaitItem()
            assertEquals(1, repository.loadOlderCallCount)
            assertFalse(state.hasReachedArchiveStart)

            repository.loadOlderResult = DataResult.Success(0)
            viewModel.onLoadMore()
            state = awaitItem()
            while (!state.hasReachedArchiveStart) state = awaitItem()
            assertTrue(state.hasReachedArchiveStart)

            // Paging is not attempted again once the archive start is known.
            viewModel.onLoadMore()
            assertEquals(2, repository.loadOlderCallCount)
        }
    }

    @Test
    fun `a failed page load is reported and does not block later attempts`() = runTest {
        repository.setEntries(TestApods.feed(size = 7))
        repository.loadOlderResult = DataResult.Failure(AppError.Timeout)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem().awaitContent(this)

            viewModel.onLoadMore()
            var state = awaitItem()
            while (state.error == null) state = awaitItem()
            assertEquals(R.string.error_timeout, state.error?.messageRes)
            assertFalse(state.isLoadingMore)

            viewModel.onErrorDismissed()
            state = awaitItem()
            while (state.error != null) state = awaitItem()
            assertNull(state.error)
        }
    }

    @Test
    fun `toggling a favorite updates the entry through the database flow`() = runTest {
        val apod = TestApods.image()
        repository.setEntries(listOf(apod))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem().awaitContent(this)
            assertFalse(state.items.single().isFavorite)

            viewModel.onToggleFavorite(apod)

            var updated = awaitItem()
            while (!updated.items.single().isFavorite) updated = awaitItem()
            assertTrue(updated.items.single().isFavorite)
        }
    }

    @Test
    fun `a rejected favorite toggle is surfaced as an error`() = runTest {
        val apod = TestApods.image()
        repository.setEntries(listOf(apod))
        repository.setFavoriteResult = DataResult.Failure(AppError.Database("disk full"))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem().awaitContent(this)

            viewModel.onToggleFavorite(apod)

            var state = awaitItem()
            while (state.error == null) state = awaitItem()
            assertEquals(R.string.error_database, state.error?.messageRes)
        }
    }

    @Test
    fun `turning new-entry notifications on is persisted and reflected in the state`() = runTest {
        repository.setEntries(TestApods.feed(size = 1))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertFalse(awaitItem().awaitContent(this).areNewApodNotificationsEnabled)

            viewModel.onNewApodNotificationsChanged(enabled = true)

            var state = awaitItem()
            while (!state.areNewApodNotificationsEnabled) state = awaitItem()
            assertTrue(userPreferences.currentNewApodNotificationsEnabled)
        }
    }

    @Test
    fun `search is closed until opened and then starts blank`() = runTest {
        repository.setEntries(TestApods.feed(size = 3))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertNull(awaitItem().awaitContent(this).search)

            viewModel.onSearchQueryChanged("")

            val search = awaitSearch { it != null }
            assertTrue(requireNotNull(search).isBlank)
            assertTrue(search.results.isEmpty())
        }
    }

    @Test
    fun `search shows matching cached entries and follows the query`() = runTest {
        repository.setEntries(
            listOf(
                TestApods.image(date = LocalDate.of(2026, 9, 10), title = "Andromeda Rising"),
                TestApods.image(date = LocalDate.of(2026, 9, 9), title = "Saturn's Rings"),
                TestApods.image(date = LocalDate.of(2026, 9, 8), title = "Saturn at Night"),
            ),
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onSearchQueryChanged("saturn")
            var search = requireNotNull(awaitSearch { it?.query == "saturn" })
            assertEquals(listOf("Saturn's Rings", "Saturn at Night"), search.results.map { it.title })

            viewModel.onSearchQueryChanged("saturn night")
            search = requireNotNull(awaitSearch { it?.query == "saturn night" })
            assertEquals(listOf("Saturn at Night"), search.results.map { it.title })

            viewModel.onSearchQueryChanged("jupiter")
            search = requireNotNull(awaitSearch { it?.query == "jupiter" })
            assertTrue(search.hasNoResults)
        }
    }

    @Test
    fun `closing search returns to the feed`() = runTest {
        repository.setEntries(TestApods.feed(size = 2))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onSearchQueryChanged("apod")
            awaitSearch { it != null }

            viewModel.onSearchQueryChanged(null)

            var state = awaitItem()
            while (state.search != null) state = awaitItem()
            assertEquals(2, state.items.size)
        }
    }

    @Test
    fun `On this day lists today's month and day from earlier years`() = runTest {
        timeProvider.currentDate = LocalDate.of(2026, 10, 19)
        repository.setEntries(
            listOf(
                TestApods.image(date = LocalDate.of(2026, 10, 19)),
                TestApods.image(date = LocalDate.of(2020, 10, 19), title = "Five years ago"),
                TestApods.image(date = LocalDate.of(1999, 10, 19), title = "Last century"),
                TestApods.image(date = LocalDate.of(2020, 10, 18)),
            ),
        )

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.onThisDay.isEmpty()) state = awaitItem()

            assertEquals(listOf("Five years ago", "Last century"), state.onThisDay.map { it.title })
        }
    }

    @Test
    fun `turning the daily wallpaper on is persisted and handed to the sync pipeline`() = runTest {
        val viewModel = createViewModel()
        val syncsBefore = syncScheduler.requestSyncCallCount

        viewModel.uiState.test {
            viewModel.onDailyWallpaperChanged(true)

            var state = awaitItem()
            while (!state.isDailyWallpaperEnabled) state = awaitItem()
            assertTrue(userPreferences.currentDailyWallpaperEnabled)
            assertEquals(syncsBefore + 1, syncScheduler.requestSyncCallCount)
        }
    }

    private suspend fun app.cash.turbine.TurbineTestContext<FeedUiState>.awaitSearch(
        predicate: (FeedSearchState?) -> Boolean,
    ): FeedSearchState? {
        var state = awaitItem()
        while (!predicate(state.search)) state = awaitItem()
        return state.search
    }

    private fun createViewModel() = FeedViewModel(
        observeApodFeed = ObserveApodFeedUseCase(repository),
        observeConnectivity = ObserveConnectivityUseCase(networkMonitor),
        observeSyncState = ObserveSyncStateUseCase(syncScheduler),
        observeNewApodNotificationsEnabled = ObserveNewApodNotificationsEnabledUseCase(userPreferences),
        observeDailyWallpaperEnabled = ObserveDailyWallpaperEnabledUseCase(userPreferences),
        observeOnThisDay = ObserveOnThisDayUseCase(repository, timeProvider),
        searchCachedApods = SearchCachedApodsUseCase(repository),
        refreshFeed = RefreshFeedUseCase(syncScheduler),
        loadOlderApods = LoadOlderApodsUseCase(repository, syncMetadata, networkMonitor),
        toggleFavorite = ToggleFavoriteUseCase(repository.favorites, FakeTimeProvider()),
        setNewApodNotificationsEnabled = SetNewApodNotificationsEnabledUseCase(userPreferences),
        setDailyWallpaperEnabled = SetDailyWallpaperEnabledUseCase(userPreferences, syncMetadata, syncScheduler),
        timeProvider = timeProvider,
    )

    private suspend fun FeedUiState.awaitContent(
        turbine: app.cash.turbine.TurbineTestContext<FeedUiState>,
    ): FeedUiState {
        var state = this
        while (state.items.isEmpty() && !state.isEmpty) state = turbine.awaitItem()
        return state
    }

    private suspend fun FeedUiState.awaitLoading(
        turbine: app.cash.turbine.TurbineTestContext<FeedUiState>,
    ): FeedUiState {
        var state = this
        while (!state.isLoading) state = turbine.awaitItem()
        return state
    }
}
