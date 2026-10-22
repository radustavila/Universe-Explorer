package com.radustavila.nasaexplorer.core.domain.stargazing

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.CloudForecast
import com.radustavila.nasaexplorer.core.model.DarkNight
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.StargazingAlert
import com.radustavila.nasaexplorer.core.model.StargazingAlertSettings
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * MET Norway's cloud forecast, cached per (rounded) place. Screens observe the cache; only
 * [refreshForecast] goes to the network, and only once the stored copy has expired.
 */
interface CloudForecastRepository {

    /** The stored forecast for [observer]'s rounded place, or null before the first download. */
    fun observeForecast(observer: GeoCoordinate): Flow<CloudForecast?>

    /**
     * Downloads [observer]'s forecast, unless the stored one is still within MET Norway's
     * `Expires` (then nothing is sent and the result is a success). A stored copy is revalidated
     * with `If-Modified-Since`.
     */
    suspend fun refreshForecast(observer: GeoCoordinate): DataResult<Unit>
}

/** When the nights get dark and where the Moon is, computed on the device. */
interface DarkSkyCalculator {

    /**
     * [count] nights in a row, starting with the one under way at [now] (or the coming one while
     * the Sun is up).
     */
    fun nights(observer: GeoCoordinate, now: Instant, count: Int): List<DarkNight>
}

/** The user's clear-night alert choices, and which night they were last alerted about. */
interface StargazingAlertRepository {

    val settings: Flow<StargazingAlertSettings>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setMinimumScore(score: Int)

    /** End of the night the last alert was for: a night starting before it was already announced. */
    suspend fun alertedUntil(): Instant?

    suspend fun setAlertedUntil(until: Instant?)
}

/** Posts the clear-night notification; stays silent when the platform does not allow it. */
interface StargazingAlertNotifier {
    suspend fun notifyClearNight(alert: StargazingAlert)
}

/** Runs the clear-night check in the background, only while the alert is on. */
interface StargazingAlertScheduler {
    fun ensurePeriodicCheck()
    fun cancelPeriodicCheck()
}
