package com.radustavila.nasaexplorer.core.model

import java.time.LocalDate
import kotlin.math.abs

/**
 * The four climate vital signs on the Earth tab (F26), each read from one public file. [key] is
 * stored in the climate database and in routes: never rename or reuse one.
 */
enum class ClimateIndicator(val key: String, val syncSource: SyncSource) {
    /** Monthly mean CO₂ at Mauna Loa, in parts per million (NOAA GML). */
    CO2("co2", SyncSource.CLIMATE_CO2),

    /** Monthly global surface temperature anomaly in °C against 1951–1980 (NASA GISTEMP v4). */
    GLOBAL_TEMPERATURE("global_temperature", SyncSource.CLIMATE_TEMPERATURE),

    /** Global mean sea level anomaly in millimetres, about every ten days (NOAA STAR altimetry). */
    SEA_LEVEL("sea_level", SyncSource.CLIMATE_SEA_LEVEL),

    /** Arctic sea ice extent each September, its yearly low, in million km² (NSIDC G02135). */
    ARCTIC_SEA_ICE("arctic_sea_ice", SyncSource.CLIMATE_SEA_ICE),
    ;

    companion object {
        fun fromKey(key: String): ClimateIndicator? = entries.firstOrNull { it.key == key }
    }
}

/** One value of an indicator, dated by its month's first day (or its own day for sea level). */
data class ClimatePoint(val date: LocalDate, val value: Double)

/** How much an indicator moved between the same time of [sinceYear] and its latest value. */
data class ClimateChange(val sinceYear: Int, val delta: Double)

/** Every stored value of one indicator, oldest first. */
data class ClimateSeries(
    val indicator: ClimateIndicator,
    val points: List<ClimatePoint>,
) {
    val latest: ClimatePoint? get() = points.lastOrNull()

    /**
     * The change since [BASELINE_YEAR] (or the first year of data, when it starts later), compared
     * at the same time of year so seasons do not count: CO₂ in September 2026 against September 1980.
     */
    fun change(): ClimateChange? {
        val latest = latest ?: return null
        val firstYear = maxOf(BASELINE_YEAR, points.first().date.year)
        return (firstYear until latest.date.year).firstNotNullOfOrNull { year ->
            points
                .filter { it.date.year == year }
                .minByOrNull { abs(it.date.dayOfYear - latest.date.dayOfYear) }
                ?.takeIf { abs(it.date.dayOfYear - latest.date.dayOfYear) <= MAX_DAYS_APART }
                ?.let { ClimateChange(sinceYear = year, delta = latest.value - it.value) }
        }
    }

    companion object {
        /** NASA's vital signs compare with 1980; satellite records start around then. */
        const val BASELINE_YEAR = 1980

        /** The baseline value must be from about the same time of year as the latest. */
        private const val MAX_DAYS_APART = 20
    }
}
