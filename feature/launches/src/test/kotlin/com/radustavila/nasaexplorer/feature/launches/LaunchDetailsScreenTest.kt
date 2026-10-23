package com.radustavila.nasaexplorer.feature.launches

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.radustavila.nasaexplorer.core.designsystem.notification.NotificationPermissionToggle
import com.radustavila.nasaexplorer.core.model.BoosterLanding
import com.radustavila.nasaexplorer.core.model.BoosterStage
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.core.model.LaunchTimeline
import com.radustavila.nasaexplorer.core.testing.data.TestLaunches
import com.radustavila.nasaexplorer.feature.launches.component.LAUNCH_REMINDER_BELL_TEST_TAG
import com.radustavila.nasaexplorer.feature.launches.details.LAUNCH_WEBCAST_TEST_TAG
import com.radustavila.nasaexplorer.feature.launches.details.LaunchDetailsContent
import com.radustavila.nasaexplorer.feature.launches.details.LaunchDetailsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2000dp")
class LaunchDetailsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `an upcoming launch shows its countdown, landing plan, pad and a bell`() {
        val launch = TestLaunches.detailed()
        var bell = 0
        setContent(LaunchDetailsUiState(id = launch.id, isLoaded = true, launch = launch), onBell = { bell++ })

        composeTestRule.onNodeWithText("T-02:00:00").assertExists()
        composeTestRule.onNodeWithText("Weather 85% go").assertExists()
        composeTestRule.onNodeWithText("B1090 · flight 4").assertExists()
        composeTestRule.onNodeWithText("Landing on Of Course I Still Love You (OCISLY)").assertExists()
        composeTestRule.onNodeWithText("Orbit: Low Earth Orbit (LEO)").assertExists()
        composeTestRule.onNodeWithTag(LAUNCH_REMINDER_BELL_TEST_TAG).performClick()
        assertEquals(1, bell)
    }

    @Test
    fun `a flown launch reports the landing and has no bell`() {
        val launch = TestLaunches.detailed(
            status = LaunchStatus.SUCCESS,
            timeline = LaunchTimeline.PAST,
            landingSuccess = true,
        )
        setContent(LaunchDetailsUiState(id = launch.id, isLoaded = true, launch = launch))

        composeTestRule.onNodeWithText("Landed on Of Course I Still Love You (OCISLY)").assertExists()
        composeTestRule.onNodeWithTag(LAUNCH_REMINDER_BELL_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `an expended booster says so`() {
        val launch = TestLaunches.detailed().copy(
            boosters = listOf(
                BoosterStage(
                    serial = "B1106",
                    flightNumber = 1,
                    type = "Core",
                    landing = BoosterLanding(false, null, "Atlantic Ocean", "ATL", "Expended", "EXP"),
                ),
            ),
        )
        setContent(LaunchDetailsUiState(id = launch.id, isLoaded = true, launch = launch))

        composeTestRule.onNodeWithText("B1106 · first flight").assertExists()
        composeTestRule.onNodeWithText("Expended, no landing").assertExists()
    }

    @Test
    fun `a webcast button opens the stream`() {
        val launch = TestLaunches.detailed()
        val opened = mutableListOf<String>()
        setContent(LaunchDetailsUiState(id = launch.id, isLoaded = true, launch = launch), onOpenUrl = opened::add)

        composeTestRule.onNodeWithTag(LAUNCH_WEBCAST_TEST_TAG).performScrollTo().performClick()

        assertEquals(listOf("https://www.youtube.com/watch?v=abc"), opened)
    }

    @Test
    fun `a launch no longer cached says so`() {
        setContent(LaunchDetailsUiState(id = "gone", isLoaded = true, launch = null))

        composeTestRule.onNodeWithText("This launch is no longer in the saved list.").assertExists()
    }

    private fun setContent(
        uiState: LaunchDetailsUiState,
        onBell: () -> Unit = {},
        onOpenUrl: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            LaunchDetailsContent(
                uiState = uiState,
                reminder = NotificationPermissionToggle(isOn = false, onClick = onBell),
                now = { TestLaunches.NOW },
                onBackClick = {},
                onOpenUrl = onOpenUrl,
                padMap = { _, modifier -> Box(modifier) },
            )
        }
    }
}
