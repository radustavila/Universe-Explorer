package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidget
import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidgetUpdater

/** Records every widget it was asked to re-render, in call order. */
class FakeHomeWidgetUpdater : HomeWidgetUpdater {

    val updated = mutableListOf<HomeWidget>()

    override suspend fun update(widget: HomeWidget) {
        updated += widget
    }
}
