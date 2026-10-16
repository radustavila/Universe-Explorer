package com.radustavila.nasaexplorer.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.radustavila.nasaexplorer.core.domain.announcement.LatestApodWidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Re-renders every placed instance; a no-op when the user has not added the widget. */
@Singleton
class GlanceLatestApodWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) : LatestApodWidgetUpdater {

    override suspend fun updateAll() = LatestApodWidget().updateAll(context)
}
