package com.radustavila.nasaexplorer.core.domain.announcement

/** Re-renders every home screen widget that shows the latest entry from the local cache. */
interface LatestApodWidgetUpdater {
    suspend fun updateAll()
}
