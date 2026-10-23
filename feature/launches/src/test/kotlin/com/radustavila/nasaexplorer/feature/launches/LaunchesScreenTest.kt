package com.radustavila.nasaexplorer.feature.launches

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.designsystem.notification.NotificationPermissionToggle
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.core.model.LaunchTimeline
import com.radustavila.nasaexplorer.core.testing.data.TestLaunches
import com.radustavila.nasaexplorer.feature.launches.component.LAUNCH_REMINDER_BELL_TEST_TAG
import com.radustavila.nasaexplorer.feature.launches.list.LAUNCHES_CREWED_FILTER_TEST_TAG
import com.radustavila.nasaexplorer.feature.launches.list.LAUNCHES_TAB_PAST_TEST_TAG
import com.radustavila.nasaexplorer.feature.launches.list.LAUNCH_ITEM_TEST_TAG
import com.radustavila.nasaexplorer.feature.launches.list.LaunchFilters
import com.radustavila.nasaexplorer.feature.launches.list.LaunchesContent
import com.radustavila.nasaexplorer.feature.launches.list.LaunchesTab
import com.radustavila.nasaexplorer.feature.launches.list.LaunchesUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h900dp")
class LaunchesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val upcoming = TestLaunches.upcoming(size = 2)
    private val past = TestLaunches.past(size = 3)

    @Test
    fun `upcoming launches carry a countdown and a bell, past ones neither`() {
        var tab: LaunchesTab? = null
        setContent(LaunchesUiState(upcoming = upcoming, past = past), onTabSelected = { tab = it })

        composeTestRule.onAllNodesWithTag(LAUNCH_ITEM_TEST_TAG).assertCountEquals(2)
        composeTestRule.onAllNodesWithTag(LAUNCH_REMINDER_BELL_TEST_TAG).assertCountEquals(2)
        composeTestRule.onNodeWithTag(LAUNCHES_TAB_PAST_TEST_TAG).performClick()
        assertEquals(LaunchesTab.PAST, tab)
    }

    @Test
    fun `the past tab shows outcomes without bells`() {
        val failed = TestLaunches.launch(
            id = "failed",
            status = LaunchStatus.FAILURE,
            timeline = LaunchTimeline.PAST,
            net = TestLaunches.NOW.minusSeconds(3_600),
        )
        setContent(
            LaunchesUiState(upcoming = upcoming, past = past + failed, filters = LaunchFilters(tab = LaunchesTab.PAST)),
        )

        composeTestRule.onAllNodesWithTag(LAUNCH_ITEM_TEST_TAG).assertCountEquals(4)
        composeTestRule.onAllNodesWithTag(LAUNCH_REMINDER_BELL_TEST_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithText("Failure").assertExists()
    }

    @Test
    fun `tapping a launch opens it and the bell toggles its reminder`() {
        val opened = mutableListOf<String>()
        val belled = mutableListOf<String>()
        setContent(
            LaunchesUiState(upcoming = upcoming, past = past),
            onLaunchClick = opened::add,
            onBell = belled::add,
        )

        composeTestRule.onAllNodesWithTag(LAUNCH_ITEM_TEST_TAG).onFirst().performClick()
        composeTestRule.onAllNodesWithTag(LAUNCH_REMINDER_BELL_TEST_TAG).onFirst().performClick()

        assertEquals(listOf("launch-1"), opened)
        assertEquals(listOf("launch-1"), belled)
    }

    @Test
    fun `the crewed filter is a chip, and no match says so`() {
        var crewed: Boolean? = null
        setContent(
            LaunchesUiState(upcoming = upcoming, past = past, filters = LaunchFilters(crewedOnly = true)),
            onCrewedOnlyChange = { crewed = it },
        )

        composeTestRule.onNodeWithText("No launches match these filters.").assertExists()
        composeTestRule.onNodeWithTag(LAUNCHES_CREWED_FILTER_TEST_TAG).performClick()
        assertEquals(false, crewed)
    }

    @Test
    fun `offline before the first download says so`() {
        setContent(
            LaunchesUiState(
                upcoming = emptyList(),
                past = emptyList(),
                upcomingFreshness = DataFreshness(isOffline = true),
            ),
        )

        composeTestRule.onNodeWithText("You're offline").assertExists()
    }

    private fun setContent(
        uiState: LaunchesUiState,
        onLaunchClick: (String) -> Unit = {},
        onTabSelected: (LaunchesTab) -> Unit = {},
        onCrewedOnlyChange: (Boolean) -> Unit = {},
        onBell: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            LaunchesContent(
                uiState = uiState,
                onBackClick = {},
                onLaunchClick = onLaunchClick,
                onTabSelected = onTabSelected,
                onProviderSelected = {},
                onCrewedOnlyChange = onCrewedOnlyChange,
                reminderToggle = { launch: Launch ->
                    NotificationPermissionToggle(isOn = launch.id in uiState.reminders, onClick = { onBell(launch.id) })
                },
                now = { TestLaunches.NOW },
            )
        }
    }
}
