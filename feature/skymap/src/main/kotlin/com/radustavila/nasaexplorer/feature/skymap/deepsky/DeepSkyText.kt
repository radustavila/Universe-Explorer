package com.radustavila.nasaexplorer.feature.skymap.deepsky

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.DeepSkyObject
import com.radustavila.nasaexplorer.core.model.DeepSkyType
import com.radustavila.nasaexplorer.core.model.ObservingConditions
import com.radustavila.nasaexplorer.core.model.ObservingInstrument
import com.radustavila.nasaexplorer.feature.skymap.R
import com.radustavila.nasaexplorer.feature.skymap.render.DeepSkyStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@StringRes
internal fun DeepSkyType.nameRes(): Int = when (this) {
    DeepSkyType.GALAXY -> R.string.deep_sky_type_galaxy
    DeepSkyType.OPEN_CLUSTER -> R.string.deep_sky_type_open_cluster
    DeepSkyType.GLOBULAR_CLUSTER -> R.string.deep_sky_type_globular_cluster
    DeepSkyType.PLANETARY_NEBULA -> R.string.deep_sky_type_planetary_nebula
    DeepSkyType.NEBULA -> R.string.deep_sky_type_nebula
    DeepSkyType.SUPERNOVA_REMNANT -> R.string.deep_sky_type_supernova_remnant
    DeepSkyType.DARK_NEBULA -> R.string.deep_sky_type_dark_nebula
    DeepSkyType.STAR_GROUP -> R.string.deep_sky_type_star_group
}

@StringRes
internal fun ObservingInstrument.nameRes(): Int = when (this) {
    ObservingInstrument.NAKED_EYE -> R.string.deep_sky_instrument_naked_eye
    ObservingInstrument.BINOCULARS -> R.string.deep_sky_instrument_binoculars
    ObservingInstrument.SMALL_TELESCOPE -> R.string.deep_sky_instrument_small_telescope
    ObservingInstrument.TELESCOPE -> R.string.deep_sky_instrument_telescope
}

@StringRes
internal fun ObservingConditions.nameRes(): Int = when (this) {
    ObservingConditions.EXCELLENT -> R.string.deep_sky_conditions_excellent
    ObservingConditions.GOOD -> R.string.deep_sky_conditions_good
    ObservingConditions.FAIR -> R.string.deep_sky_conditions_fair
    ObservingConditions.POOR -> R.string.deep_sky_conditions_poor
}

/** "M31 · Andromeda Galaxy", "M47 · NGC 2422", "C14 · Double Cluster", "NGC 2903". */
internal fun DeepSkyObject.title(): String {
    val short = DeepSkyStyle.shortName(this)
    val second = commonName ?: designation.takeIf { it.isNotEmpty() }
    return listOfNotNull(short, second).distinct().joinToString(" · ")
}

/** The NGC/IC designation when the title does not already show it ("NGC 224"), else null. */
internal fun DeepSkyObject.designationLine(): String? =
    designation.takeIf { commonName != null && it.isNotEmpty() && it != DeepSkyStyle.shortName(this) }

/** "Magnitude 3.4 · 178′ × 70′", whatever of the two the catalogue has. */
@Composable
internal fun DeepSkyObject.brightnessAndSize(units: UnitFormatter): String? {
    val magnitude = magnitude?.let { stringResource(R.string.sky_map_magnitude, units.number(it.toDouble(), 1)) }
    val major = majorAxisArcmin
    val minor = minorAxisArcmin
    val size = when {
        major == null -> null
        minor == null || minor == major -> stringResource(R.string.deep_sky_size_round, arcminutes(major, units))
        else -> stringResource(R.string.deep_sky_size, arcminutes(major, units), arcminutes(minor, units))
    }
    return listOfNotNull(magnitude, size).joinToString(" · ").ifEmpty { null }
}

/** "Best in October · Binoculars". */
@Composable
internal fun DeepSkyObject.bestMonthAndInstrument(locale: Locale = Locale.getDefault()): String =
    stringResource(
        R.string.deep_sky_best_month_and_instrument,
        bestMonth.getDisplayName(TextStyle.FULL_STANDALONE, locale),
        stringResource(instrument.nameRes()),
    )

/** The day a mark was made, in the device's style. */
internal fun LocalDate.formatDay(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

private const val ARCMIN_DECIMALS_BELOW = 10f

private fun arcminutes(value: Float, units: UnitFormatter): String =
    units.number(value.toDouble(), if (value < ARCMIN_DECIMALS_BELOW) 1 else 0)
