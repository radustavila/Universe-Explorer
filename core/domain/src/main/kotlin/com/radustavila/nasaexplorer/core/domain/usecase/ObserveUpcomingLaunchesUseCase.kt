package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.model.Launch
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveUpcomingLaunchesUseCase @Inject constructor(
    private val launchRepository: LaunchRepository,
) {
    operator fun invoke(): Flow<List<Launch>> = launchRepository.observeUpcomingLaunches()
}
