package com.radustavila.nasaexplorer.core.designsystem.chart

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LineChartTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val data = ChartData((0..10).map { ChartPoint(it.toDouble(), it * 10.0) })

    @Test
    fun `ticks are round numbers covering the range`() {
        assertEquals(listOf(300.0, 350.0, 400.0, 450.0), niceTicks(318.4, 426.07))
        assertEquals(listOf(-0.5, 0.0, 0.5, 1.0, 1.5), niceTicks(-0.48, 1.42))
        assertEquals(listOf(4.0), niceTicks(4.0, 4.0))
    }

    @Test
    fun `the nearest point is found between and beyond the points`() {
        assertEquals(0, data.nearestIndex(-5.0))
        assertEquals(3, data.nearestIndex(3.4))
        assertEquals(4, data.nearestIndex(3.6))
        assertEquals(10, data.nearestIndex(42.0))
    }

    @Test
    fun `the readout shows the latest point, then the touched one`() {
        composeTestRule.setContent {
            LineChart(
                data = data,
                xTicks = listOf(0.0, 5.0, 10.0),
                formatX = { it.toInt().toString() },
                formatY = { it.toInt().toString() },
                readout = { "x=${it.x.toInt()}" },
                description = "test chart",
                modifier = Modifier.width(300.dp),
            )
        }
        composeTestRule.onNodeWithText("x=10").assertExists()

        composeTestRule.onNodeWithTag(LINE_CHART_TEST_TAG).performTouchInput { click(centerLeft) }

        composeTestRule.onNodeWithText("x=0").assertExists()
    }
}
