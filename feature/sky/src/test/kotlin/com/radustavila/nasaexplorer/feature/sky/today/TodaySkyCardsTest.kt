package com.radustavila.nasaexplorer.feature.sky.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.model.Planet
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TodaySkyCardsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `the ISS card shows where the station is and its next visible pass`() {
        var clicks = 0
        composeTestRule.setContent {
            NasaTheme {
                IssNowCardContent(
                    uiState = IssNowUiState(
                        isLoaded = true,
                        position = TestSky.position(isLive = false),
                        nextPass = IssNextPass.Next(TestSky.pass()),
                    ),
                    onClick = { clicks++ },
                )
            }
        }

        composeTestRule.onNodeWithText("ISS now").assertIsDisplayed()
        composeTestRule.onNodeWithText("Over 35.72° N, 155.14° W").assertIsDisplayed()
        composeTestRule.onNodeWithText("up to 62°", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ISS_NOW_CARD_TEST_TAG).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `offline without an orbit, the ISS card says so and asks for a place`() {
        composeTestRule.setContent {
            NasaTheme { IssNowCardContent(uiState = IssNowUiState(isLoaded = true), onClick = {}) }
        }

        composeTestRule.onNodeWithText("not available offline", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Choose where you watch from", substring = true).assertIsDisplayed()
    }

    @Test
    fun `nothing is drawn before the first values arrive`() {
        composeTestRule.setContent {
            NasaTheme {
                IssNowCardContent(uiState = IssNowUiState(), onClick = {})
                SkyTonightSummaryCardContent(uiState = SkyTonightSummaryUiState(), onClick = {})
            }
        }

        composeTestRule.onNodeWithTag(ISS_NOW_CARD_TEST_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(SKY_TONIGHT_SUMMARY_CARD_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `the sky card sums up the Moon, darkness and the visible planets`() {
        composeTestRule.setContent {
            NasaTheme {
                SkyTonightSummaryCardContent(
                    uiState = SkyTonightSummaryUiState(isLoaded = true, sky = TestSky.skyTonight()),
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("First quarter · 58% lit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dark from", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Planets tonight: Saturn, Jupiter").assertIsDisplayed()
    }

    @Test
    fun `the sky card without planets or a place`() {
        val mars = TestSky.planet(Planet.MARS, bestTime = null, bestAltitude = null)
        val noPlanets = TestSky.skyTonight(planets = listOf(mars))
        composeTestRule.setContent {
            NasaTheme {
                SkyTonightSummaryCardContent(
                    uiState = SkyTonightSummaryUiState(isLoaded = true, sky = noPlanets),
                    onClick = {},
                )
                SkyTonightSummaryCardContent(uiState = SkyTonightSummaryUiState(isLoaded = true), onClick = {})
            }
        }

        composeTestRule.onNodeWithText("No bright planets tonight.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Choose where you watch from to see", substring = true).assertIsDisplayed()
    }
}
