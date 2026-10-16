package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.announcement.NewApodNotifier
import com.radustavila.nasaexplorer.core.model.Apod

/** Records what would have been posted instead of touching the system tray. */
class FakeNewApodNotifier : NewApodNotifier {

    var notified: List<Apod> = emptyList()
        private set

    override suspend fun notifyNewApod(apod: Apod) {
        notified = notified + apod
    }
}
