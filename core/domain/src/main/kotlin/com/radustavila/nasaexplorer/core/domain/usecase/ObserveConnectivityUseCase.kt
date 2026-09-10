package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveConnectivityUseCase @Inject constructor(
    private val networkMonitor: NetworkMonitor,
) {
    operator fun invoke(): Flow<Boolean> = networkMonitor.isOnline
}
