package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.announcement.AuroraAlertNotifier
import com.radustavila.nasaexplorer.core.model.AuroraAlert

/** Records what would have been posted instead of touching the system tray. */
class FakeAuroraAlertNotifier : AuroraAlertNotifier {

    var notified: List<AuroraAlert> = emptyList()
        private set

    override suspend fun notifyAuroraAlert(alert: AuroraAlert) {
        notified = notified + alert
    }
}
