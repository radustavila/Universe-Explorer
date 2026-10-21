package com.radustavila.nasaexplorer.core.designsystem.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.radustavila.nasaexplorer.core.model.DataFreshness
import java.time.Duration
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FreshnessLabelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun ago(duration: Duration): Instant = Instant.now().minus(duration)

    private fun setLabel(freshness: DataFreshness) {
        composeTestRule.setContent { FreshnessLabel(freshness) }
    }

    @Test
    fun `nothing is shown before the first sync`() {
        setLabel(DataFreshness(lastUpdatedAt = null, isOffline = true))

        composeTestRule.onNodeWithTag(FRESHNESS_LABEL_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `a recent sync reads as just now`() {
        setLabel(DataFreshness(lastUpdatedAt = ago(Duration.ofSeconds(10))))

        composeTestRule.onNodeWithText("Updated just now").assertExists()
    }

    @Test
    fun `an older sync reads as a relative time`() {
        setLabel(DataFreshness(lastUpdatedAt = ago(Duration.ofMinutes(5))))

        composeTestRule.onNodeWithText("Updated 5 min", substring = true).assertExists()
        composeTestRule.onNodeWithText("ago", substring = true).assertExists()
    }

    @Test
    fun `a failed refresh is admitted`() {
        setLabel(DataFreshness(lastUpdatedAt = ago(Duration.ofHours(2)), lastRefreshFailed = true))

        composeTestRule.onNodeWithText("The last refresh failed.", substring = true).assertExists()
    }

    @Test
    fun `offline it says the data is what was saved, and from when`() {
        setLabel(DataFreshness(lastUpdatedAt = ago(Duration.ofDays(3)), isOffline = true, lastRefreshFailed = true))

        composeTestRule.onNodeWithText("Offline · showing data from", substring = true).assertExists()
        composeTestRule.onNodeWithText("refresh failed", substring = true).assertDoesNotExist()
    }
}
