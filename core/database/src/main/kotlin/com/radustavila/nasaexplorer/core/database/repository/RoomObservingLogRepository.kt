package com.radustavila.nasaexplorer.core.database.repository

import com.radustavila.nasaexplorer.core.database.dao.ObservingLogDao
import com.radustavila.nasaexplorer.core.database.entity.ObservingLogEntity
import com.radustavila.nasaexplorer.core.domain.sky.ObservingLogRepository
import com.radustavila.nasaexplorer.core.model.ObservingConditions
import com.radustavila.nasaexplorer.core.model.ObservingLogEntry
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The observing log (F23) in the favourites database, which backups keep. */
class RoomObservingLogRepository @Inject constructor(
    private val dao: ObservingLogDao,
) : ObservingLogRepository {

    override fun observeEntries(): Flow<List<ObservingLogEntry>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toEntry() } }

    override suspend fun save(entry: ObservingLogEntry) = dao.upsert(
        ObservingLogEntity(
            objectId = entry.objectId,
            observedOn = entry.observedOn.toString(),
            conditions = entry.conditions?.name,
            note = entry.note,
            loggedAtEpochMillis = entry.loggedAt.toEpochMilli(),
        ),
    )

    override suspend fun remove(objectId: String) {
        dao.delete(objectId)
    }

    // A row a future version wrote in a shape this one cannot read is skipped, not fatal.
    private fun ObservingLogEntity.toEntry(): ObservingLogEntry? = try {
        ObservingLogEntry(
            objectId = objectId,
            observedOn = LocalDate.parse(observedOn),
            conditions = ObservingConditions.fromName(conditions),
            note = note,
            loggedAt = Instant.ofEpochMilli(loggedAtEpochMillis),
        )
    } catch (_: DateTimeParseException) {
        null
    }
}
