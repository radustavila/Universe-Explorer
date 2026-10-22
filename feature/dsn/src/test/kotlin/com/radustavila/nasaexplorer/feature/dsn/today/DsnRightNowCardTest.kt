package com.radustavila.nasaexplorer.feature.dsn.today

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.designsystem.units.LocalUnitPreferences
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDsnRightNowUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDsnSnapshotUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.PollDeepSpaceNetworkUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.model.DsnRightNow
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import com.radustavila.nasaexplorer.core.testing.data.TestDeepSpaceNetwork
import com.radustavila.nasaexplorer.core.testing.fake.FakeDeepSpaceNetworkRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DsnRightNowCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `names the farthest spacecraft in contact, its distance and light time`() {
        var clicks = 0
        composeTestRule.setContent {
            NasaTheme {
                CompositionLocalProvider(
                    LocalUnitPreferences provides UnitPreferences(measurementSystem = MeasurementSystem.METRIC),
                ) {
                    DsnRightNowCardContent(
                        uiState = DsnRightNowUiState(
                            isLoaded = true,
                            hasSnapshot = true,
                            rightNow = DsnRightNow(
                                spacecraft = TestDeepSpaceNetwork.link().spacecraft,
                                siteName = "Canberra",
                                distanceKm = 21_600_000_000.0,
                                observedAt = TestDeepSpaceNetwork.OBSERVED_AT,
                            ),
                        ),
                        onClick = { clicks++ },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Right now: Voyager 2 is talking to Canberra").assertIsDisplayed()
        composeTestRule.onNodeWithText("21.6 billion km away · signal takes 20 h 0 min").assertIsDisplayed()
        composeTestRule.onNodeWithTag(DSN_RIGHT_NOW_CARD_TEST_TAG).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `a quiet network says so`() {
        composeTestRule.setContent {
            NasaTheme {
                DsnRightNowCardContent(uiState = DsnRightNowUiState(isLoaded = true, hasSnapshot = true), onClick = {})
            }
        }

        composeTestRule.onNodeWithText("No spacecraft in contact at the moment").assertIsDisplayed()
    }
}

class DsnRightNowViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeDeepSpaceNetworkRepository()

    @Test
    fun `reads right now from the cache and polls once a minute while visible`() = runTest {
        repository.snapshot.value = TestDeepSpaceNetwork.snapshot()
        val clock = object : TimeProvider {
            override fun now(): Instant = Instant.parse("2026-10-22T16:00:00Z").plusMillis(testScheduler.currentTime)
            override fun today(): LocalDate = now().atZone(ZoneOffset.UTC).toLocalDate()
        }
        val metadata = FakeSyncMetadataRepository()
        val network = FakeNetworkMonitor()
        val viewModel = DsnRightNowViewModel(
            observeRightNow = ObserveDsnRightNowUseCase(repository),
            observeSnapshot = ObserveDsnSnapshotUseCase(repository),
            poll = PollDeepSpaceNetworkUseCase(repository, RefreshSourceUseCase(metadata, clock), network),
        )

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.isLoaded) state = awaitItem()
            assertEquals("Voyager 2", state.rightNow!!.spacecraft.displayName)
        }

        val job = launch { viewModel.pollWhileVisible() }
        testScheduler.advanceTimeBy(Duration.ofMinutes(2).toMillis())
        runCurrent()
        job.cancel()
        assertEquals(3, repository.liveRefreshCount)
    }
}
