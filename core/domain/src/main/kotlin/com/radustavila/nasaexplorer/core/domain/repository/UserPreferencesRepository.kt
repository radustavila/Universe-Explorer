package com.radustavila.nasaexplorer.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** Choices the user made in the app, as opposed to bookkeeping the app keeps for itself. */
interface UserPreferencesRepository {

    /** Whether a notification is posted when a new Astronomy Picture of the Day is published. */
    val newApodNotificationsEnabled: Flow<Boolean>

    suspend fun setNewApodNotificationsEnabled(enabled: Boolean)
}
