package com.radustavila.nasaexplorer.core.model

import java.time.Instant

/**
 * MET Norway's weather forecast for one place, reduced to what matters for observing the sky.
 *
 * Steps are hourly for the first two to three days, then 6-hourly, up to about ten days ahead.
 */
data class CloudForecast(
    /** The place the forecast is for, rounded as it was requested. */
    val coordinate: GeoCoordinate,
    val steps: List<CloudForecastStep>,
    /** When the app downloaded it, or last confirmed it unchanged. */
    val fetchedAt: Instant,
    /** When MET Norway last ran its model for this place, if it said. */
    val updatedAt: Instant? = null,
)

/** The forecast for one moment; cover and humidity in percent (0–100). */
data class CloudForecastStep(
    val time: Instant,
    val cloudCover: Double,
    val lowCloudCover: Double? = null,
    val mediumCloudCover: Double? = null,
    val highCloudCover: Double? = null,
    /** Only in the first days of the forecast. */
    val fogCover: Double? = null,
    val relativeHumidity: Double? = null,
    /** Metres per second. */
    val windSpeed: Double? = null,
    val airTemperatureCelsius: Double? = null,
)

/** How dark a night gets at its darkest. */
enum class NightDarkness {
    /** The Sun goes 18 degrees below the horizon: no twilight left. */
    ASTRONOMICAL,

    /** Only 12 degrees (summer at high latitudes): most stars, but a glow on the horizon. */
    NAUTICAL,

    /** Never 12 degrees below: too bright to observe. */
    NONE,
}

/**
 * One night as the sky decides it, before the weather: when it is dark and where the Moon is.
 *
 * Nights are told apart by [sunset]; a night in the polar winter, without one, by [darkStart].
 */
data class DarkNight(
    val sunset: Instant?,
    val sunrise: Instant?,
    val darkness: NightDarkness,
    /** Start of the darkest part (astronomical night, or nautical where it never gets darker). */
    val darkStart: Instant?,
    val darkEnd: Instant?,
    /** The Moon from [darkStart] to [darkEnd], one sample per [MoonSample.STEP_MINUTES]. */
    val moonSamples: List<MoonSample>,
    /** Lit fraction of the Moon in the middle of the night, 0–1. */
    val moonIlluminatedFraction: Double,
)

data class MoonSample(
    val time: Instant,
    /** Degrees above the horizon (negative: below it). */
    val altitudeDegrees: Double,
    /** Lit fraction, 0–1. */
    val illuminatedFraction: Double,
) {
    companion object {
        const val STEP_MINUTES = 60L
    }
}

/** Tonight and the next nights at the user's place, scored for observing. */
data class StargazingForecast(
    val coordinate: GeoCoordinate,
    val nights: List<StargazingNight>,
    /** When the cloud forecast was downloaded; null when there is none for this place yet. */
    val fetchedAt: Instant?,
)

/** One night's observing outlook. */
data class StargazingNight(
    val night: DarkNight,
    /**
     * 0–100: clear, dark and moonless is 100. Null when the cloud forecast does not cover the
     * night or the night never gets dark.
     */
    val score: Int?,
    /** The longest stretch worth going out for, or null when there is none. */
    val bestWindow: ObservingWindow?,
    /** Mean total cloud cover over the dark hours, percent; null without a forecast. */
    val meanCloudCover: Double?,
) {
    val rating: StargazingRating? get() = score?.let(StargazingRating::of)
}

/** A stretch of a night with a clear, dark sky. */
data class ObservingWindow(
    val start: Instant,
    val end: Instant,
    /** Mean total cloud cover in the window, percent. */
    val meanCloudCover: Double,
    val moon: MoonDuringWindow,
)

/** Where the Moon is during an [ObservingWindow]. */
enum class MoonDuringWindow {
    /** Below the horizon throughout. */
    DOWN,

    /** Above the horizon throughout. */
    UP,

    /** Comes up during the window. */
    RISES,

    /** Goes down during the window. */
    SETS,
}

enum class StargazingRating {
    POOR,
    FAIR,
    GOOD,
    EXCELLENT,
    ;

    companion object {
        const val FAIR_FROM = 30
        const val GOOD_FROM = 55
        const val EXCELLENT_FROM = 75

        fun of(score: Int): StargazingRating = when {
            score >= EXCELLENT_FROM -> EXCELLENT
            score >= GOOD_FROM -> GOOD
            score >= FAIR_FROM -> FAIR
            else -> POOR
        }
    }
}

/** The user's clear-night alert choices. */
data class StargazingAlertSettings(
    val enabled: Boolean = false,
    /** Alert only for a night scoring at least this much. */
    val minimumScore: Int = DEFAULT_MINIMUM_SCORE,
) {
    companion object {
        const val DEFAULT_MINIMUM_SCORE = StargazingRating.GOOD_FROM

        /** The thresholds the user can pick from: good, very good, excellent. */
        val MINIMUM_SCORE_CHOICES: List<Int> = listOf(StargazingRating.GOOD_FROM, 65, StargazingRating.EXCELLENT_FROM)
    }
}

/** What the clear-night notification says. */
data class StargazingAlert(
    val score: Int,
    val window: ObservingWindow,
    val moonIlluminatedFraction: Double,
)
