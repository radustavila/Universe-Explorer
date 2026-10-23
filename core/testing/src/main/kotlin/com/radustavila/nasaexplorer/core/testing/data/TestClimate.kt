package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.ClimatePoint
import com.radustavila.nasaexplorer.core.model.ClimateSeries
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.sin

/**
 * Climate series for tests and screenshots: smooth made-up curves shaped like the real records,
 * ending where the files did on 23 October 2026 (CO₂ 426.07 ppm and +1.38 °C in September 2026,
 * sea level 83.6 mm in February 2025, Arctic ice 4.81 million km² in September 2026).
 */
@Suppress("MagicNumber") // Made-up curves.
object TestClimate {

    val LAST_MONTH: LocalDate = LocalDate.of(2026, 9, 1)
    val SEA_LEVEL_LAST: LocalDate = LocalDate.of(2025, 2, 16)

    fun co2(): ClimateSeries = monthly(ClimateIndicator.CO2, LocalDate.of(1974, 5, 1)) { years, month ->
        331.0 + 1.25 * years + 0.0125 * years * years + 3.0 * sin(2 * PI * (month - 2) / 12.0)
    }.endingAt(426.07)

    fun temperature(): ClimateSeries = monthly(
        ClimateIndicator.GLOBAL_TEMPERATURE,
        LocalDate.of(1880, 1, 1),
    ) { years, month ->
        val trend = if (years < 95) -0.2 + 0.002 * years else -0.01 + 0.027 * (years - 95)
        trend + 0.12 * sin(years * 1.7 + month) + 0.06 * sin(years * 0.37)
    }.endingAt(1.38)

    fun seaLevel(): ClimateSeries {
        val start = LocalDate.of(1993, 1, 1)
        val points = generateSequence(start) { it.plusDays(10) }
            .takeWhile { !it.isAfter(SEA_LEVEL_LAST) }
            .map { date ->
                val years = (date.toEpochDay() - start.toEpochDay()) / 365.25
                ClimatePoint(date, -22.0 + 2.2 * years + 0.033 * years * years + 3.0 * sin(years * 2.1))
            }
            .toList()
        return ClimateSeries(ClimateIndicator.SEA_LEVEL, points).endingAt(83.6)
    }

    fun arcticSeaIce(): ClimateSeries = ClimateSeries(
        ClimateIndicator.ARCTIC_SEA_ICE,
        (1979..2026).map { year ->
            val years = year - 1979.0
            ClimatePoint(LocalDate.of(year, 9, 1), 7.3 - 0.065 * years + 0.45 * sin(years * 1.3))
        },
    ).endingAt(4.81)

    fun all(): List<ClimateSeries> = listOf(co2(), temperature(), seaLevel(), arcticSeaIce())

    private fun monthly(
        indicator: ClimateIndicator,
        start: LocalDate,
        value: (years: Double, month: Int) -> Double,
    ): ClimateSeries = ClimateSeries(
        indicator,
        generateSequence(start) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(LAST_MONTH) }
            .map { date ->
                val years = (date.year - start.year) + (date.monthValue - 1) / 12.0
                ClimatePoint(date, value(years, date.monthValue))
            }
            .toList(),
    )

    private fun ClimateSeries.endingAt(value: Double) =
        copy(points = points.dropLast(1) + points.last().copy(value = value))
}
