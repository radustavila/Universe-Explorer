package com.radustavila.nasaexplorer.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import com.radustavila.nasaexplorer.core.designsystem.adaptive.LocalWindowAdaptiveInfo
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.ui.ProvideWindowAdaptiveInfo
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Serializable
internal data object TestHomeRoute

@Serializable
internal data object TestItemsRoute

@Serializable
internal data class TestItemRoute(val id: Int)

/**
 * A list screen hosted the way `NasaNavHost` hosts the feed, the gallery, the exoplanets and the
 * favourites: one pane (items open full screen) until the window is expanded, two panes from then.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ListDetailLayoutTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** [window] stands in for the measured window when given. */
    private fun showList(window: State<WindowAdaptiveInfo>? = null) {
        composeTestRule.setContent {
            NasaTheme {
                ProvideWindow(window) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = TestHomeRoute) {
                        composable<TestHomeRoute> {
                            Button(onClick = { navController.navigate(TestItemsRoute) }) { Text(OPEN_LIST) }
                        }
                        composable<TestItemsRoute> {
                            ListDetailLayout(
                                openFullScreen = { id: Int -> navController.navigate(TestItemRoute(id)) },
                                openInPane = { id, navOptions -> navigate(TestItemRoute(id), navOptions) },
                                opensInPane = { id -> id != NOT_IN_PANE },
                                detailPanes = { pane, onBackClick ->
                                    composable<TestItemRoute> { entry ->
                                        val id = entry.toRoute<TestItemRoute>().id
                                        Column {
                                            Text("Detail $id")
                                            Button(onClick = onBackClick) { Text(DETAIL_BACK) }
                                            Button(onClick = { pane.navigate(TestItemRoute(id + 1)) }) { Text(DETAIL_LINK) }
                                        }
                                    }
                                },
                                list = { onItemClick ->
                                    Column {
                                        var query by remember { mutableStateOf("") }
                                        TextField(value = query, onValueChange = { query = it }, modifier = Modifier.testTag(SEARCH))
                                        (1..NOT_IN_PANE).forEach { id ->
                                            Text("Item $id", Modifier.clickable { onItemClick(id) })
                                        }
                                    }
                                },
                            )
                        }
                        composable<TestItemRoute> { entry -> Text("Full screen ${entry.toRoute<TestItemRoute>().id}") }
                    }
                }
            }
        }
        composeTestRule.onNodeWithText(OPEN_LIST).performClick()
    }

    @Composable
    private fun ProvideWindow(window: State<WindowAdaptiveInfo>?, content: @Composable () -> Unit) {
        if (window == null) {
            ProvideWindowAdaptiveInfo(content)
        } else {
            CompositionLocalProvider(LocalWindowAdaptiveInfo provides window.value, content = content)
        }
    }

    private fun pressBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    private fun assertDisplayed(text: String) {
        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun assertGone(text: String) {
        composeTestRule.onNodeWithText(text).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `on a phone the list is alone and an item opens full screen`() {
        showList()
        composeTestRule.onNodeWithTag(DETAIL_PANE_TEST_TAG).assertDoesNotExist()

        composeTestRule.onNodeWithText("Item 2").performClick()

        assertDisplayed("Full screen 2")
        assertGone("Item 1")

        pressBack()

        assertDisplayed("Item 1")
        assertGone("Full screen 2")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `showing the list does not put focus in its text field`() {
        showList()

        composeTestRule.onNodeWithTag(SEARCH).assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun `a medium window still shows one pane`() {
        showList()
        composeTestRule.onNodeWithTag(DETAIL_PANE_TEST_TAG).assertDoesNotExist()

        composeTestRule.onNodeWithText("Item 2").performClick()

        assertDisplayed("Full screen 2")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `an expanded window shows the picked item beside the list`() {
        showList()
        composeTestRule.onNodeWithTag(LIST_PANE_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DETAIL_PANE_PLACEHOLDER_TEST_TAG).assertIsDisplayed()

        composeTestRule.onNodeWithText("Item 2").performClick()

        assertDisplayed("Detail 2")
        assertDisplayed("Item 1")
        assertGone("Full screen 2")
        composeTestRule.onNodeWithTag(DETAIL_PANE_PLACEHOLDER_TEST_TAG).assertDoesNotExist()

        // A new pick replaces the shown item.
        composeTestRule.onNodeWithText("Item 3").performClick()

        assertDisplayed("Detail 3")
        assertGone("Detail 2")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `the panes follow the window when it widens after the first frame`() {
        // As on a device, where the window's size arrives after the first composition.
        val window = mutableStateOf(windowOf(widthDp = 0, heightDp = 0))
        showList(window)
        composeTestRule.onNodeWithTag(DETAIL_PANE_TEST_TAG).assertDoesNotExist()

        window.value = windowOf(widthDp = 1280, heightDp = 800)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(DETAIL_PANE_PLACEHOLDER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Item 2").performClick()
        assertDisplayed("Detail 2")
        assertDisplayed("Item 1")
    }

    private fun windowOf(widthDp: Int, heightDp: Int) =
        WindowAdaptiveInfo(WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp, heightDp), Posture())

    @Test
    @Config(qualifiers = "w923dp-h411dp")
    fun `a phone in landscape shows two panes`() {
        showList()

        composeTestRule.onNodeWithTag(DETAIL_PANE_PLACEHOLDER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Item 2").performClick()
        assertDisplayed("Detail 2")
        assertDisplayed("Item 1")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `with two panes back leaves the screen instead of stepping through picks`() {
        showList()
        composeTestRule.onNodeWithText("Item 1").performClick()
        composeTestRule.onNodeWithText("Item 2").performClick()

        pressBack()

        assertDisplayed(OPEN_LIST)
        assertGone("Detail 2")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `with two panes back first returns through links followed inside the pane`() {
        showList()
        composeTestRule.onNodeWithText("Item 1").performClick()
        composeTestRule.onNodeWithText(DETAIL_LINK).performClick()
        assertDisplayed("Detail 2")

        pressBack()

        assertDisplayed("Detail 1")
        assertDisplayed("Item 1")

        pressBack()

        assertDisplayed(OPEN_LIST)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `with two panes the detail's back arrow puts the item away`() {
        showList()
        composeTestRule.onNodeWithText("Item 1").performClick()

        composeTestRule.onNodeWithText(DETAIL_BACK).performClick()

        composeTestRule.onNodeWithTag(DETAIL_PANE_PLACEHOLDER_TEST_TAG).assertIsDisplayed()
        assertGone("Detail 1")
        assertDisplayed("Item 1")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `an item the pane cannot show still opens full screen`() {
        showList()

        composeTestRule.onNodeWithText("Item $NOT_IN_PANE").performClick()

        assertDisplayed("Full screen $NOT_IN_PANE")
    }

    private companion object {
        const val OPEN_LIST = "Open list"
        const val DETAIL_BACK = "Back"
        const val DETAIL_LINK = "Next"
        const val NOT_IN_PANE = 4
        const val SEARCH = "search"
    }
}
