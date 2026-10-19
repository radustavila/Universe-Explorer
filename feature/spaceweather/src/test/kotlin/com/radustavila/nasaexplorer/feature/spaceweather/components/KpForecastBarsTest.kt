package com.radustavila.nasaexplorer.feature.spaceweather.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.KpKind
import com.radustavila.nasaexplorer.core.model.KpReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h900dp")
// Real text measurement: the legacy mode measures every label a few pixels wide.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KpForecastBarsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** [count] consecutive slots from [from] (UTC). Monday 2026-10-19 21:00 is the last slot of Monday. */
    private fun slots(count: Int, from: String) = List(count) { index ->
        KpReading(Instant.parse(from).plusSeconds(index * KpReading.SLOT.seconds), kp = 3.0, kind = KpKind.PREDICTED)
    }

    private fun setForecast(forecast: List<KpReading>) {
        composeTestRule.setContent {
            KpForecastBars(
                forecast = forecast,
                threshold = null,
                modifier = Modifier.width(360.dp),
                zone = ZoneOffset.UTC,
            )
        }
    }

    @Test
    fun `a one-slot today drops its label instead of running into tomorrow's`() {
        setForecast(slots(24, from = "2026-10-19T21:00:00Z"))

        composeTestRule.onNodeWithText("Today").assertIsNotDisplayed()
        val tue = composeTestRule.onNodeWithText("Tue").assertIsDisplayed().getUnclippedBoundsInRoot()
        val wed = composeTestRule.onNodeWithText("Wed").assertIsDisplayed().getUnclippedBoundsInRoot()
        val thu = composeTestRule.onNodeWithText("Thu").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Tue and Wed overlap", tue.right < wed.left)
        assertTrue("Wed and Thu overlap", wed.right < thu.left)
    }

    @Test
    fun `today keeps its label when it has room`() {
        setForecast(slots(24, from = "2026-10-19T03:00:00Z"))

        val today = composeTestRule.onNodeWithText("Today").assertIsDisplayed().getUnclippedBoundsInRoot()
        val tue = composeTestRule.onNodeWithText("Tue").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Today and Tue overlap", today.right < tue.left)
    }

    @Test
    fun `labels never overlap whatever slot the forecast starts in`() {
        for (hour in 0..21 step 3) {
            val forecast = slots(24, from = "2026-10-19T%02d:00:00Z".format(hour))
            val days = forecast.groupBy { it.start.atZone(ZoneOffset.UTC).toLocalDate() }.values
            val starts = days.runningFold(0) { first, day -> first + day.size }.dropLast(1)
            // Labels about as wide as "Today" on a 360-px row with 15-px slots.
            val positions = dayLabelPositions(starts.map { it * 15 }, List(starts.size) { 32 }, rowWidth = 360, minGap = 4)
            val kept = positions.withIndex().mapNotNull { (index, x) -> x?.let { it..(it + 32) } }
            kept.zipWithNext().forEach { (a, b) -> assertTrue("$hour h: $a overlaps $b", a.last + 4 <= b.first) }
            kept.forEach { assertTrue("$hour h: $it runs past the row", it.last <= 360) }
        }
    }

    @Test
    fun `dayLabelPositions keeps labels that fit and drops the ones that don't`() {
        assertEquals(
            listOf(null, 15, 135, null),
            dayLabelPositions(starts = listOf(0, 15, 135, 345), widths = listOf(32, 30, 30, 30), rowWidth = 360, minGap = 4),
        )
    }
}
