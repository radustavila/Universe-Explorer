package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** The user's aurora alert choices, and which storm they were last alerted about. */
interface AuroraAlertRepository {

    val settings: Flow<AuroraAlertSettings>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setLocation(latitude: Double, longitude: Double)

    /**
     * End of the forecast period the last alert covered. A forecast slot starting before it belongs
     * to the storm already announced; null when no alert is pending.
     */
    suspend fun alertedUntil(): Instant?

    suspend fun setAlertedUntil(until: Instant?)
}
