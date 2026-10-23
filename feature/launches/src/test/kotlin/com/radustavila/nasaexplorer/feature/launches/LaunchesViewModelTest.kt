package com.radustavila.nasaexplorer.feature.launches

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveLaunchRemindersUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObservePastLaunchesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveUpcomingLaunchesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshPastLaunchesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RescheduleLaunchRemindersUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetLaunchReminderUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeUpcomingLaunchesUseCase
import com.radustavila.nasaexplorer.core.testing.data.TestLaunches
import com.radustavila.nasaexplorer.core.testing.fake.FakeHomeWidgetUpdater
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchReminderRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchReminderScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import com.radustavila.nasaexplorer.feature.launches.list.LaunchesTab
import com.radustavila.nasaexplorer.feature.launches.list.LaunchesUiState
import com.radustavila.nasaexplorer.feature.launches.list.LaunchesViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class LaunchesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLaunchRepository().apply {
        refreshResult = DataResult.Success(3)
        refreshPastResult = DataResult.Success(2)
    }
    private val metadata = FakeSyncMetadataRepository()
    private val network = FakeNetworkMonitor()
    private val clock = FakeTimeProvider(currentInstant = TestLaunches.NOW)
    private val reminders = FakeLaunchReminderRepository()
    private val scheduler = FakeLaunchReminderScheduler()

    @Test
    fun `opening the screen refreshes both lists once, and again only when due`() = runTest {
        createViewModel()
        createViewModel()

        assertEquals(1, repository.refreshCallCount)
        assertEquals(1, repository.refreshPastCallCount)
    }

    @Test
    fun `filters by tab, provider and crewed flights`() = runTest {
        repository.setLaunches(
            listOf(
                TestLaunches.launch(id = "f9", provider = "SpaceX"),
                TestLaunches.launch(id = "crew", provider = "SpaceX", crewCount = 4),
                TestLaunches.launch(id = "electron", provider = "Rocket Lab"),
            ),
        )
        repository.setPastLaunches(TestLaunches.past(2))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitLoaded()
            assertEquals(listOf("Rocket Lab", "SpaceX"), state.providers)

            viewModel.onProviderSelected("SpaceX")
            state = awaitItem()
            assertEquals(setOf("f9", "crew"), state.shown.map { it.id }.toSet())

            viewModel.onCrewedOnlyChange(true)
            assertEquals(listOf("crew"), awaitItem().shown.map { it.id })

            viewModel.onTabSelected(LaunchesTab.PAST)
            state = awaitItem()
            assertNull(state.filters.provider)
            assertEquals(LaunchesTab.PAST, state.filters.tab)
            // Crewed only still applies, and the past launches are uncrewed.
            assertEquals(emptyList<String>(), state.shown.map { it.id })

            viewModel.onCrewedOnlyChange(false)
            assertEquals(listOf("past-1", "past-2"), awaitItem().shown.map { it.id })
        }
    }

    @Test
    fun `ringing a bell stores the reminder and arms it`() = runTest {
        val launch = TestLaunches.launch()
        repository.setLaunches(listOf(launch))
        val viewModel = createViewModel()

        viewModel.onReminderChange(launch.id, enabled = true)

        assertEquals(setOf(launch.id), reminders.reminders.first().keys)
        assertEquals(launch.net.minus(java.time.Duration.ofHours(1)), scheduler.scheduledAt)
        viewModel.uiState.test {
            assertEquals(setOf(launch.id), awaitLoaded().reminders)
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<LaunchesUiState>.awaitLoaded(): LaunchesUiState {
        var state = awaitItem()
        while (state.upcoming == null || state.past == null) state = awaitItem()
        return state
    }

    private fun createViewModel(): LaunchesViewModel {
        val refreshSource = RefreshSourceUseCase(metadata, clock)
        val reschedule = RescheduleLaunchRemindersUseCase(repository, reminders, scheduler, clock)
        return LaunchesViewModel(
            observeUpcomingLaunches = ObserveUpcomingLaunchesUseCase(repository),
            observePastLaunches = ObservePastLaunchesUseCase(repository),
            observeReminders = ObserveLaunchRemindersUseCase(reminders),
            observeFreshness = ObserveDataFreshnessUseCase(metadata, network),
            synchronizeUpcomingLaunches = SynchronizeUpcomingLaunchesUseCase(
                repository,
                network,
                refreshSource,
                FakeHomeWidgetUpdater(),
                reschedule,
            ),
            refreshPastLaunches = RefreshPastLaunchesUseCase(repository, network, refreshSource, reschedule),
            setLaunchReminder = SetLaunchReminderUseCase(reminders, reschedule),
        )
    }
}
