package com.radustavila.nasaexplorer.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Which navigation component the tabs get at each window size. The window is sized through the
 * Robolectric qualifiers, so the size classes come from the same measurement the app makes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class NasaNavigationSuiteTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var selected: TopLevelDestination? = null

    private fun showSuite(current: TopLevelDestination? = TopLevelDestination.TODAY) {
        composeTestRule.setContent {
            NasaTheme {
                ProvideWindowAdaptiveInfo {
                    NasaNavigationSuite(
                        currentTopLevelDestination = current,
                        onTopLevelDestinationClick = { selected = it },
                    ) {
                        Box(Modifier.fillMaxSize().testTag(CONTENT))
                    }
                }
            }
        }
    }

    private fun bounds(label: String): DpRect = composeTestRule.onNodeWithText(label).getBoundsInRoot()

    private fun contentBounds(): DpRect = composeTestRule.onNodeWithTag(CONTENT).getBoundsInRoot()

    /** Tabs side by side, below the content. */
    private fun assertBottomBar() {
        val today = bounds("Today")
        val more = bounds("More")
        assertEquals(today.top, more.top)
        assertTrue(more.left > today.left)
        assertTrue(today.top >= contentBounds().bottom)
    }

    /** Tabs stacked, beside the content, no wider than [maxWidth]. */
    private fun assertBesideContent(maxWidth: Float) {
        val today = bounds("Today")
        val more = bounds("More")
        assertEquals(today.left, more.left)
        assertTrue(more.top > today.top)
        val content = contentBounds()
        assertTrue(today.right <= content.left)
        assertTrue(content.left.value <= maxWidth)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a phone held upright gets a bottom bar`() {
        showSuite()

        assertBottomBar()
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp")
    fun `a phone in landscape gets a rail, keeping its height for content`() {
        showSuite()

        assertBesideContent(maxWidth = RAIL_MAX_WIDTH)
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun `a medium window gets a rail`() {
        showSuite()

        assertBesideContent(maxWidth = RAIL_MAX_WIDTH)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `an expanded window gets a rail`() {
        showSuite()

        assertBesideContent(maxWidth = RAIL_MAX_WIDTH)
    }

    @Test
    @Config(qualifiers = "w1700dp-h1000dp")
    fun `an extra-large window gets a permanent drawer`() {
        showSuite()

        assertBesideContent(maxWidth = DRAWER_MAX_WIDTH)
        assertTrue(contentBounds().left.value > RAIL_MAX_WIDTH)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `a detail screen outside the tabs takes the whole window`() {
        showSuite(current = null)

        composeTestRule.onNodeWithText("Today").assertDoesNotExist()
        assertEquals(0.dp, contentBounds().left)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `a tab in the rail reports its click`() {
        showSuite()

        composeTestRule.onNodeWithText("Earth").performClick()

        assertEquals(TopLevelDestination.EARTH, selected)
    }

    private companion object {
        const val CONTENT = "content"
        const val RAIL_MAX_WIDTH = 96f
        const val DRAWER_MAX_WIDTH = 400f
    }
}
