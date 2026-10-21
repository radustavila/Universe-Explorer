package com.radustavila.nasaexplorer.feature.widget.aurora

import android.content.Intent
import android.net.Uri
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import com.radustavila.nasaexplorer.core.model.SpaceWeatherDeepLink
import com.radustavila.nasaexplorer.feature.widget.WidgetSizes
import com.radustavila.nasaexplorer.feature.widget.testContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuroraWidgetContentTest {

    private val state = AuroraWidgetState(
        kp = "4.3",
        level = 4,
        activity = "Active",
        peak = "Peak Kp 6 · 18:00",
        local = "40% aurora chance here",
        updated = "Kp at 14:20",
        isStale = false,
        contentDescription = "Kp index 4.3, Active",
    )

    @Test
    fun `the small widget shows the reading and its time only`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSizes.Small)
        setContext(testContext)
        provideComposable { AuroraWidgetContent(state, title = "Aurora · Kp", openIntent = null) }

        onNode(hasText("4.3")).assertExists()
        onNode(hasText("Active")).assertExists()
        onNode(hasText("Kp at 14:20")).assertExists()
        onNode(hasText("Peak Kp 6 · 18:00")).assertDoesNotExist()
        onNode(hasText("40% aurora chance here")).assertDoesNotExist()
    }

    @Test
    fun `a 2x2 square has room for the peak and the local outlook`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSizes.Square)
        setContext(testContext)
        provideComposable { AuroraWidgetContent(state, title = "Aurora · Kp", openIntent = null) }

        onNode(hasText("Peak Kp 6 · 18:00")).assertExists()
        onNode(hasText("40% aurora chance here")).assertExists()
    }

    @Test
    fun `the large widget adds the peak and the local outlook`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSizes.Large)
        setContext(testContext)
        provideComposable { AuroraWidgetContent(state, title = "Aurora · Kp", openIntent = null) }

        onNode(hasText("Peak Kp 6 · 18:00")).assertExists()
        onNode(hasText("40% aurora chance here")).assertExists()
    }

    @Test
    fun `the whole widget is described and opens the app`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSizes.Wide)
        setContext(testContext)
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(SpaceWeatherDeepLink.URI))
        provideComposable { AuroraWidgetContent(state, title = "Aurora · Kp", openIntent = open) }

        onNode(hasContentDescriptionEqualTo("Kp index 4.3, Active")).assert(hasClickAction())
    }
}
