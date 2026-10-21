package com.radustavila.nasaexplorer.feature.widget.launch

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import com.radustavila.nasaexplorer.feature.widget.WidgetSizes
import com.radustavila.nasaexplorer.feature.widget.testContext
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NextLaunchWidgetContentTest {

    private val state = NextLaunchWidgetState(
        mission = "Bandwagon 5",
        vehicle = "SpaceX · Falcon 9 Block 5",
        net = "13:20",
        countdown = "in 3 h 20 m",
        liveCountdownTo = Instant.parse("2026-10-19T13:20:00Z"),
        status = "Go",
        contentDescription = "Bandwagon 5, in 3 h 20 m",
    )

    @Test
    fun `shows the mission, who flies it, the countdown and the target time`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSizes.Wide)
        setContext(testContext)
        provideComposable {
            NextLaunchWidgetContent(state, title = "Next launch", openIntent = null, liveCountdown = false)
        }

        onNode(hasText("Next launch")).assertExists()
        onNode(hasText("Go")).assertExists()
        onNode(hasText("Bandwagon 5")).assertExists()
        onNode(hasText("SpaceX · Falcon 9 Block 5")).assertExists()
        onNode(hasText("in 3 h 20 m")).assertExists()
        onNode(hasText("13:20")).assertExists()
        onNode(hasContentDescriptionEqualTo("Bandwagon 5, in 3 h 20 m")).assertExists()
    }

    @Test
    fun `the live countdown replaces the minute text`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSizes.Wide)
        setContext(testContext)
        provideComposable { NextLaunchWidgetContent(state, title = "Next launch", openIntent = null) }

        onNode(hasText("in 3 h 20 m")).assertDoesNotExist()
        onNode(hasText("13:20")).assertExists()
    }
}
