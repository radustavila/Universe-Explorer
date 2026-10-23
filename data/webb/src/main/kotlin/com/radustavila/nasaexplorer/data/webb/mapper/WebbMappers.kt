package com.radustavila.nasaexplorer.data.webb.mapper

import com.radustavila.nasaexplorer.core.model.WebbSchedule
import com.radustavila.nasaexplorer.core.model.WebbVisit
import com.radustavila.nasaexplorer.data.webb.local.WebbVisitEntity
import com.radustavila.nasaexplorer.data.webb.remote.ScheduledVisitDto
import java.time.Duration
import java.time.Instant

internal fun ScheduledVisitDto.toEntity(fetchedAtMillis: Long) = WebbVisitEntity(
    visitId = visitId,
    startMillis = start.toEpochMilli(),
    durationSeconds = duration.seconds,
    visitType = visitType,
    instrument = instrument,
    targetName = targetName,
    category = category,
    keywords = keywords,
    parallels = parallels.joinToString(WebbVisitEntity.PARALLEL_SEPARATOR),
    fetchedAtMillis = fetchedAtMillis,
)

internal fun WebbVisitEntity.toDomain() = WebbVisit(
    visitId = visitId,
    start = Instant.ofEpochMilli(startMillis),
    duration = Duration.ofSeconds(durationSeconds),
    visitType = visitType,
    instrument = instrument,
    targetName = targetName,
    category = category,
    keywords = keywords,
    parallels = parallels.split(WebbVisitEntity.PARALLEL_SEPARATOR).filter { it.isNotBlank() },
)

/** The stored rows as one schedule, or null when nothing is stored. */
internal fun List<WebbVisitEntity>.toSchedule(): WebbSchedule? = takeIf { it.isNotEmpty() }?.let { rows ->
    WebbSchedule(
        visits = rows.map { it.toDomain() }.sortedBy { it.start },
        fetchedAt = Instant.ofEpochMilli(rows.maxOf { it.fetchedAtMillis }),
    )
}
