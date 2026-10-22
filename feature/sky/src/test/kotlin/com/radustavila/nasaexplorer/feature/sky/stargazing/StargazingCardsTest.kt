package com.radustavila.nasaexplorer.feature.sky.stargazing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingScorer
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.StargazingForecast
import com.radustavila.nasaexplorer.core.testing.fake.TestStargazing
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StargazingCardsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val noon = Instant.parse("2026-09-10T12:00:00Z")

    /** Clear tonight under a full Moon, then overcast, clear, and two nights past the forecast. */
    private val uiState: StargazingUiState = run {
        val clouds = TestStargazing.cloudForecast(from = noon, hours = 80) { hour ->
            when (hour / 24) {
                1 -> 100.0
                else -> 0.0
            }
        }
        val nights = (0L until 5L).map { day ->
            val night = TestStargazing.darkNight(
                darkStart = Instant.parse("2026-09-10T20:00:00Z").plus(Duration.ofDays(day)),
                moonAltitude = if (day == 0L) 30.0 else -30.0,
                moonIlluminated = if (day == 0L) 1.0 else 0.1,
            )
            StargazingScorer.score(night, clouds, now = noon)
        }
        StargazingUiState(
            isLoaded = true,
            hasLocation = true,
            forecast = StargazingForecast(GeoCoordinate(44.43, 26.1), nights, fetchedAt = noon),
            freshness = DataFreshness(lastUpdatedAt = noon),
        )
    }

    @Test
    fun `the card shows tonight in full, the next four nights and the MET Norway credit`() {
        composeTestRule.setContent { NasaTheme { StargazingForecastCardContent(uiState) } }

        composeTestRule.onNodeWithText("Tonight: Fair").assertIsDisplayed()
        composeTestRule.onNodeWithText("Moon up (100% lit)", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Observing score 50 out of 100, Fair").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Observing score 0 out of 100, Poor").assertIsDisplayed()
        composeTestRule.onNodeWithTag(stargazingNightTestTag(4)).assertIsDisplayed()
        assertEquals(2, composeTestRule.onAllNodesWithText("No forecast").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithText("Based on data from MET Norway").assertIsDisplayed()
        composeTestRule.onNodeWithText("Updated", substring = true).assertIsDisplayed()
    }

    @Test
    fun `without a place the card stays out of the way`() {
        composeTestRule.setContent {
            NasaTheme { StargazingForecastCardContent(StargazingUiState(isLoaded = true, hasLocation = false)) }
        }

        composeTestRule.onNodeWithTag(STARGAZING_CARD_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `the Today line sums up tonight and opens Sky tonight`() {
        var clicks = 0
        composeTestRule.setContent { NasaTheme { StargazingTodayCardContent(uiState, onClick = { clicks++ }) } }

        composeTestRule.onNodeWithText("Fair · 50/100").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear from", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Based on data from MET Norway").assertIsDisplayed()
        composeTestRule.onNodeWithTag(STARGAZING_TODAY_CARD_TEST_TAG).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `the Today line asks for a place when there is none`() {
        composeTestRule.setContent {
            NasaTheme { StargazingTodayCardContent(StargazingUiState(isLoaded = true), onClick = {}) }
        }

        composeTestRule.onNodeWithText("Save a place in Sky tonight", substring = true).assertIsDisplayed()
    }
}
