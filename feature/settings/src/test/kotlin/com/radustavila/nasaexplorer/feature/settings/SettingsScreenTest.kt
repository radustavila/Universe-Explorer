package com.radustavila.nasaexplorer.feature.settings

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.TimeFormat
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h900dp")
class SettingsScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fixture = SettingsFixture()
    private var customizeTodayClicks = 0

    private fun setScreen() {
        val viewModel = fixture.createViewModel()
        composeTestRule.setContent {
            SettingsScreen(
                onBackClick = {},
                onCustomizeTodayClick = { customizeTodayClicks++ },
                locationSection = { Text("Location slot") },
                viewModel = viewModel,
            )
        }
    }

    private fun scrollTo(tag: String) {
        composeTestRule.onNodeWithTag(SETTINGS_LIST_TEST_TAG).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun `Customize Today opens from the top of Settings`() {
        setScreen()

        composeTestRule.onNodeWithTag(SETTINGS_CUSTOMIZE_TODAY_TEST_TAG)
            .assertTextContains("Customize Today")
            .performClick()

        assertEquals(1, customizeTodayClicks)
    }

    @Test
    fun `picking imperial and a 24-hour clock saves them and updates the example`() {
        setScreen()

        composeTestRule.onNodeWithTag(measurementOptionTestTag(MeasurementSystem.METRIC)).performClick()
        composeTestRule.onNodeWithTag(SETTINGS_UNITS_EXAMPLE_TEST_TAG).assertTextContains("420 km", substring = true)
        composeTestRule.onNodeWithTag(measurementOptionTestTag(MeasurementSystem.IMPERIAL)).performClick()
        composeTestRule.onNodeWithTag(timeFormatOptionTestTag(TimeFormat.TWENTY_FOUR_HOUR)).performClick()

        composeTestRule.onNodeWithTag(measurementOptionTestTag(MeasurementSystem.IMPERIAL)).assertIsSelected()
        composeTestRule.onNodeWithTag(SETTINGS_UNITS_EXAMPLE_TEST_TAG)
            .assertTextContains("261 mi", substring = true)
            .assertTextContains("17,150 mph", substring = true)
            .assertTextContains("21:30", substring = true)
        assertEquals(
            UnitPreferences(MeasurementSystem.IMPERIAL, TimeFormat.TWENTY_FOUR_HOUR),
            fixture.preferences.currentUnitPreferences,
        )
    }

    @Test
    fun `the automatic option says what it means in this region`() {
        setScreen()

        // Robolectric's default locale is en-US.
        composeTestRule.onNodeWithText("Automatic (imperial)").assertExists()
    }

    @Test
    fun `notification and wallpaper switches turn their choices on`() {
        setScreen()

        scrollTo(SETTINGS_NEW_APOD_SWITCH_TEST_TAG)
        composeTestRule.onNodeWithTag(SETTINGS_NEW_APOD_SWITCH_TEST_TAG).assertIsOff().performClick()
        composeTestRule.onNodeWithTag(SETTINGS_NEW_APOD_SWITCH_TEST_TAG).assertIsOn()
        assertTrue(fixture.preferences.currentNewApodNotificationsEnabled)

        scrollTo(SETTINGS_WALLPAPER_SWITCH_TEST_TAG)
        composeTestRule.onNodeWithTag(SETTINGS_WALLPAPER_SWITCH_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(SETTINGS_WALLPAPER_SWITCH_TEST_TAG).assertIsOn()
        assertTrue(fixture.preferences.currentDailyWallpaperEnabled)
    }

    @Test
    fun `aurora alerts and ISS reminders use the saved location`() = runTest {
        fixture.location.setLocation(TestSky.location())
        setScreen()

        scrollTo(SETTINGS_AURORA_SWITCH_TEST_TAG)
        composeTestRule.onNodeWithTag(SETTINGS_AURORA_SWITCH_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(SETTINGS_AURORA_SWITCH_TEST_TAG).assertIsOn()
        // Too far south for a Kp threshold, but the place is the saved one.
        composeTestRule.onNodeWithText("44.4° N, 26.1° E", substring = true).assertExists()
        assertTrue(fixture.aurora.settings.first().enabled)

        scrollTo(SETTINGS_ISS_SWITCH_TEST_TAG)
        composeTestRule.onNodeWithTag(SETTINGS_ISS_SWITCH_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(SETTINGS_ISS_SWITCH_TEST_TAG).assertIsOn()
    }

    @Test
    fun `clear night alerts switch on for the saved place and offer a minimum score`() = runTest {
        fixture.location.setLocation(TestSky.location())
        setScreen()

        scrollTo(SETTINGS_STARGAZING_SWITCH_TEST_TAG)
        composeTestRule.onNodeWithTag(SETTINGS_STARGAZING_SWITCH_TEST_TAG).assertIsOff().performClick()
        composeTestRule.onNodeWithTag(SETTINGS_STARGAZING_SWITCH_TEST_TAG).assertIsOn()
        scrollTo(stargazingMinimumOptionTestTag(75))
        composeTestRule.onNodeWithTag(stargazingMinimumOptionTestTag(75)).performClick()

        composeTestRule.onNodeWithText("score is 75/100 or more", substring = true).assertExists()
        assertEquals(75, fixture.stargazing.settings.first().minimumScore)
    }

    @Test
    fun `aurora alerts without any place explain what is missing`() {
        setScreen()

        scrollTo(SETTINGS_AURORA_SWITCH_TEST_TAG)
        composeTestRule.onNodeWithTag(SETTINGS_AURORA_SWITCH_TEST_TAG).performClick()

        composeTestRule.onNodeWithTag(SETTINGS_AURORA_SWITCH_TEST_TAG).assertIsOff()
        composeTestRule.onNodeWithText("Save a location below first", substring = true).assertExists()
    }

    @Test
    fun `the location section of Sky tonight is embedded`() {
        setScreen()

        composeTestRule.onNodeWithTag(SETTINGS_LIST_TEST_TAG).performScrollToNode(hasText("Location slot"))
        composeTestRule.onNodeWithText("Location slot").assertExists()
    }

    @Test
    fun `storage shows each cache and clearing it asks first, then keeps favourites`() {
        setScreen()

        scrollTo(SETTINGS_CLEAR_CACHE_TEST_TAG)
        composeTestRule.onNodeWithText("Image cache").assertExists()
        composeTestRule.onNodeWithText("Network cache").assertExists()
        composeTestRule.onNodeWithText("Offline favourites").assertExists()
        composeTestRule.onNodeWithTag(SETTINGS_CLEAR_CACHE_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Your favourites and their offline copies are kept", substring = true).assertExists()
        composeTestRule.onNodeWithTag(SETTINGS_CLEAR_CACHE_CONFIRM_TEST_TAG).performClick()

        composeTestRule.onNodeWithText("Cache cleared").assertExists()
        assertEquals(1, fixture.images.clearCount)
        assertEquals(1, fixture.feeds.clearCount)
    }
}
