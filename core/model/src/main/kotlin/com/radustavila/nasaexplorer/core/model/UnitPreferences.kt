package com.radustavila.nasaexplorer.core.model

import java.util.Locale

/** Units for distances, altitudes and speeds. Astronomical units (light-years, LD, Earth radii) never change. */
enum class MeasurementSystem {
    /** Whatever the device's region uses. */
    AUTOMATIC,
    METRIC,
    IMPERIAL,
    ;

    /** [METRIC] or [IMPERIAL]: [AUTOMATIC] decided by the region of [locale]. */
    fun resolve(locale: Locale): MeasurementSystem = when (this) {
        AUTOMATIC -> if (locale.country.uppercase(Locale.ROOT) in IMPERIAL_REGIONS) IMPERIAL else METRIC
        METRIC, IMPERIAL -> this
    }

    private companion object {
        /**
         * Regions whose everyday distances and speeds are in miles: the United States, Liberia,
         * Myanmar, and the United Kingdom (road distances and speed limits).
         */
        val IMPERIAL_REGIONS = setOf("US", "LR", "MM", "GB")
    }
}

/** How clock times are written. */
enum class TimeFormat {
    /** The device's own 12/24-hour setting. */
    SYSTEM,
    TWELVE_HOUR,
    TWENTY_FOUR_HOUR,
    ;

    /** Whether times use the 24-hour clock, given what the system would do. */
    fun is24Hour(systemIs24Hour: Boolean): Boolean = when (this) {
        SYSTEM -> systemIs24Hour
        TWELVE_HOUR -> false
        TWENTY_FOUR_HOUR -> true
    }
}

/** The user's display units, applied wherever the app shows a distance, a speed or a clock time. */
data class UnitPreferences(
    val measurementSystem: MeasurementSystem = MeasurementSystem.AUTOMATIC,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
)
