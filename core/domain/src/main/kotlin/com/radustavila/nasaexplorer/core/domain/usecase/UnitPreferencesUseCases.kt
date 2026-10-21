package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.TimeFormat
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The units every screen formats distances, speeds and clock times in. */
class ObserveUnitPreferencesUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    operator fun invoke(): Flow<UnitPreferences> = userPreferencesRepository.unitPreferences
}

class SetMeasurementSystemUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(system: MeasurementSystem) = userPreferencesRepository.setMeasurementSystem(system)
}

class SetTimeFormatUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(format: TimeFormat) = userPreferencesRepository.setTimeFormat(format)
}
