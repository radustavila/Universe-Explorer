package com.radustavila.nasaexplorer.data.comets.remote

import com.radustavila.nasaexplorer.data.comets.remote.dto.CometCandidateDto
import com.radustavila.nasaexplorer.data.comets.remote.dto.SbdbQueryDto
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Which comets are worth asking Horizons about: a bright absolute magnitude, a perihelion close
 * enough to the Sun to light them up, passed within the last 200 days or due within 350. On
 * 2026-10-22 that was five comets.
 */
internal object SbdbCometQuery {

    val FIELDS: List<String> = listOf("full_name", "pdes", "M1", "K1", "q", "e", "tp")

    /** The `sb-cdata` filter for [now]; Retrofit URL-encodes it. */
    fun constraints(now: Instant): String {
        val from = julianDate(now.minus(PERIHELION_PASSED_WITHIN))
        val until = julianDate(now.plus(PERIHELION_DUE_WITHIN))
        return """{"AND":["M1|LT|$MAX_ABSOLUTE_MAGNITUDE","q|LT|$MAX_PERIHELION_DISTANCE_AU","tp|RG|$from|$until"]}"""
    }

    /** The rows that name a comet with an orbit; anything incomplete is dropped. */
    fun candidates(table: SbdbQueryDto): List<CometCandidateDto> = table.rows().mapNotNull { row ->
        val designation = row["pdes"]?.trim()?.ifEmpty { null } ?: return@mapNotNull null
        CometCandidateDto(
            designation = designation,
            fullName = row["full_name"]?.trim()?.ifEmpty { null } ?: designation,
            absoluteMagnitude = row["M1"]?.toDoubleOrNull(),
            slopeParameter = row["K1"]?.toDoubleOrNull(),
            perihelionDistanceAu = row["q"]?.toDoubleOrNull() ?: return@mapNotNull null,
            eccentricity = row["e"]?.toDoubleOrNull() ?: return@mapNotNull null,
            perihelionJulianDate = row["tp"]?.toDoubleOrNull(),
        )
    }

    fun julianDateToInstant(julianDate: Double): Instant =
        Instant.ofEpochMilli(((julianDate - UNIX_EPOCH_JULIAN_DATE) * MILLIS_PER_DAY).roundToLong())

    private fun julianDate(instant: Instant): String =
        String.format(Locale.ROOT, "%.1f", instant.toEpochMilli() / MILLIS_PER_DAY + UNIX_EPOCH_JULIAN_DATE)

    private const val MAX_ABSOLUTE_MAGNITUDE = 11
    private const val MAX_PERIHELION_DISTANCE_AU = 2.5
    private val PERIHELION_PASSED_WITHIN: Duration = Duration.ofDays(200)
    private val PERIHELION_DUE_WITHIN: Duration = Duration.ofDays(350)
    private const val UNIX_EPOCH_JULIAN_DATE = 2_440_587.5
    private const val MILLIS_PER_DAY = 86_400_000.0
}
