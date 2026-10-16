package com.radustavila.nasaexplorer.core.domain.announcement

import com.radustavila.nasaexplorer.core.model.Apod

/**
 * Tells the user, outside the app, that a new entry has been published. The domain decides *whether*
 * to announce; the implementation only decides *how* (and may stay silent when the platform does not
 * allow it, e.g. the notification permission was revoked).
 */
interface NewApodNotifier {
    suspend fun notifyNewApod(apod: Apod)
}
