package com.radustavila.nasaexplorer.data.climate.remote

import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.ClimatePoint
import java.time.LocalDate
import java.time.Year
import kotlin.math.floor

/**
 * Reads the four climate files into dated values. Each parser finds its header row and its columns
 * by name, so a new column or a reordered one does not break it; comment lines (`#`), title lines,
 * blank lines and rows it cannot read are skipped, and missing values (`***`, `-99.99`, `-9999`,
 * an empty cell) are left out. A file without its header or any usable row gives an empty list.
 */
internal object ClimateFileParser {

    /** NOAA's own record starts in May 1974; the months before are Scripps data, whose terms are unconfirmed. */
    val CO2_FIRST_MONTH: LocalDate = LocalDate.of(1974, 5, 1)

    /** Below this a sea level or CO₂ value is a fill value: -99.99 in NOAA's files. */
    private const val FILL_VALUE_LIMIT = -99.0

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun parse(indicator: ClimateIndicator, text: String): List<ClimatePoint> = when (indicator) {
        ClimateIndicator.CO2 -> co2(text)
        ClimateIndicator.GLOBAL_TEMPERATURE -> gistemp(text)
        ClimateIndicator.SEA_LEVEL -> seaLevel(text)
        ClimateIndicator.ARCTIC_SEA_ICE -> seaIce(text)
    }

    /** NOAA GML `co2_mm_mlo.csv`: `year,month,decimal date,average,…`, the monthly mean in ppm. */
    fun co2(text: String): List<ClimatePoint> {
        val (table, columns) = Table.read(text, "year", "month", "average") ?: return emptyList()
        val (year, month, average) = columns
        return table.rows.mapNotNull { row ->
            val date = monthDate(row.int(year), row.int(month)) ?: return@mapNotNull null
            val value = row.double(average)?.takeIf { it > 0 } ?: return@mapNotNull null
            ClimatePoint(date, value).takeIf { !date.isBefore(CO2_FIRST_MONTH) }
        }.sortedDistinct()
    }

    /** GISTEMP `GLB.Ts+dSST.csv`: a title line, then `Year,Jan,…,Dec,J-D,…` anomalies in °C, `***` when missing. */
    fun gistemp(text: String): List<ClimatePoint> {
        val (table, columns) = Table.read(text, "year", *MONTHS.toTypedArray()) ?: return emptyList()
        val year = columns.first()
        val months = columns.drop(1)
        return table.rows.flatMap { row ->
            months.mapIndexedNotNull { index, column ->
                val date = monthDate(row.int(year), index + 1) ?: return@mapIndexedNotNull null
                row.double(column)?.let { ClimatePoint(date, it) }
            }
        }.sortedDistinct()
    }

    /**
     * NOAA STAR `slr_sla_gbl_free_ref_90.csv`: a decimal year, then one column per altimetry
     * mission (mm). Where missions overlap the newer one, further right, wins.
     */
    fun seaLevel(text: String): List<ClimatePoint> {
        val (table, columns) = Table.read(text, "year") ?: return emptyList()
        val year = columns.single()
        // Newest mission first; a file with no mission column reads nothing.
        val missions = table.header.indices.filter { it != year }.reversed()
        return table.rows.mapNotNull { row ->
            val date = row.double(year)?.let(::decimalYearDate) ?: return@mapNotNull null
            val value = missions.firstNotNullOfOrNull { column ->
                row.double(column)?.takeIf { it > FILL_VALUE_LIMIT }
            } ?: return@mapNotNull null
            ClimatePoint(date, value)
        }.sortedDistinct()
    }

    /**
     * NSIDC `N_09_extent_v4.0.csv`: `year, mo, source_dataset, region, extent, area`, in million km²,
     * -9999 when missing.
     */
    fun seaIce(text: String): List<ClimatePoint> {
        val (table, columns) = Table.read(text, "year", "mo", "extent") ?: return emptyList()
        val (year, month, extent) = columns
        return table.rows.mapNotNull { row ->
            val date = monthDate(row.int(year), row.int(month)) ?: return@mapNotNull null
            val value = row.double(extent)?.takeIf { it > 0 } ?: return@mapNotNull null
            ClimatePoint(date, value)
        }.sortedDistinct()
    }

    @Suppress("MagicNumber") // Calendar bounds.
    private fun monthDate(year: Int?, month: Int?): LocalDate? {
        if (year == null || month == null) return null
        return if (month in 1..12 && year in MIN_YEAR..MAX_YEAR) LocalDate.of(year, month, 1) else null
    }

    /** 2025.1268 → 17 February 2025. */
    private fun decimalYearDate(value: Double): LocalDate? {
        val year = floor(value).toInt()
        if (year !in MIN_YEAR..MAX_YEAR) return null
        val days = Year.of(year).length()
        val dayOfYear = (floor((value - year) * days).toInt() + 1).coerceIn(1, days)
        return LocalDate.ofYearDay(year, dayOfYear)
    }

    private fun List<ClimatePoint>.sortedDistinct(): List<ClimatePoint> =
        sortedBy { it.date }.asReversed().distinctBy { it.date }.asReversed()

    private const val MIN_YEAR = 1800
    private const val MAX_YEAR = 2999

    /** A comma-separated table under the first row whose first cell is "year". */
    private class Table(val header: List<String>, val rows: List<List<String>>) {

        companion object {
            /** The table and the index of each of [columns], or null when the header or a column is missing. */
            fun read(text: String, vararg columns: String): Pair<Table, List<Int>>? {
                val table = read(text) ?: return null
                val indices = columns.map { table.header.indexOf(it) }
                return if (indices.any { it < 0 }) null else table to indices
            }

            private fun read(text: String): Table? {
                val lines = text.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toList()
                val headerIndex = lines.indexOfFirst { cells(it).firstOrNull()?.lowercase() == "year" }
                if (headerIndex < 0) return null
                return Table(
                    header = cells(lines[headerIndex]).map { it.lowercase() },
                    rows = lines.drop(headerIndex + 1).map(::cells),
                )
            }

            private fun cells(line: String): List<String> = line.split(',').map { it.trim() }
        }
    }

    private fun List<String>.int(column: Int): Int? = getOrNull(column)?.toIntOrNull()

    private fun List<String>.double(column: Int): Double? =
        getOrNull(column)?.toDoubleOrNull()?.takeIf { it.isFinite() }
}
