package com.radustavila.nasaexplorer.feature.earthevents

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveOpenEarthEventsUseCase
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.testing.data.TestEarthEvents
import com.radustavila.nasaexplorer.core.testing.fake.FakeEarthEventRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RightNowOnEarthCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `counts the largest categories, then sums the rest`() {
        var clicks = 0
        composeTestRule.setContent {
            RightNowOnEarthCardContent(
                counts = listOf(
                    EarthEventCategory.WILDFIRES to 192,
                    EarthEventCategory.SEVERE_STORMS to 7,
                    EarthEventCategory.SEA_LAKE_ICE to 1,
                    EarthEventCategory.VOLCANOES to 2,
                    EarthEventCategory.FLOODS to 1,
                ),
                onClick = { clicks++ },
            )
        }

        composeTestRule.onNodeWithText("Right now on Earth").assertIsDisplayed()
        composeTestRule.onNodeWithText("192 active wildfires").assertIsDisplayed()
        composeTestRule.onNodeWithText("7 severe storms").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 iceberg or sea-ice event").assertIsDisplayed()
        composeTestRule.onNodeWithText("and 3 other events").assertIsDisplayed()

        composeTestRule.onNodeWithTag(RIGHT_NOW_ON_EARTH_CARD_TEST_TAG).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `nothing cached, no card`() {
        composeTestRule.setContent { RightNowOnEarthCardContent(counts = emptyList(), onClick = {}) }

        composeTestRule.onNodeWithTag(RIGHT_NOW_ON_EARTH_CARD_TEST_TAG).assertDoesNotExist()
    }
}

class RightNowOnEarthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `counts the events shown by default per category, largest first`() = runTest {
        val repository = FakeEarthEventRepository()
        repository.setEvents(
            listOf(
                TestEarthEvents.wildfire(id = "f1"),
                TestEarthEvents.wildfire(id = "f2"),
                TestEarthEvents.storm(),
                // Last reported 15 and 43 days ago: history is left out of the card, expired everywhere.
                TestEarthEvents.wildfire(id = "f3", at = TestEarthEvents.NOW.minus(Duration.ofDays(15))),
                TestEarthEvents.wildfire(id = "f4", at = TestEarthEvents.NOW.minus(Duration.ofDays(43))),
            ),
        )
        val timeProvider = FakeTimeProvider(currentDate = LocalDate.of(2026, 10, 19))
        val viewModel = RightNowOnEarthViewModel(ObserveOpenEarthEventsUseCase(repository, timeProvider))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.counts.collect {} }

        assertEquals(
            listOf(EarthEventCategory.WILDFIRES to 2, EarthEventCategory.SEVERE_STORMS to 1),
            viewModel.counts.value,
        )
    }
}
