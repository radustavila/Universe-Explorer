package com.radustavila.nasaexplorer.data.webb.remote

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Reads STScI's weekly JWST schedule, a fixed-width text table with the columns VISIT ID, PCS
 * MODE, VISIT TYPE, SCHEDULED START TIME, DURATION, SCIENCE INSTRUMENT AND MODE, TARGET NAME,
 * CATEGORY and KEYWORDS (see the recorded files under `src/test/resources/fixtures/webb/`):
 *
 * ```
 * VISIT ID    VISIT TYPE            SCHEDULED START TIME  DURATION     SCIENCE INSTRUMENT AND MODE
 * 1366:4:1    PRIME TARGETED FIXED  2022-07-10T15:04:42Z  00/08:33:57  NIRSpec Bright Object TSO
 *             COORDINATED PARALLEL                                     NIRCam Imaging
 * 12551:58:1  PARALLEL DARK CALIB.  ^ATTACHED TO PRIME^                NIRSpec Dark
 * ```
 *
 * Prime visits have an id, a start time and a duration. Two kinds of rows continue the visit above:
 * coordinated parallels (no id, a `PARALLEL` visit type) and attached parallels (an id and
 * `^ATTACHED TO PRIME^` for a start); both add their instrument to the prime's parallels. A row
 * with only a target name is a further target of the visit above, kept only if it had none.
 *
 * Column drift is tolerated: columns are found by their header titles, not fixed offsets, and each
 * value goes to the column whose start is nearest, so a few characters' shift, a reordered or a
 * missing optional column still read. Times and durations are recognised by their shape even when
 * a long value leaves a single space before them. Rows that do not read are skipped.
 */
object WebbScheduleParser {

    fun parse(text: String): List<ScheduledVisitDto> {
        val visits = mutableListOf<VisitBuilder>()
        var columns: List<Pair<Column, Int>>? = null
        text.lineSequence().forEach { line ->
            val header = headerColumns(line)
            when {
                header != null -> columns = header
                line.isBlank() || DIVIDER.matches(line) -> Unit
                else -> columns?.let { read(fields(line, it), visits) }
            }
        }
        return visits.map(VisitBuilder::build)
    }

    private fun read(fields: Map<Column, String>, visits: MutableList<VisitBuilder>) {
        val visitId = fields[Column.VISIT_ID]?.takeIf { VISIT_ID.matches(it) }
        val start = fields[Column.START]
        val instrument = fields[Column.INSTRUMENT]
        val isParallel = start == ATTACHED_TO_PRIME ||
            (visitId == null && fields[Column.VISIT_TYPE]?.contains(PARALLEL) == true)
        val prime = visits.lastOrNull()
        when {
            visitId != null && start != null && !isParallel -> primeVisit(visitId, start, fields)?.let(visits::add)
            isParallel && instrument != null -> prime?.addParallel(instrument)
            visitId == null && prime != null && prime.targetName == null -> prime.targetName = fields[Column.TARGET]
        }
    }

    private fun primeVisit(visitId: String, start: String, fields: Map<Column, String>): VisitBuilder? {
        val startTime = parseInstant(start)
        val duration = fields[Column.DURATION]?.let(::parseDuration)
        if (startTime == null || duration == null) return null
        return VisitBuilder(
            visitId = visitId,
            start = startTime,
            duration = duration,
            visitType = fields[Column.VISIT_TYPE].orEmpty(),
            instrument = fields[Column.INSTRUMENT].orEmpty(),
            targetName = fields[Column.TARGET],
            category = fields[Column.CATEGORY],
            keywords = fields[Column.KEYWORDS],
        )
    }

    /** The columns a header line names, with where each title starts; null for any other line. */
    private fun headerColumns(line: String): List<Pair<Column, Int>>? {
        val found = Column.entries.mapNotNull { column ->
            line.indexOf(column.title).takeIf { it >= 0 }?.let { column to it }
        }
        val required = setOf(Column.VISIT_ID, Column.START, Column.DURATION)
        return found.takeIf { columns -> columns.map { it.first }.containsAll(required) }?.sortedBy { it.second }
    }

    /** The line's values by column: each run of text separated by two or more spaces, at its nearest column. */
    private fun fields(line: String, columns: List<Pair<Column, Int>>): Map<Column, String> {
        val fields = mutableMapOf<Column, String>()
        values(line).forEach { (offset, value) ->
            val column = columns.minBy { (_, start) -> kotlin.math.abs(start - offset) }.first
            fields[column] = fields[column]?.let { "$it $value" } ?: value
        }
        return fields
    }

    /** Every value with its offset; a time or duration glued to its neighbours by one space is split off. */
    private fun values(line: String): List<Pair<Int, String>> = VALUE.findAll(line).flatMap { run ->
        val starts = cuts(run.value)
        starts.mapIndexed { index, from ->
            val until = starts.getOrNull(index + 1) ?: run.value.length
            run.range.first + from to run.value.substring(from, until).trim()
        }
    }.filter { it.second.isNotEmpty() }.toList()

    /** Where [run] splits into values: its start, and either side of each time-like part between words. */
    private fun cuts(run: String): List<Int> {
        val cuts = sortedSetOf(0)
        TIME_LIKE.findAll(run).forEach { match ->
            val before = match.range.first
            val after = match.range.last + 1
            if (before == 0 || run[before - 1] == ' ') cuts += before
            if (after < run.length && run[after] == ' ') cuts += after + 1
        }
        return cuts.toList()
    }

    private fun parseInstant(text: String): Instant? = try {
        Instant.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }

    /** "DD/HH:MM:SS" as a duration; null for anything else. */
    private fun parseDuration(text: String): Duration? {
        val parts = DURATION.matchEntire(text)?.groupValues?.drop(1) ?: return null
        return parts.zip(DURATION_UNITS).fold(Duration.ZERO) { total, (value, unit) ->
            total.plus(value.toLong(), unit)
        }
    }

    private enum class Column(val title: String) {
        VISIT_ID("VISIT ID"),
        PCS_MODE("PCS MODE"),
        VISIT_TYPE("VISIT TYPE"),
        START("SCHEDULED START TIME"),
        DURATION("DURATION"),
        INSTRUMENT("SCIENCE INSTRUMENT AND MODE"),
        TARGET("TARGET NAME"),
        CATEGORY("CATEGORY"),
        KEYWORDS("KEYWORDS"),
    }

    private class VisitBuilder(
        val visitId: String,
        val start: Instant,
        val duration: Duration,
        val visitType: String,
        val instrument: String,
        var targetName: String?,
        val category: String?,
        val keywords: String?,
    ) {
        private val parallels = linkedSetOf<String>()

        fun addParallel(instrument: String) {
            parallels += instrument
        }

        fun build() = ScheduledVisitDto(
            visitId = visitId,
            start = start,
            duration = duration,
            visitType = visitType,
            instrument = instrument,
            targetName = targetName,
            category = category,
            keywords = keywords,
            parallels = parallels.toList(),
        )
    }

    private const val ATTACHED_TO_PRIME = "^ATTACHED TO PRIME^"
    private const val PARALLEL = "PARALLEL"
    private val DIVIDER = Regex("""[-\s]+""")
    private val VISIT_ID = Regex("""\d+:\d+:\d+""")
    private val DURATION = Regex("""(\d+)/(\d{1,2}):(\d{2}):(\d{2})""")
    private val DURATION_UNITS = listOf(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS)

    /** Runs of text in which words are separated by single spaces. */
    private val VALUE = Regex("""\S+(?: \S+)*""")

    /** A start time, a duration or the attached-parallel marker inside a run of text. */
    private val TIME_LIKE = Regex(
        listOf("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""", """\d+/\d{1,2}:\d{2}:\d{2}""", """\^ATTACHED TO PRIME\^""")
            .joinToString("|"),
    )
}
