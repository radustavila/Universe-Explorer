package com.radustavila.nasaexplorer.feature.widget

import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidget
import com.radustavila.nasaexplorer.feature.widget.aurora.AuroraWidget
import com.radustavila.nasaexplorer.feature.widget.earth.EarthTodayWidget
import com.radustavila.nasaexplorer.feature.widget.launch.NextLaunchWidget
import com.radustavila.nasaexplorer.feature.widget.sky.SkyTonightWidget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GlanceHomeWidgetUpdaterTest {

    @Test
    fun `each widget kind re-renders its own widget`() {
        assertTrue(HomeWidget.AURORA.glanceWidget() is AuroraWidget)
        assertTrue(HomeWidget.NEXT_LAUNCH.glanceWidget() is NextLaunchWidget)
        assertTrue(HomeWidget.SKY_TONIGHT.glanceWidget() is SkyTonightWidget)
        assertTrue(HomeWidget.EARTH_TODAY.glanceWidget() is EarthTodayWidget)
    }

    @Test
    fun `updating a widget nobody placed does nothing`() = runTest {
        val updater = GlanceHomeWidgetUpdater(testContext)

        // Syncs call this whether or not the widget is on the home screen; it must not fail them.
        HomeWidget.entries.forEach { updater.update(it) }
    }
}
