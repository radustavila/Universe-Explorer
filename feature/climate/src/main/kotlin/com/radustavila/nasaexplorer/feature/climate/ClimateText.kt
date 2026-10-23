package com.radustavila.nasaexplorer.feature.climate

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.radustavila.nasaexplorer.core.designsystem.chart.ChartData
import com.radustavila.nasaexplorer.core.designsystem.chart.ChartPoint
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.ClimateSeries
import java.time.LocalDate

/** The words and the source of one indicator, all from string resources. */
internal data class ClimateTexts(
    @param:StringRes val title: Int,
    @param:StringRes val subtitle: Int,
    @param:StringRes val explanation: Int,
    @param:StringRes val credit: Int,
    val sourceUrl: String,
)

internal val ClimateIndicator.texts: ClimateTexts
    get() = when (this) {
        ClimateIndicator.CO2 -> ClimateTexts(
            title = R.string.climate_co2_title,
            subtitle = R.string.climate_co2_subtitle,
            explanation = R.string.climate_co2_explanation,
            credit = R.string.climate_co2_credit,
            sourceUrl = "https://gml.noaa.gov/ccgg/trends/",
        )
        ClimateIndicator.GLOBAL_TEMPERATURE -> ClimateTexts(
            title = R.string.climate_temperature_title,
            subtitle = R.string.climate_temperature_subtitle,
            explanation = R.string.climate_temperature_explanation,
            credit = R.string.climate_temperature_credit,
            sourceUrl = "https://data.giss.nasa.gov/gistemp/",
        )
        ClimateIndicator.SEA_LEVEL -> ClimateTexts(
            title = R.string.climate_sea_level_title,
            subtitle = R.string.climate_sea_level_subtitle,
            explanation = R.string.climate_sea_level_explanation,
            credit = R.string.climate_sea_level_credit,
            sourceUrl = "https://www.star.nesdis.noaa.gov/socd/lsa/SeaLevelRise/",
        )
        ClimateIndicator.ARCTIC_SEA_ICE -> ClimateTexts(
            title = R.string.climate_sea_ice_title,
            subtitle = R.string.climate_sea_ice_subtitle,
            explanation = R.string.climate_sea_ice_explanation,
            credit = R.string.climate_sea_ice_credit,
            sourceUrl = "https://nsidc.org/data/g02135/versions/4",
        )
    }

/**
 * Words an indicator's values in the user's units: CO₂ in ppm (the same everywhere), temperature
 * anomalies in °C or °F, sea level in mm or in, ice in million km² or mi².
 */
internal class ClimateValueFormat(private val indicator: ClimateIndicator, private val units: UnitFormatter) {

    /** How many of the user's units one stored unit is; the chart works in the user's units. */
    val scale: Double = when (indicator) {
        ClimateIndicator.CO2 -> 1.0
        ClimateIndicator.GLOBAL_TEMPERATURE -> units.temperatureChangeValue(1.0)
        ClimateIndicator.SEA_LEVEL -> units.smallLengthValue(1.0)
        ClimateIndicator.ARCTIC_SEA_ICE -> units.millionAreaValue(1.0)
    }

    /** "426.07 ppm", "+1.38 °C", "+84 mm", "4.81 million km²". */
    fun value(value: Double, ppm: (String) -> String): String = when (indicator) {
        ClimateIndicator.CO2 -> ppm(units.number(value, 2))
        ClimateIndicator.GLOBAL_TEMPERATURE -> units.temperatureChange(value)
        ClimateIndicator.SEA_LEVEL -> units.smallLength(value, signed = true)
        ClimateIndicator.ARCTIC_SEA_ICE -> units.millionArea(value)
    }

    /** A change, always signed: "+88 ppm", "+1.10 °C", "+102 mm", "−2.86 million km²". */
    fun change(delta: Double, ppm: (String) -> String): String = when (indicator) {
        ClimateIndicator.CO2 -> ppm(units.signedNumber(delta, 0))
        ClimateIndicator.GLOBAL_TEMPERATURE -> units.temperatureChange(delta)
        ClimateIndicator.SEA_LEVEL -> units.smallLength(delta, signed = true)
        ClimateIndicator.ARCTIC_SEA_ICE -> units.millionArea(delta, signed = true)
    }

    /** An axis label for a value already in the user's units: round, short. */
    fun axis(userValue: Double, ppm: (String) -> String): String {
        val stored = userValue / scale
        return when (indicator) {
            ClimateIndicator.CO2 -> ppm(units.number(userValue, 0))
            ClimateIndicator.GLOBAL_TEMPERATURE -> units.temperatureChange(stored, fractionDigits = 1)
            ClimateIndicator.SEA_LEVEL -> units.smallLength(stored, signed = true)
            ClimateIndicator.ARCTIC_SEA_ICE -> units.millionArea(stored, fractionDigits = 1)
        }
    }

    fun chartData(series: ClimateSeries): ChartData =
        ChartData(series.points.map { ChartPoint(it.date.toEpochDay().toDouble(), it.value * scale) })
}

/** January 1st of every tenth year (every twentieth over long records) inside the series, as epoch days. */
internal fun yearTicks(series: ClimateSeries): List<Double> {
    val first = series.points.first().date.year
    val last = series.points.last().date.year
    val step = if (last - first > LONG_RECORD_YEARS) 20 else 10
    val start = (first / step + 1) * step
    return (start..last step step).map { LocalDate.of(it, 1, 1).toEpochDay().toDouble() }
}

private const val LONG_RECORD_YEARS = 80

/** Words a CO₂ number: "426.07 ppm". */
@Composable
internal fun rememberPpm(): (String) -> String {
    val resources = LocalResources.current
    return remember(resources) { { value -> resources.getString(R.string.climate_value_ppm, value) } }
}
