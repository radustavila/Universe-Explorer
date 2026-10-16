package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserPreferencesRepository(
    initialNewApodNotificationsEnabled: Boolean = false,
) : UserPreferencesRepository {

    private val notificationsEnabled = MutableStateFlow(initialNewApodNotificationsEnabled)

    override val newApodNotificationsEnabled: Flow<Boolean> = notificationsEnabled

    val currentNewApodNotificationsEnabled: Boolean get() = notificationsEnabled.value

    override suspend fun setNewApodNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled.value = enabled
    }
}
