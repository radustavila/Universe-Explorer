package com.radustavila.nasaexplorer.navigation

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveTodayCardsUseCase
import com.radustavila.nasaexplorer.core.model.TodayCardId
import com.radustavila.nasaexplorer.core.testing.fake.FakeTodayCardsRepository
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TodayCardsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `every card the user can pick has a composable`() {
        val navController = NavHostController(RuntimeEnvironment.getApplication())

        assertEquals(TodayCardId.entries.toSet(), todayCards(navController).keys)
    }

    @Test
    fun `search stays first, then the user's cards in their order, skipping unknown ones`() {
        val repository = FakeTodayCardsRepository(
            initial = listOf(TodayCardId.ISS_NOW, TodayCardId.SPACE_WEATHER, TodayCardId.EARTH_TODAY),
        )
        // Space weather has no composable here: it is skipped, not an error.
        val cards: Map<TodayCardId, @Composable () -> Unit> = mapOf(
            TodayCardId.EARTH_TODAY to { Text("Earth card") },
            TodayCardId.ISS_NOW to { Text("ISS card") },
            TodayCardId.NEXT_LAUNCHES to { Text("Launches card") },
        )
        var customizeClicks = 0
        val viewModel = TodayCardsViewModel(ObserveTodayCardsUseCase(repository))
        composeTestRule.setContent {
            NasaTheme {
                TodayHeader(
                    cards = cards,
                    onSearchClick = {},
                    onCustomizeClick = { customizeClicks++ },
                    viewModel = viewModel,
                )
            }
        }

        val search = composeTestRule.onNodeWithText("Search", substring = true).getBoundsInRoot().top
        val iss = composeTestRule.onNodeWithText("ISS card").getBoundsInRoot().top
        val earth = composeTestRule.onNodeWithText("Earth card").getBoundsInRoot().top
        assertTrue(search < iss && iss < earth)
        composeTestRule.onNodeWithText("Launches card").assertDoesNotExist()

        composeTestRule.onNodeWithTag(TODAY_CUSTOMIZE_BUTTON_TEST_TAG).performClick()
        assertEquals(1, customizeClicks)
    }
}
