package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.stargazing.CloudForecastRepository
import com.radustavila.nasaexplorer.core.domain.stargazing.DarkSkyCalculator
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingAlertNotifier
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingAlertRepository
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingAlertScheduler
import com.radustavila.nasaexplorer.core.model.CloudForecast
import com.radustavila.nasaexplorer.core.model.CloudForecastStep
import com.radustavila.nasaexplorer.core.model.DarkNight
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.MoonSample
import com.radustavila.nasaexplorer.core.model.NightDarkness
import com.radustavila.nasaexplorer.core.model.StargazingAlert
import com.radustavila.nasaexplorer.core.model.StargazingAlertSettings
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** One stored forecast for every place; [refreshResult] answers every refresh. */
class FakeCloudForecastRepository(initial: CloudForecast? = null) : CloudForecastRepository {

    val forecast = MutableStateFlow(initial)

    var refreshResult: DataResult<Unit> = DataResult.Success(Unit)

    val refreshRequests = mutableListOf<GeoCoordinate>()

    override fun observeForecast(observer: GeoCoordinate): Flow<CloudForecast?> = forecast

    override suspend fun refreshForecast(observer: GeoCoordinate): DataResult<Unit> {
        refreshRequests += observer
        return refreshResult
    }
}

/** Returns the first `count` of [nights], whatever the place and time. */
class FakeDarkSkyCalculator(var nights: List<DarkNight> = emptyList()) : DarkSkyCalculator {
    override fun nights(observer: GeoCoordinate, now: Instant, count: Int): List<DarkNight> = nights.take(count)
}

class FakeStargazingAlertRepository(initial: StargazingAlertSettings = StargazingAlertSettings()) :
    StargazingAlertRepository {

    private val state = MutableStateFlow(initial)

    var alertedUntilValue: Instant? = null

    override val settings: Flow<StargazingAlertSettings> = state

    override suspend fun setEnabled(enabled: Boolean) {
        state.update { it.copy(enabled = enabled) }
    }

    override suspend fun setMinimumScore(score: Int) {
        state.update { it.copy(minimumScore = score) }
    }

    override suspend fun alertedUntil(): Instant? = alertedUntilValue

    override suspend fun setAlertedUntil(until: Instant?) {
        alertedUntilValue = until
    }
}

/** Records what would have been posted instead of touching the system tray. */
class FakeStargazingAlertNotifier : StargazingAlertNotifier {

    var notified: List<StargazingAlert> = emptyList()
        private set

    override suspend fun notifyClearNight(alert: StargazingAlert) {
        notified = notified + alert
    }
}

class FakeStargazingAlertScheduler : StargazingAlertScheduler {

    var isPeriodicCheckScheduled = false
        private set

    override fun ensurePeriodicCheck() {
        isPeriodicCheckScheduled = true
    }

    override fun cancelPeriodicCheck() {
        isPeriodicCheckScheduled = false
    }
}

/** Builders for nights and cloud forecasts with round, readable numbers. */
object TestStargazing {

    /**
     * A night dark from [darkStart] for [darkHours] hours, the Moon at [moonAltitude] degrees and
     * [moonIlluminated] lit all along. Sunset and sunrise sit 90 minutes outside the dark part.
     */
    fun darkNight(
        darkStart: Instant = Instant.parse("2026-09-10T20:00:00Z"),
        darkHours: Long = 8,
        moonAltitude: Double = -20.0,
        moonIlluminated: Double = 0.0,
        darkness: NightDarkness = NightDarkness.ASTRONOMICAL,
        moonAltitudes: List<Double> = List(darkHours.toInt()) { moonAltitude },
    ): DarkNight {
        val darkEnd = darkStart.plus(Duration.ofHours(darkHours))
        return DarkNight(
            sunset = darkStart.minus(TWILIGHT),
            sunrise = darkEnd.plus(TWILIGHT),
            darkness = darkness,
            darkStart = darkStart,
            darkEnd = darkEnd,
            moonSamples = moonAltitudes.mapIndexed { hour, altitude ->
                MoonSample(
                    time = darkStart.plus(Duration.ofHours(hour.toLong())),
                    altitudeDegrees = altitude,
                    illuminatedFraction = moonIlluminated,
                )
            },
            moonIlluminatedFraction = moonIlluminated,
        )
    }

    /** Hourly steps from [from] for [hours] hours, each with the cover [cloudAt] gives for its hour index. */
    fun cloudForecast(
        from: Instant = Instant.parse("2026-09-10T12:00:00Z"),
        hours: Int = 48,
        coordinate: GeoCoordinate = GeoCoordinate(44.43, 26.1),
        fetchedAt: Instant = from,
        lowCloud: (Int) -> Double? = { null },
        fog: (Int) -> Double? = { null },
        cloudAt: (Int) -> Double = { 0.0 },
    ): CloudForecast = CloudForecast(
        coordinate = coordinate,
        steps = List(hours) { hour ->
            CloudForecastStep(
                time = from.plus(Duration.ofHours(hour.toLong())),
                cloudCover = cloudAt(hour),
                lowCloudCover = lowCloud(hour),
                fogCover = fog(hour),
            )
        },
        fetchedAt = fetchedAt,
    )

    private val TWILIGHT: Duration = Duration.ofMinutes(90)
}
