package com.radustavila.nasaexplorer.feature.events.next

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.data.TestSkyEvents
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import com.radustavila.nasaexplorer.feature.events.SkyEventsFixture
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NextInTheSkyCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `lists the next events, each opening itself, the card the calendar`() {
        var opened = 0
        var event: String? = null
        composeTestRule.setContent {
            NasaTheme {
                NextInTheSkyCardContent(
                    uiState = NextInTheSkyUiState(
                        isLoaded = true,
                        events = listOf(TestSkyEvents.perseids(), TestSkyEvents.partialLunarEclipse()),
                        hasPlace = true,
                    ),
                    onOpenCalendar = { opened++ },
                    onEventClick = { event = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Next in the sky").assertIsDisplayed()
        composeTestRule.onNodeWithText("Partial lunar eclipse").performClick()
        assertEquals(TestSkyEvents.partialLunarEclipse().id, event)
        composeTestRule.onNodeWithContentDescription("Open the sky events calendar").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `the view model skips quarter Moons and knows whether there is a place`() = runTest {
        val fixture = SkyEventsFixture(location = TestSky.location(), now = Instant.parse("2026-08-12T18:00:00Z"))
        NextInTheSkyViewModel(fixture.observeUpcoming, fixture.observeLocation).uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.isLoaded)
            assertTrue(state.hasPlace)
            assertEquals(
                listOf(
                    TestSkyEvents.perseids(),
                    TestSkyEvents.venusElongation(),
                    TestSkyEvents.partialLunarEclipse(),
                ).map { it.id },
                state.events.map { it.id },
            )
        }
    }
}
