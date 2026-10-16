package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import javax.inject.Inject

class SetNewApodNotificationsEnabledUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(enabled: Boolean) =
        userPreferencesRepository.setNewApodNotificationsEnabled(enabled)
}
