package com.radustavila.nasaexplorer.core.domain.announcement

import com.radustavila.nasaexplorer.core.model.AuroraAlert

/**
 * Tells the user, outside the app, that aurora may be visible from their place. The domain decides
 * when (see `CheckAuroraAlertUseCase`); the implementation only decides how, and stays silent when
 * the platform does not allow notifications.
 */
interface AuroraAlertNotifier {
    suspend fun notifyAuroraAlert(alert: AuroraAlert)
}
