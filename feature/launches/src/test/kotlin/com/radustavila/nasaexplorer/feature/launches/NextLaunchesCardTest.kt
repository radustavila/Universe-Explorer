package com.radustavila.nasaexplorer.feature.launches

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.radustavila.nasaexplorer.core.designsystem.units.LocalUnitPreferences
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.core.model.NetPrecision
import com.radustavila.nasaexplorer.core.model.TimeFormat
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import com.radustavila.nasaexplorer.core.testing.data.TestLaunches
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp")
class NextLaunchesCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val now = TestLaunches.NOW

    @Test
    fun `lists each launch with its countdown and status`() {
        val launch = TestLaunches.launch(net = now + Duration.parse("PT1H2M3S"), status = LaunchStatus.GO)

        setCard(listOf(launch))

        composeTestRule.onNodeWithText("Bandwagon 5").assertExists()
        composeTestRule.onNodeWithText("T-01:02:03").assertExists()
        composeTestRule.onNodeWithText("Go").assertExists()
    }

    @Test
    fun `launches further away count whole days and show the lift-off time in the user's clock`() {
        val net = now + Duration.parse("P2DT3H4M5S")
        val local = net.atZone(ZoneId.systemDefault())

        setCard(listOf(TestLaunches.launch(net = net, status = LaunchStatus.GO)), timeFormat = TimeFormat.TWENTY_FOUR_HOUR)

        composeTestRule.onNodeWithText("T-2d 03:04:05").assertExists()
        composeTestRule.onNodeWithText(local.format(DateTimeFormatter.ofPattern("HH:mm")), substring = true).assertExists()
    }

    @Test
    fun `renders the launches it is given`() {
        setCard(TestLaunches.upcoming(size = 3))

        composeTestRule.onAllNodesWithTag(LAUNCH_ROW_TEST_TAG).assertCountEquals(3)
    }

    @Test
    fun `tapping a launch opens its detail page, the play button its livestream`() {
        val opened = mutableListOf<String>()
        val clicked = mutableListOf<String>()
        val launch = TestLaunches.launch()

        setCard(listOf(launch), onOpenUrl = opened::add, onLaunchClick = clicked::add)
        composeTestRule.onNodeWithTag(LAUNCH_ROW_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(LAUNCH_LIVESTREAM_BUTTON_TEST_TAG).performClick()

        assertEquals(listOf(launch.id), clicked)
        assertEquals(listOf(launch.livestreamUrl), opened)
    }

    @Test
    fun `a launch without a livestream shows no watch button`() {
        val clicked = mutableListOf<String>()
        val launch = TestLaunches.launch(livestreamUrl = null)

        setCard(listOf(launch), onLaunchClick = clicked::add)
        composeTestRule.onAllNodesWithTag(LAUNCH_LIVESTREAM_BUTTON_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(LAUNCH_ROW_TEST_TAG).onFirst().performClick()

        assertEquals(listOf(launch.id), clicked)
    }

    @Test
    fun `See all opens the Launches screen`() {
        var seeAll = 0
        setCard(TestLaunches.upcoming(size = 1), onSeeAllClick = { seeAll++ })

        composeTestRule.onNodeWithTag(LAUNCHES_SEE_ALL_TEST_TAG).performClick()

        assertEquals(1, seeAll)
    }

    @Test
    fun `a launch known only to the month shows the month instead of a countdown`() {
        val net = Instant.parse("2026-11-30T00:00:00Z")
        setCard(listOf(TestLaunches.launch(net = net, status = LaunchStatus.TBD, netPrecision = NetPrecision.MONTH)))

        composeTestRule.onNodeWithText("NET November 2026").assertExists()
    }

    @Test
    fun `a TBD launch shows its date instead of a ticking countdown`() {
        setCard(listOf(TestLaunches.launch(status = LaunchStatus.TBD)))

        composeTestRule.onNodeWithText("TBD").assertExists()
        composeTestRule.onAllNodesWithTag(LAUNCH_ROW_TEST_TAG).assertCountEquals(1)
        composeTestRule.onNodeWithText("T-02:00:00").assertDoesNotExist()
    }

    @Test
    fun `offline the card says the list is the saved one`() {
        setCard(
            TestLaunches.upcoming(size = 1),
            freshness = DataFreshness(lastUpdatedAt = Instant.now().minusSeconds(120), isOffline = true),
        )

        composeTestRule.onNodeWithText("Offline · showing data from", substring = true).assertExists()
    }

    @Test
    fun `draws nothing without launches`() {
        setCard(emptyList())

        composeTestRule.onNodeWithTag(NEXT_LAUNCHES_CARD_TEST_TAG).assertDoesNotExist()
    }

    private fun setCard(
        launches: List<com.radustavila.nasaexplorer.core.model.Launch>,
        onOpenUrl: (String) -> Unit = {},
        onLaunchClick: (String) -> Unit = {},
        onSeeAllClick: () -> Unit = {},
        timeFormat: TimeFormat = TimeFormat.SYSTEM,
        freshness: DataFreshness = DataFreshness(),
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalUnitPreferences provides UnitPreferences(timeFormat = timeFormat)) {
                NextLaunchesCardContent(
                    launches = launches,
                    now = { now },
                    onLaunchClick = onLaunchClick,
                    onOpenUrl = onOpenUrl,
                    onSeeAllClick = onSeeAllClick,
                    freshness = freshness,
                )
            }
        }
    }
}
