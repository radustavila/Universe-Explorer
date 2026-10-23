package com.radustavila.nasaexplorer.data.comets.remote

import com.radustavila.nasaexplorer.data.comets.remote.dto.CometEphemerisRowDto
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Reads a Horizons observer table requested as CSV with quantities 1, 9, 19 and 20 in degrees
 * (columns abbreviated here):
 *
 * ```
 *  Date__(UT)__HR:MN, , , R.A._(ICRF), DEC_(ICRF),    T-mag,  N-mag,           r, …,        delta, …
 * ****…
 * $$SOE
 *  2026-Oct-23 00:00, , ,   292.54598,   59.85362,   14.152,   n.a.,   2.0745086, …,   1.69719869, …
 * ```
 *
 * Columns are found by their header, so a column Horizons adds does not shift the others. Rows that
 * cannot be read are skipped; null when there is no table at all (an unknown designation, a list of
 * matches to choose from, a changed layout).
 */
internal object HorizonsCometParser {

    fun parse(result: String): List<CometEphemerisRowDto>? {
        val parts = result.split(START_OF_EPHEMERIS, limit = 2)
        val indices = parts.takeIf { it.size == 2 }?.let { columnIndices(it[0]) } ?: return null
        return parts[1].substringBefore(END_OF_EPHEMERIS)
            .lineSequence()
            .filter(String::isNotBlank)
            .mapNotNull { line -> parseRow(line.split(',').map(String::trim), indices) }
            .toList()
    }

    /** Where each column is, from the header line above `$$SOE`; null when one is missing. */
    private fun columnIndices(header: String): ColumnIndices? {
        val columns = header.lineSequence()
            .lastOrNull { it.contains(DATE_COLUMN) }
            ?.split(',')
            ?.map(String::trim)
            ?: return null
        return ColumnIndices(
            date = columns.indexOfFirst { it.startsWith(DATE_COLUMN) },
            rightAscension = columns.indexOf(RA_COLUMN),
            declination = columns.indexOf(DEC_COLUMN),
            magnitude = columns.indexOf(MAGNITUDE_COLUMN),
            sunDistance = columns.indexOf(SUN_DISTANCE_COLUMN),
            earthDistance = columns.indexOf(EARTH_DISTANCE_COLUMN),
        ).takeIf { it.isComplete }
    }

    private fun parseRow(values: List<String>, indices: ColumnIndices): CometEphemerisRowDto? {
        fun number(index: Int) = values.getOrNull(index)?.toDoubleOrNull()?.takeIf(Double::isFinite)
        fun distance(index: Int) = number(index)?.takeIf { it > 0 }
        val epoch = values.getOrNull(indices.date)?.let(::parseEpoch) ?: return null
        val position = number(indices.rightAscension)?.let { ra -> number(indices.declination)?.let { ra to it } }
        val distances = distance(indices.sunDistance)?.let { sun -> distance(indices.earthDistance)?.let { sun to it } }
        return if (position != null && distances != null) {
            CometEphemerisRowDto(
                epochMillis = epoch,
                rightAscensionDegrees = position.first,
                declinationDegrees = position.second,
                totalMagnitude = number(indices.magnitude),
                sunDistanceAu = distances.first,
                earthDistanceAu = distances.second,
            )
        } else {
            null
        }
    }

    private fun parseEpoch(text: String): Long? = try {
        LocalDateTime.parse(text, EPOCH_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

    private data class ColumnIndices(
        val date: Int,
        val rightAscension: Int,
        val declination: Int,
        val magnitude: Int,
        val sunDistance: Int,
        val earthDistance: Int,
    ) {
        val isComplete: Boolean
            get() = listOf(date, rightAscension, declination, magnitude, sunDistance, earthDistance).none { it < 0 }
    }

    private const val START_OF_EPHEMERIS = "\$\$SOE"
    private const val END_OF_EPHEMERIS = "\$\$EOE"
    private const val DATE_COLUMN = "Date__(UT)__HR:MN"
    private const val RA_COLUMN = "R.A._(ICRF)"
    private const val DEC_COLUMN = "DEC_(ICRF)"
    private const val MAGNITUDE_COLUMN = "T-mag"
    private const val SUN_DISTANCE_COLUMN = "r"
    private const val EARTH_DISTANCE_COLUMN = "delta"
    private val EPOCH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm", Locale.ENGLISH)
}
