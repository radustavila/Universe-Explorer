package com.radustavila.nasaexplorer.feature.settings.today

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.height
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveTodayCardsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetTodayCardsUseCase
import com.radustavila.nasaexplorer.core.model.TodayCardId
import com.radustavila.nasaexplorer.core.model.TodayCardId.EARTH_EVENTS
import com.radustavila.nasaexplorer.core.model.TodayCardId.EARTH_TODAY
import com.radustavila.nasaexplorer.core.model.TodayCardId.ISS_NOW
import com.radustavila.nasaexplorer.core.model.TodayCardId.NASA_NEWS
import com.radustavila.nasaexplorer.core.model.TodayCardId.NEXT_LAUNCHES
import com.radustavila.nasaexplorer.core.testing.fake.FakeTodayCardsRepository
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h900dp")
class CustomizeTodayScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val repository = FakeTodayCardsRepository()

    private fun setScreen() {
        val viewModel = CustomizeTodayViewModel(ObserveTodayCardsUseCase(repository), SetTodayCardsUseCase(repository))
        composeTestRule.setContent { CustomizeTodayScreen(onBackClick = {}, viewModel = viewModel) }
    }

    @Test
    fun `toggling a card shows or hides it on Today`() {
        setScreen()

        composeTestRule.onNodeWithTag(customizeTodaySwitchTestTag(ISS_NOW)).assertIsOff().performClick()
        composeTestRule.onNodeWithTag(customizeTodaySwitchTestTag(ISS_NOW)).assertIsOn()
        composeTestRule.onNodeWithTag(customizeTodaySwitchTestTag(EARTH_TODAY)).performClick()

        assertEquals(listOf(EARTH_EVENTS, NEXT_LAUNCHES, NASA_NEWS, ISS_NOW), repository.currentTodayCards)
        composeTestRule.onNodeWithTag(customizeTodaySwitchTestTag(EARTH_TODAY)).assertIsOff()
    }

    @Test
    fun `dragging a handle down past the next card swaps them`() {
        setScreen()
        val rowHeight = composeTestRule.onNodeWithTag(customizeTodayRowTestTag(EARTH_TODAY)).getBoundsInRoot().height

        composeTestRule.onNodeWithTag(customizeTodayHandleTestTag(EARTH_TODAY)).performTouchInput {
            down(center)
            // Past the touch slop, then a little over one row: Earth today trades places once.
            moveBy(Offset(0f, viewConfiguration.touchSlop + 1f))
            moveBy(Offset(0f, rowHeight.toPx() * 1.1f))
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf(EARTH_EVENTS, EARTH_TODAY, NEXT_LAUNCHES, NASA_NEWS), repository.currentTodayCards)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `screen readers move cards with actions, and reset restores the default`() {
        setScreen()

        composeTestRule.onNodeWithTag(customizeTodayRowTestTag(NEXT_LAUNCHES))
            .performCustomAccessibilityActionWithLabel("Move up")
        assertEquals(listOf(EARTH_TODAY, NEXT_LAUNCHES, EARTH_EVENTS, NASA_NEWS), repository.currentTodayCards)

        composeTestRule.onNodeWithTag(CUSTOMIZE_TODAY_RESET_TEST_TAG).performClick()
        assertEquals(TodayCardId.DEFAULT, repository.currentTodayCards)
    }
}
