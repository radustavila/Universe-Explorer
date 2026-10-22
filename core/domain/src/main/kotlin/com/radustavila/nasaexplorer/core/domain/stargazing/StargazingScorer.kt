package com.radustavila.nasaexplorer.core.domain.stargazing

import com.radustavila.nasaexplorer.core.model.CloudForecast
import com.radustavila.nasaexplorer.core.model.CloudForecastStep
import com.radustavila.nasaexplorer.core.model.DarkNight
import com.radustavila.nasaexplorer.core.model.MoonDuringWindow
import com.radustavila.nasaexplorer.core.model.MoonSample
import com.radustavila.nasaexplorer.core.model.NightDarkness
import com.radustavila.nasaexplorer.core.model.ObservingWindow
import com.radustavila.nasaexplorer.core.model.StargazingNight
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Scores a night for observing from its cloud forecast, its darkness and the Moon.
 *
 * Every dark hour (one per [MoonSample]) gets a clarity from the forecast step nearest to it: total
 * cloud cover, with extra weight on low cloud (thick and opaque, where high cloud is often thin
 * cirrus), and fog. A bright Moon above the horizon then takes up to half of it away. The night's
 * score is the best [SESSION_HOURS]-hour stretch, the length of a typical session: a night that
 * clears after midnight still scores well.
 *
 * The best window is the longest run of clear hours, whatever the Moon does, so the outlook can say
 * "clear from 21:00 to 02:00, Moon up" for a full-Moon night that scores only fair.
 */
object StargazingScorer {

    /** How long an observing session usually lasts. */
    const val SESSION_HOURS = 3

    /** Clarity (0–100) from which an hour counts as clear. */
    const val CLEAR_FROM = 60

    private const val TOTAL_CLOUD_WEIGHT = 0.75
    private const val LOW_CLOUD_WEIGHT = 0.25

    /** A full Moon above the horizon halves an hour's score. */
    private const val MOON_WEIGHT = 0.5

    /** Nautical darkness: most stars, but the faintest objects stay lost in the glow. */
    private const val NAUTICAL_FACTOR = 0.8

    private const val PERCENT = 100.0

    /** Half the 6-hourly spacing of the forecast's later days: farther steps say nothing. */
    private val MAX_STEP_DISTANCE: Duration = Duration.ofHours(3)

    fun score(night: DarkNight, forecast: CloudForecast?, now: Instant): StargazingNight {
        val darkEnd = night.darkEnd
        if (night.darkness == NightDarkness.NONE || night.darkStart == null || darkEnd == null) {
            return StargazingNight(night = night, score = null, bestWindow = null, meanCloudCover = null)
        }
        val sampleLength = Duration.ofMinutes(MoonSample.STEP_MINUTES)
        val hours = night.moonSamples.mapNotNull { sample ->
            val end = minOf(sample.time.plus(sampleLength), darkEnd)
            // Hours already over do not count; the hour under way counts from now.
            if (!end.isAfter(now)) return@mapNotNull null
            val start = maxOf(sample.time, now)
            val middle = start.plus(Duration.between(start, end).dividedBy(2))
            DarkHour(start = start, end = end, moon = sample, weather = forecast?.stepNear(middle))
        }
        val forecastHours = hours.count { it.weather != null }
        // A forecast that ends partway through the night cannot vouch for it.
        if (hours.isEmpty() || forecastHours * 2 < hours.size) {
            return StargazingNight(night = night, score = null, bestWindow = null, meanCloudCover = null)
        }
        val darknessFactor = if (night.darkness == NightDarkness.NAUTICAL) NAUTICAL_FACTOR else 1.0
        val scored = hours.map { hour -> hour.score(darknessFactor) }

        return StargazingNight(
            night = night,
            score = bestSessionScore(scored),
            bestWindow = bestWindow(scored),
            meanCloudCover = hours.mapNotNull { it.weather?.cloudCover }.average(),
        )
    }

    private fun bestSessionScore(hours: List<ScoredHour>): Int {
        val session = minOf(SESSION_HOURS, hours.size)
        return hours.windowed(session).maxOf { stretch -> stretch.map { it.score }.average() }
            .roundToInt()
            .coerceIn(0, PERCENT.toInt())
    }

    /** The longest run of clear hours; between equally long runs, the one scoring best. */
    private fun bestWindow(hours: List<ScoredHour>): ObservingWindow? {
        val runs = mutableListOf<List<ScoredHour>>()
        var current = mutableListOf<ScoredHour>()
        hours.forEach { hour ->
            if (hour.clarity >= CLEAR_FROM) {
                current += hour
            } else if (current.isNotEmpty()) {
                runs += current
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) runs += current
        val best = runs.maxWithOrNull(
            compareBy<List<ScoredHour>> { run -> Duration.between(run.first().hour.start, run.last().hour.end) }
                .thenBy { run -> run.sumOf { it.score } },
        ) ?: return null
        return ObservingWindow(
            start = best.first().hour.start,
            end = best.last().hour.end,
            meanCloudCover = best.mapNotNull { it.hour.weather?.cloudCover }.average(),
            moon = moonDuring(best.map { it.hour.moon.altitudeDegrees }),
        )
    }

    private fun moonDuring(altitudes: List<Double>): MoonDuringWindow {
        val up = altitudes.map { it > 0.0 }
        return when {
            up.all { it } -> MoonDuringWindow.UP
            up.none { it } -> MoonDuringWindow.DOWN
            up.first() && !up.last() -> MoonDuringWindow.SETS
            else -> MoonDuringWindow.RISES
        }
    }

    private fun CloudForecast.stepNear(time: Instant): CloudForecastStep? = steps
        .minByOrNull { abs(Duration.between(it.time, time).toMillis()) }
        ?.takeIf { abs(Duration.between(it.time, time).toMillis()) <= MAX_STEP_DISTANCE.toMillis() }

    private fun DarkHour.score(darknessFactor: Double): ScoredHour {
        val step = weather ?: return ScoredHour(this, clarity = 0.0, score = 0.0)
        val lowCloud = step.lowCloudCover ?: step.cloudCover
        val cloudiness = (TOTAL_CLOUD_WEIGHT * step.cloudCover + LOW_CLOUD_WEIGHT * lowCloud) / PERCENT
        val fog = (step.fogCover ?: 0.0) / PERCENT
        val clarity = PERCENT * (1.0 - cloudiness).coerceIn(0.0, 1.0) * (1.0 - fog).coerceIn(0.0, 1.0) * darknessFactor
        val moonFactor = if (moon.altitudeDegrees > 0.0) 1.0 - MOON_WEIGHT * moon.illuminatedFraction else 1.0
        return ScoredHour(this, clarity = clarity, score = clarity * moonFactor)
    }

    private data class DarkHour(
        val start: Instant,
        val end: Instant,
        val moon: MoonSample,
        val weather: CloudForecastStep?,
    )

    private data class ScoredHour(val hour: DarkHour, val clarity: Double, val score: Double)
}
