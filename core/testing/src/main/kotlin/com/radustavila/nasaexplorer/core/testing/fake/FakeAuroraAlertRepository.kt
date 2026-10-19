package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.repository.AuroraAlertRepository
import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeAuroraAlertRepository(initial: AuroraAlertSettings = AuroraAlertSettings()) : AuroraAlertRepository {

    private val state = MutableStateFlow(initial)

    var alertedUntilValue: Instant? = null

    override val settings: Flow<AuroraAlertSettings> = state

    override suspend fun setEnabled(enabled: Boolean) {
        state.update { it.copy(enabled = enabled) }
    }

    override suspend fun setLocation(latitude: Double, longitude: Double) {
        state.update { it.copy(latitude = latitude, longitude = longitude) }
    }

    override suspend fun alertedUntil(): Instant? = alertedUntilValue

    override suspend fun setAlertedUntil(until: Instant?) {
        alertedUntilValue = until
    }
}
