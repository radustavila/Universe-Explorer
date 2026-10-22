package com.radustavila.nasaexplorer.data.dsn.remote

import com.radustavila.nasaexplorer.data.dsn.remote.dto.HorizonsRangeDto
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Reads the first row of a Horizons observer ephemeris with quantity 20 ("delta" range in AU and
 * "deldot" range rate in km/s), e.g.
 *
 * ```
 * Target body name: Voyager 1 (spacecraft) (-31)    {source: Voyager_1_ST+refit2022_m}
 * …
 * $$SOE
 *  2026-Oct-22 00:00     172.652953067762  34.8813474
 * ```
 *
 * Returns null for anything else (an error text, `n.a.` values, a changed layout).
 */
internal object HorizonsEphemerisParser {

    fun parseRange(horizonsId: Int, result: String): HorizonsRangeDto? {
        val columns = firstRow(result)?.split(WHITESPACE)?.takeIf { it.size >= MIN_COLUMNS }
        val epoch = columns?.let { parseEpoch("${it[0]} ${it[1]}") }
        val delta = columns?.get(2)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        val rangeRate = columns?.get(3)?.toDoubleOrNull()?.takeIf(Double::isFinite)
        val name = targetName(result)
        return if (epoch != null && delta != null && name != null) {
            HorizonsRangeDto(
                horizonsId = horizonsId,
                name = name,
                epochMillis = epoch,
                deltaAu = delta,
                rangeRateKmPerSecond = rangeRate,
            )
        } else {
            null
        }
    }

    /** The first line between `$$SOE` and `$$EOE`, trimmed. */
    private fun firstRow(result: String): String? = result
        .substringAfter(START_OF_EPHEMERIS, missingDelimiterValue = "")
        .substringBefore(END_OF_EPHEMERIS)
        .lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotEmpty)

    /** "Voyager 1 (spacecraft) (-31)    {source: …}" → "Voyager 1". */
    private fun targetName(result: String): String? = result.lineSequence()
        .firstOrNull { it.startsWith(TARGET_NAME_LABEL) }
        ?.substringAfter(TARGET_NAME_LABEL)
        ?.substringBefore('{')
        ?.replace(PARENTHESISED, "")
        ?.trim()
        ?.ifEmpty { null }

    private fun parseEpoch(text: String): Long? = try {
        LocalDateTime.parse(text, EPOCH_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

    private const val START_OF_EPHEMERIS = "\$\$SOE"
    private const val END_OF_EPHEMERIS = "\$\$EOE"
    private const val TARGET_NAME_LABEL = "Target body name:"
    private const val MIN_COLUMNS = 4
    private val WHITESPACE = Regex("\\s+")
    private val PARENTHESISED = Regex("\\([^)]*\\)")
    private val EPOCH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm", Locale.ENGLISH)
}
