package com.radustavila.nasaexplorer.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidget
import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidgetUpdater
import com.radustavila.nasaexplorer.feature.widget.aurora.AuroraWidget
import com.radustavila.nasaexplorer.feature.widget.earth.EarthTodayWidget
import com.radustavila.nasaexplorer.feature.widget.launch.NextLaunchWidget
import com.radustavila.nasaexplorer.feature.widget.sky.SkyTonightWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Re-renders every placed instance of a widget; a no-op when the user has not added it. */
@Singleton
class GlanceHomeWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) : HomeWidgetUpdater {

    override suspend fun update(widget: HomeWidget) = widget.glanceWidget().updateAll(context)
}

internal fun HomeWidget.glanceWidget(): GlanceAppWidget = when (this) {
    HomeWidget.AURORA -> AuroraWidget()
    HomeWidget.NEXT_LAUNCH -> NextLaunchWidget()
    HomeWidget.SKY_TONIGHT -> SkyTonightWidget()
    HomeWidget.EARTH_TODAY -> EarthTodayWidget()
}
