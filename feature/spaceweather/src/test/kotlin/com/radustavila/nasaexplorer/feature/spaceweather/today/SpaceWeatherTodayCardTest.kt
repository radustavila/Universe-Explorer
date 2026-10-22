package com.radustavila.nasaexplorer.feature.spaceweather.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCurrentKpUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveKpForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestSpaceWeatherSyncUseCase
import com.radustavila.nasaexplorer.core.testing.data.TestSpaceWeather
import com.radustavila.nasaexplorer.core.testing.fake.FakeSpaceWeatherRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSpaceWeatherSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpaceWeatherTodayCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `shows Kp now, what it means, and a stronger Kp ahead`() {
        var clicks = 0
        composeTestRule.setContent {
            NasaTheme {
                SpaceWeatherTodayCardContent(
                    uiState = SpaceWeatherTodayUiState(
                        isLoaded = true,
                        kp = 4.33,
                        peak = TestSpaceWeather.reading(TestSpaceWeather.CURRENT_SLOT.plusSeconds(THREE_HOURS), 5.67),
                    ),
                    onClick = { clicks++ },
                )
            }
        }

        composeTestRule.onNodeWithText("Kp 4.3 · Active").assertIsDisplayed()
        composeTestRule.onNodeWithText("Up to Kp 6 (Moderate storm)", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SPACE_WEATHER_TODAY_CARD_TEST_TAG).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `nothing cached yet, the card says it downloads while online`() {
        composeTestRule.setContent {
            NasaTheme {
                SpaceWeatherTodayCardContent(uiState = SpaceWeatherTodayUiState(isLoaded = true), onClick = {})
            }
        }

        composeTestRule.onNodeWithText("No Kp reading yet", substring = true).assertIsDisplayed()
    }

    private companion object {
        const val THREE_HOURS = 3L * 60 * 60
    }
}

class SpaceWeatherTodayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSpaceWeatherRepository()
    private val scheduler = FakeSpaceWeatherSyncScheduler()

    @Test
    fun `reads the cache and asks for a Kp refresh without the aurora map`() = runTest {
        repository.currentKp.value = TestSpaceWeather.currentKp(kp = 2.33)
        repository.kpForecast.value = TestSpaceWeather.forecast(2.0, 2.33, 3.0, 5.33, 4.0)
        val viewModel = SpaceWeatherTodayViewModel(
            observeCurrentKp = ObserveCurrentKpUseCase(repository),
            observeKpForecast = ObserveKpForecastUseCase(repository),
            requestSync = RequestSpaceWeatherSyncUseCase(scheduler),
            timeProvider = FakeTimeProvider(currentInstant = TestSpaceWeather.NOW),
        )

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.isLoaded) state = awaitItem()
            assertEquals(2.33, state.kp!!, 0.0)
            assertEquals(5, state.peak?.level)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(false to false), scheduler.requests)
    }

    @Test
    fun `a peak no stronger than now is not repeated`() {
        val forecast = TestSpaceWeather.forecast(3.0, 2.0, 2.33)

        val state = spaceWeatherTodayState(TestSpaceWeather.currentKp(kp = 2.33), forecast, TestSpaceWeather.NOW)

        assertNull(state.peak)
        // Without the 1-minute estimate, Kp now is the slot under way.
        assertEquals(2.33, spaceWeatherTodayState(null, forecast, TestSpaceWeather.NOW).kp!!, 0.0)
    }
}
