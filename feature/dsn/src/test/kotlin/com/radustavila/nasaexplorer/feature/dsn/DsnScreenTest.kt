package com.radustavila.nasaexplorer.feature.dsn

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.designsystem.units.LocalUnitPreferences
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.DsnSiteLink
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import com.radustavila.nasaexplorer.core.testing.data.TestDeepSpaceNetwork
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DsnScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(uiState: DsnUiState, onSpacecraftClick: (String, String) -> Unit = { _, _ -> }) {
        composeTestRule.setContent {
            NasaTheme {
                CompositionLocalProvider(LocalUnitPreferences provides METRIC) {
                    DsnContent(
                        uiState = uiState,
                        onBackClick = {},
                        onSpacecraftClick = onSpacecraftClick,
                        onSheetDismiss = {},
                    )
                }
            }
        }
    }

    @Test
    fun `sites list their busy dishes with the spacecraft, and idle ones as idle`() {
        val clicks = mutableListOf<Pair<String, String>>()
        show(
            DsnUiState(isLoaded = true, snapshot = TestDeepSpaceNetwork.snapshot()),
            onSpacecraftClick = { dish, code -> clicks += dish to code },
        )

        composeTestRule.onNodeWithText("2 spacecraft in contact").assertIsDisplayed()
        composeTestRule.onNodeWithText("Goldstone").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Idle · Engineering Upgrades").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Mars Reconnaissance Orbiter · standing by").assertIsDisplayed()
        composeTestRule.onNodeWithTag(DSN_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(dsnSpacecraftTestTag("DSS43", "VGR2")))
        composeTestRule.onNodeWithText("DSS 43 · 70 m · Az 207° · El 17°").assertIsDisplayed()
        composeTestRule.onNodeWithTag(dsnSpacecraftTestTag("DSS43", "VGR2")).performClick()
        assertEquals(listOf("DSS43" to "VGR2"), clicks)
    }

    @Test
    fun `the open spacecraft shows distance, light time, data rate and its mission`() {
        val snapshot = TestDeepSpaceNetwork.snapshot()
        val canberra = snapshot.sites[2]
        show(
            DsnUiState(
                isLoaded = true,
                snapshot = snapshot,
                selected = DsnSiteLink(canberra, canberra.dishes[0], canberra.dishes[0].links[0]),
            ),
        )

        composeTestRule.onNodeWithTag(DSN_SPACECRAFT_SHEET_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Through DSS 43, Canberra").assertIsDisplayed()
        composeTestRule.onNodeWithText("21.6 billion km").assertIsDisplayed()
        composeTestRule.onNodeWithText("20 h 0 min one way").assertExists()
        composeTestRule.onNodeWithText("160 bit/s · X band").assertExists()
        composeTestRule.onNodeWithText("Uranus and Neptune", substring = true).assertExists()
    }

    @Test
    fun `a failing feed says it is unavailable and keeps the last state`() {
        show(
            DsnUiState(
                isLoaded = true,
                snapshot = TestDeepSpaceNetwork.snapshot(),
                freshness = DataFreshness(lastUpdatedAt = TestDeepSpaceNetwork.OBSERVED_AT, lastRefreshFailed = true),
            ),
        )

        composeTestRule.onNodeWithText("Live feed unavailable", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Goldstone").assertIsDisplayed()
    }

    @Test
    fun `without any snapshot an unreachable feed is reported, not an error screen`() {
        show(DsnUiState(isLoaded = true, freshness = DataFreshness(lastRefreshFailed = true)))

        composeTestRule.onNodeWithText("DSN Now is unavailable").assertIsDisplayed()
    }

    @Test
    fun `the farthest list shows AU, kilometres and light time`() {
        show(DsnUiState(isLoaded = true, farthest = TestDeepSpaceNetwork.farthest))

        composeTestRule.onNodeWithText("Farthest from home").assertIsDisplayed()
        composeTestRule.onNodeWithText("172.65 AU · 25.8 billion km").assertIsDisplayed()
        composeTestRule.onNodeWithText("Light takes 23 h 55 min to reach it").assertIsDisplayed()
    }

    @Test
    fun `polling runs only while the screen is started`() {
        val owner = TestLifecycleOwner()
        var running = 0
        var starts = 0
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                PollWhileStarted {
                    starts++
                    running++
                    try {
                        awaitCancellation()
                    } finally {
                        running--
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(1, running)

        // Another screen on top, the app in the background, or the screen off.
        composeTestRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        composeTestRule.waitForIdle()
        assertEquals(0, running)

        composeTestRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeTestRule.waitForIdle()
        assertEquals(1, running)
        assertEquals(2, starts)
    }

    private companion object {
        val METRIC = UnitPreferences(measurementSystem = MeasurementSystem.METRIC)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }
}
