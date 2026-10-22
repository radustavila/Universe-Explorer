package com.radustavila.nasaexplorer.feature.spaceweather.neighbourhood

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.radustavila.nasaexplorer.core.designsystem.units.LocalUnitPreferences
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCloseApproachesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveRecentFireballsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSmallBodiesSyncStatusUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestSmallBodiesSyncUseCase
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import com.radustavila.nasaexplorer.core.testing.data.TestSmallBodies
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSmallBodiesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSmallBodiesSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Duration
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h900dp")
class NeighbourhoodTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val repository = FakeSmallBodiesRepository()
    private val scheduler = FakeSmallBodiesSyncScheduler()
    private val clock = FakeTimeProvider(currentInstant = TestSmallBodies.NOW)

    private fun createViewModel() = NeighbourhoodViewModel(
        observeCloseApproaches = ObserveCloseApproachesUseCase(repository),
        observeFireballs = ObserveRecentFireballsUseCase(repository),
        observeSyncStatus = ObserveSmallBodiesSyncStatusUseCase(scheduler, FakeSyncMetadataRepository()),
        observeConnectivity = ObserveConnectivityUseCase(FakeNetworkMonitor()),
        requestSync = RequestSmallBodiesSyncUseCase(scheduler),
        timeProvider = clock,
    )

    @Test
    fun `opening asks the daily job for a refresh and drops approaches long gone`() = runTest {
        repository.closeApproaches.value = listOf(
            TestSmallBodies.closeApproach("2026 TA", time = TestSmallBodies.NOW.minus(Duration.ofDays(3))),
            TestSmallBodies.closeApproach("2026 UK", time = TestSmallBodies.NOW.minus(Duration.ofHours(10))),
            TestSmallBodies.closeApproach("2020 FN3"),
        )
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(listOf(false), scheduler.requests)
        assertEquals(1, scheduler.periodicSyncRegistrations)
        assertEquals(listOf("2026 UK", "2020 FN3"), viewModel.uiState.value.approaches.map { it.designation })
    }

    private fun setNeighbourhood(system: MeasurementSystem, onApproachClick: (String) -> Unit = {}) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalUnitPreferences provides UnitPreferences(measurementSystem = system)) {
                NeighbourhoodContent(
                    uiState = NeighbourhoodUiState(
                        approaches = listOf(
                            TestSmallBodies.closeApproach("2026 UK", distanceAu = 0.0194736855784829),
                            TestSmallBodies.closeApproach("2025 UK9", sentry = TestSmallBodies.sentry("2025 UK9")),
                        ),
                        fireballs = listOf(TestSmallBodies.fireball()),
                    ),
                    onBackClick = {},
                    onRefresh = {},
                    onApproachClick = onApproachClick,
                )
            }
        }
    }

    @Test
    fun `objects on the Sentry list wear a badge, and tapping one opens it`() {
        var opened: String? = null
        setNeighbourhood(MeasurementSystem.METRIC) { opened = it }

        composeTestRule.onAllNodesWithTag(SENTRY_BADGE_TEST_TAG, useUnmergedTree = true).fetchSemanticsNodes().let {
            assertEquals(1, it.size)
        }
        composeTestRule.onNodeWithText("7.6 LD · 5.8 km/s · 34–75 m across").assertIsDisplayed()
        composeTestRule.onNodeWithTag(closeApproachTestTag("2025 UK9")).performClick()
        assertEquals("2025 UK9", opened)

        composeTestRule.onNodeWithTag(NEIGHBOURHOOD_LIST_TEST_TAG).performScrollToNode(hasText("0.18 kilotons of TNT"))
        composeTestRule.onNodeWithText("41.8° S, 173.1° W · 27 km up · 13.8 km/s").assertIsDisplayed()
    }

    @Test
    fun `imperial units convert speeds, sizes and altitudes but keep lunar distances`() {
        setNeighbourhood(MeasurementSystem.IMPERIAL)

        composeTestRule.onNodeWithText("7.6 LD · 3.6 mi/s · 110–250 ft across").assertIsDisplayed()
        composeTestRule.onNodeWithTag(NEIGHBOURHOOD_LIST_TEST_TAG).performScrollToNode(hasText("0.18 kilotons of TNT"))
        composeTestRule.onNodeWithText("41.8° S, 173.1° W · 17 mi up · 8.6 mi/s").assertIsDisplayed()
    }
}
