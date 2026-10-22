package com.radustavila.nasaexplorer.feature.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetMessageTest {

    @Test
    fun `the empty state is read out as it is shown`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSizes.Small)
        setContext(testContext)
        val message = testResources.getString(R.string.aurora_widget_empty)
        provideComposable { WidgetMessage(WidgetMessageState(message, openIntent = null)) }

        onNode(hasText(message)).assertExists()
        onNode(hasContentDescriptionEqualTo(message)).assertExists()
    }
}
