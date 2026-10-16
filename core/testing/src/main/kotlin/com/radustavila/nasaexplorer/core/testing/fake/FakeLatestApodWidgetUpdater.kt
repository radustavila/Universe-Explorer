package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.announcement.LatestApodWidgetUpdater

class FakeLatestApodWidgetUpdater : LatestApodWidgetUpdater {

    var updateCallCount: Int = 0
        private set

    override suspend fun updateAll() {
        updateCallCount++
    }
}
