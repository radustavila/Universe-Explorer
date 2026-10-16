package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSyncMetadataRepository(
    initialLastSync: Instant? = null,
    initialOldestRequested: LocalDate? = null,
    initialLastAnnounced: LocalDate? = null,
) : SyncMetadataRepository {

    private val lastSync = MutableStateFlow(initialLastSync)
    private val oldestRequested = MutableStateFlow(initialOldestRequested)
    private val lastAnnounced = MutableStateFlow(initialLastAnnounced)

    override val lastSuccessfulSync: Flow<Instant?> = lastSync
    override val oldestRequestedDate: Flow<LocalDate?> = oldestRequested
    override val lastAnnouncedApodDate: Flow<LocalDate?> = lastAnnounced

    val currentLastSync: Instant? get() = lastSync.value
    val currentOldestRequested: LocalDate? get() = oldestRequested.value
    val currentLastAnnounced: LocalDate? get() = lastAnnounced.value

    override suspend fun setLastSuccessfulSync(instant: Instant) {
        lastSync.value = instant
    }

    override suspend fun setOldestRequestedDate(date: LocalDate) {
        oldestRequested.value = date
    }

    override suspend fun setLastAnnouncedApodDate(date: LocalDate) {
        lastAnnounced.value = date
    }
}
