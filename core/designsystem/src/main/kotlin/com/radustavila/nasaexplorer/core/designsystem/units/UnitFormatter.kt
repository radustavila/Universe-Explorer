package com.radustavila.nasaexplorer.core.designsystem.units

import android.content.Context
import android.content.res.Resources
import android.icu.text.DateFormat
import android.icu.util.TimeZone
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import com.radustavila.nasaexplorer.core.designsystem.R
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The user's unit choices, provided once at the root of the UI (`MainActivity`), so features read
 * them without depending on each other or on the data layer. Defaults follow the device.
 */
val LocalUnitPreferences = staticCompositionLocalOf { UnitPreferences() }

/**
 * Formats distances, speeds, temperatures and clock times in the user's units, with translatable
 * unit labels. Metric values go in (the APIs all speak kilometres); the user's system comes out.
 *
 * Astronomical units (light-years, lunar distances, Earth radii) are not this class's business:
 * they mean the same in every system.
 */
@Stable
class UnitFormatter(
    private val resources: Resources,
    val locale: Locale,
    /** Already resolved: never [MeasurementSystem.AUTOMATIC]. */
    val system: MeasurementSystem,
    val is24Hour: Boolean,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    val isImperial: Boolean get() = system == MeasurementSystem.IMPERIAL

    private val timeFormat by lazy { dateFormat(if (is24Hour) "Hm" else "hm") }
    private val dateTimeFormat by lazy { dateFormat(if (is24Hour) "yMMMdHm" else "yMMMdhm") }
    private val dayAndTimeFormat by lazy { dateFormat(if (is24Hour) "EEEdMMMHm" else "EEEdMMMhm") }

    /** "21:30" or "9:30 PM", in the device's time zone. */
    fun time(instant: Instant): String = timeFormat.format(Date.from(instant))

    /** "21 Oct 2026, 21:30" or "Oct 21, 2026, 9:30 PM", ordered as the locale writes dates. */
    fun dateTime(instant: Instant): String = dateTimeFormat.format(Date.from(instant))

    /** "Tue 21 Oct, 21:30": for times within the next days. */
    fun dayAndTime(instant: Instant): String = dayAndTimeFormat.format(Date.from(instant))

    /** "408 km" or "254 mi": distances and altitudes given in kilometres. */
    fun distance(km: Double, fractionDigits: Int = 0): String {
        val value = if (isImperial) km / KM_PER_MILE else km
        return string(if (isImperial) R.string.unit_mi else R.string.unit_km, number(value, fractionDigits))
    }

    /**
     * Distances too large to read digit by digit: "2.9 million km", or rounded to the thousand below
     * a million ("748,000 km").
     */
    fun largeDistance(km: Double): String {
        val value = if (isImperial) km / KM_PER_MILE else km
        return if (value >= MILLION) {
            string(if (isImperial) R.string.unit_million_mi else R.string.unit_million_km, number(value / MILLION, 1))
        } else {
            val rounded = (value / THOUSAND).roundToLong() * THOUSAND.toLong()
            string(if (isImperial) R.string.unit_mi else R.string.unit_km, number(rounded.toDouble(), 0))
        }
    }

    /**
     * Rough lengths given in metres, e.g. an asteroid's size: "340 m" / "1.2 km", or "1,100 ft" /
     * "0.8 mi". A range shares one unit: "34–75 m". Two significant figures below the larger unit.
     */
    fun length(minMeters: Double, maxMeters: Double = minMeters): String {
        val (unit, toUnit) = lengthUnit(maxMeters)
        fun value(meters: Double): String {
            val converted = toUnit(meters)
            return if (unit.isLarge) number(converted, 1) else number(roundToTwoSignificant(converted).toDouble(), 0)
        }
        val text = if (minMeters == maxMeters) value(minMeters) else string(R.string.unit_range, value(minMeters), value(maxMeters))
        return string(unit.labelRes, text)
    }

    /** "27,600 km/h" or "17,150 mph". */
    fun speedPerHour(kmPerHour: Double, fractionDigits: Int = 0): String =
        if (isImperial) {
            string(R.string.unit_mph, number(kmPerHour / KM_PER_MILE, fractionDigits))
        } else {
            string(R.string.unit_km_per_hour, number(kmPerHour, fractionDigits))
        }

    /** "12.3 km/s" or "7.6 mi/s": speeds of things in space. */
    fun speedPerSecond(kmPerSecond: Double, fractionDigits: Int = 1): String =
        if (isImperial) {
            string(R.string.unit_mi_per_second, number(kmPerSecond / KM_PER_MILE, fractionDigits))
        } else {
            string(R.string.unit_km_per_second, number(kmPerSecond, fractionDigits))
        }

    /** "21 °C" or "70 °F". */
    fun temperature(celsius: Double): String =
        if (isImperial) {
            string(R.string.unit_fahrenheit, number(celsius * FAHRENHEIT_PER_CELSIUS + FAHRENHEIT_OFFSET, 0))
        } else {
            string(R.string.unit_celsius, number(celsius, 0))
        }

    /** "1,200 km²" or "463 mi²". */
    fun area(squareKm: Double): String =
        if (isImperial) {
            string(R.string.unit_square_mi, number(squareKm / SQUARE_KM_PER_SQUARE_MILE, 0))
        } else {
            string(R.string.unit_square_km, number(squareKm, 0))
        }

    /** "304 ha" or "751 acres": land areas, e.g. a burned area reported in acres. */
    fun landArea(acres: Double): String =
        if (isImperial) {
            resources.getQuantityString(R.plurals.unit_acres, quantity(acres), number(acres, 0))
        } else {
            string(R.string.unit_hectares, number(acres * HECTARES_PER_ACRE, 0))
        }

    /** "51.5° N, 0.1° W": hemispheres spelled out, as the locale writes them. */
    fun coordinate(latitude: Double, longitude: Double, fractionDigits: Int = 1): String = string(
        R.string.coordinate_pair,
        string(if (latitude >= 0) R.string.coordinate_north else R.string.coordinate_south, number(abs(latitude), fractionDigits)),
        string(if (longitude >= 0) R.string.coordinate_east else R.string.coordinate_west, number(abs(longitude), fractionDigits)),
    )

    /** A plain number in the locale's digits and grouping. */
    fun number(value: Double, fractionDigits: Int): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }.format(value)

    private fun string(@StringRes id: Int, vararg args: Any): String = resources.getString(id, *args)

    private fun dateFormat(skeleton: String): DateFormat =
        DateFormat.getInstanceForSkeleton(skeleton, locale).apply { timeZone = TimeZone.getTimeZone(zone.id) }

    private fun lengthUnit(maxMeters: Double): Pair<LengthUnit, (Double) -> Double> = when {
        !isImperial && maxMeters >= THOUSAND -> LengthUnit.KILOMETRE to { m: Double -> m / THOUSAND }
        !isImperial -> LengthUnit.METRE to { m: Double -> m }
        maxMeters * FEET_PER_METRE >= FEET_PER_MILE -> LengthUnit.MILE to { m: Double -> m / THOUSAND / KM_PER_MILE }
        else -> LengthUnit.FOOT to { m: Double -> m * FEET_PER_METRE }
    }

    private enum class LengthUnit(@param:StringRes val labelRes: Int, val isLarge: Boolean) {
        METRE(R.string.unit_m, isLarge = false),
        KILOMETRE(R.string.unit_km, isLarge = true),
        FOOT(R.string.unit_ft, isLarge = false),
        MILE(R.string.unit_mi, isLarge = true),
    }

    companion object {
        const val KM_PER_MILE = 1.609344
        const val FEET_PER_METRE = 3.280839895
        const val KM_PER_NAUTICAL_MILE = 1.852
        private const val FEET_PER_MILE = 5_280.0
        private const val SQUARE_KM_PER_SQUARE_MILE = KM_PER_MILE * KM_PER_MILE
        private const val HECTARES_PER_ACRE = 0.40468564224
        private const val FAHRENHEIT_PER_CELSIUS = 1.8
        private const val FAHRENHEIT_OFFSET = 32.0
        private const val MILLION = 1_000_000.0
        private const val THOUSAND = 1_000.0

        /** For code outside composition (notifications): the same formatting as the screens. */
        fun create(context: Context, preferences: UnitPreferences, locale: Locale = Locale.getDefault()): UnitFormatter =
            UnitFormatter(
                resources = context.resources,
                locale = locale,
                system = preferences.measurementSystem.resolve(locale),
                is24Hour = preferences.timeFormat.is24Hour(android.text.format.DateFormat.is24HourFormat(context)),
            )
    }
}

/** The formatter for the current locale and the user's [LocalUnitPreferences]. */
@Composable
fun rememberUnitFormatter(): UnitFormatter {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val preferences = LocalUnitPreferences.current
    // Read on every composition: the user may flip the system clock setting while the app is open.
    val systemIs24Hour = android.text.format.DateFormat.is24HourFormat(context)
    return remember(context, locale, preferences, systemIs24Hour) {
        UnitFormatter(
            resources = context.resources,
            locale = locale,
            system = preferences.measurementSystem.resolve(locale),
            is24Hour = preferences.timeFormat.is24Hour(systemIs24Hour),
        )
    }
}

private fun quantity(value: Double): Int = value.roundToLong().coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

private fun roundToTwoSignificant(value: Double): Long {
    if (value < 100) return value.roundToLong()
    var scale = 1L
    var scaled = value
    while (scaled >= 100) {
        scaled /= 10
        scale *= 10
    }
    return scaled.roundToLong() * scale
}
