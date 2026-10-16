package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveNewApodNotificationsEnabledUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = userPreferencesRepository.newApodNotificationsEnabled
}
