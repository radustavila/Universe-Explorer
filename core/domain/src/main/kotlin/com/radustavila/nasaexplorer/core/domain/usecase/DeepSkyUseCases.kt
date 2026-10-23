package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.sky.DeepSkyCatalogRepository
import com.radustavila.nasaexplorer.core.domain.sky.ObservingLogRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyPositionCalculator
import com.radustavila.nasaexplorer.core.domain.stargazing.DarkSkyCalculator
import com.radustavila.nasaexplorer.core.model.DeepSkyObject
import com.radustavila.nasaexplorer.core.model.DeepSkyTonight
import com.radustavila.nasaexplorer.core.model.DeepSkyVisibility
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.ObservingConditions
import com.radustavila.nasaexplorer.core.model.ObservingLogEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The bundled deep-sky catalogue (F23), read off the main thread. */
class LoadDeepSkyCatalogUseCase @Inject constructor(
    private val repository: DeepSkyCatalogRepository,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(): List<DeepSkyObject> = withContext(ioDispatcher) { repository.objects() }
}

/** The observing log's marks by object id. */
class ObserveObservingLogUseCase @Inject constructor(private val repository: ObservingLogRepository) {
    operator fun invoke(): Flow<Map<String, ObservingLogEntry>> =
        repository.observeEntries().map { entries -> entries.associateBy { it.objectId } }
}

/** "Mark as seen", or edits an earlier mark: the date it was seen, the sky and a note. */
class MarkDeepSkySeenUseCase @Inject constructor(
    private val repository: ObservingLogRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(
        objectId: String,
        observedOn: LocalDate,
        conditions: ObservingConditions?,
        note: String?,
    ) = repository.save(
        ObservingLogEntry(
            objectId = objectId,
            observedOn = observedOn,
            conditions = conditions,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            loggedAt = timeProvider.now(),
        ),
    )
}

class RemoveDeepSkySeenUseCase @Inject constructor(private val repository: ObservingLogRepository) {
    suspend operator fun invoke(objectId: String) = repository.remove(objectId)
}

/**
 * The observing log as CSV (RFC 4180, a header row, oldest observation first), for Settings'
 * export. Column names and values are data, not words for the screen: types and conditions are
 * their stable names.
 */
class ExportObservingLogUseCase @Inject constructor(
    private val repository: ObservingLogRepository,
    private val catalog: DeepSkyCatalogRepository,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(): String = withContext(ioDispatcher) {
        val objects = catalog.objects().associateBy { it.id }
        val entries = repository.observeEntries().first().sortedWith(compareBy({ it.observedOn }, { it.objectId }))
        buildString {
            appendRow(HEADER)
            entries.forEach { entry ->
                val item = objects[entry.objectId]
                appendRow(
                    listOf(
                        entry.objectId,
                        item?.designation.orEmpty(),
                        item?.messier?.let { "M$it" }.orEmpty(),
                        item?.caldwell?.let { "C$it" }.orEmpty(),
                        item?.commonName.orEmpty(),
                        item?.type?.name.orEmpty(),
                        item?.constellation.orEmpty(),
                        entry.observedOn.toString(),
                        entry.conditions?.name.orEmpty(),
                        entry.note.orEmpty(),
                    ),
                )
            }
        }
    }

    private fun StringBuilder.appendRow(fields: List<String>) {
        fields.joinTo(this, separator = ",") { field ->
            if (field.any { it in NEEDS_QUOTES }) {
                "\"" + field.replace("\"", "\"\"") + "\""
            } else {
                field
            }
        }
        append("\r\n")
    }

    companion object {
        private val NEEDS_QUOTES = charArrayOf(',', '"', '\n', '\r')

        val HEADER = listOf(
            "object",
            "designation",
            "messier",
            "caldwell",
            "common_name",
            "type",
            "constellation",
            "observed_on",
            "conditions",
            "note",
        )
    }
}

/**
 * Which deep-sky objects climb at least [DeepSkyTonight.MIN_ALTITUDE_DEGREES] during tonight's
 * dark hours at [observer] (astronomical night, or nautical where it never gets darker), from F11's
 * darkness, recomputed every [RECOMPUTE_INTERVAL] so it rolls over to the next night. Emits null
 * while tonight never gets dark. Offline: nothing here touches the network.
 */
class ObserveDeepSkyTonightUseCase @Inject constructor(
    private val darkSky: DarkSkyCalculator,
    private val positions: SkyPositionCalculator,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(observer: GeoCoordinate, objects: List<DeepSkyObject>): Flow<DeepSkyTonight?> = flow {
        while (true) {
            emit(tonight(observer, objects, timeProvider.now()))
            delay(RECOMPUTE_INTERVAL.toMillis())
        }
    }.flowOn(computeDispatcher)

    fun tonight(observer: GeoCoordinate, objects: List<DeepSkyObject>, now: Instant): DeepSkyTonight? {
        val night = darkSky.nights(observer, now, count = 1).firstOrNull()
        val start = night?.darkStart
        val end = night?.darkEnd
        val isDark = start != null && end != null && end.isAfter(start)
        return if (isDark) visibleBetween(observer, objects, checkNotNull(start), checkNotNull(end)) else null
    }

    private fun visibleBetween(
        observer: GeoCoordinate,
        objects: List<DeepSkyObject>,
        start: Instant,
        end: Instant,
    ): DeepSkyTonight {
        val times = generateSequence(start) { it.plus(STEP) }.takeWhile { !it.isAfter(end) }.toList() + end
        val visible = objects.mapNotNull { item ->
            times.map { time -> time to positions.horizontal(item.position, observer, time) }
                .maxBy { (_, horizontal) -> horizontal.altitudeDegrees }
                .takeIf { (_, horizontal) -> horizontal.altitudeDegrees >= DeepSkyTonight.MIN_ALTITUDE_DEGREES }
                ?.let { (time, horizontal) ->
                    DeepSkyVisibility(item.id, time, horizontal.altitudeDegrees, horizontal.azimuthDegrees)
                }
        }
        return DeepSkyTonight(start, end, visible.associateBy { it.objectId })
    }

    companion object {
        val STEP: Duration = Duration.ofMinutes(20)
        val RECOMPUTE_INTERVAL: Duration = Duration.ofMinutes(15)
    }
}
