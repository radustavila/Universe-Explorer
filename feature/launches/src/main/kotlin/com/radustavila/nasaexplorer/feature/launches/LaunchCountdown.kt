package com.radustavila.nasaexplorer.feature.launches

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.radustavila.nasaexplorer.core.model.Launch
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** How many launches the card lists. */
internal const val NEXT_LAUNCH_COUNT = 3

/**
 * A launch stays on the card for a while after its target time, so a lift-off is seen counting up
 * ("T+") instead of vanishing the second it happens.
 */
internal val LIFTOFF_GRACE: Duration = Duration.ofHours(1)

/**
 * The launches worth showing at [now]: the cache is only refreshed hourly at most, so entries whose
 * time has long passed are dropped locally.
 */
internal fun List<Launch>.nextLaunches(now: Instant): List<Launch> =
    filter { launch -> launch.net > now - LIFTOFF_GRACE }
        .sortedBy(Launch::net)
        .take(NEXT_LAUNCH_COUNT)

/** Time left to (or, after lift-off, since) a launch: whole days, and the rest as `hh:mm:ss`. */
internal data class Countdown(val afterLiftoff: Boolean, val days: Long, val clock: String) {
    /** Mission control's sign: `T-` before lift-off, `T+` after. */
    val sign: String get() = if (afterLiftoff) "+" else "-"
}

/**
 * Mission-control style countdown to [net]; worded by [text]: `T-2d 03:04:05`, `T-03:04:05` inside
 * a day, `T+00:12:30` after lift-off.
 */
internal fun countdown(net: Instant, now: Instant): Countdown {
    val remaining = Duration.between(now, net)
    // Duration.toHoursPart() and friends need API 31; plain arithmetic works from the min SDK.
    val totalSeconds = remaining.abs().seconds
    val clock = String.format(
        Locale.ROOT,
        "%02d:%02d:%02d",
        totalSeconds % SECONDS_PER_DAY / SECONDS_PER_HOUR,
        totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE,
        totalSeconds % SECONDS_PER_MINUTE,
    )
    return Countdown(afterLiftoff = remaining.isNegative, days = totalSeconds / SECONDS_PER_DAY, clock = clock)
}

@Composable
internal fun Countdown.text(): String =
    if (days > 0) {
        pluralStringResource(R.plurals.launches_countdown_days, days.toInt(), sign, days.toInt(), clock)
    } else {
        stringResource(R.string.launches_countdown, sign, clock)
    }

/** Launch Library 2 names launches "Rocket | Mission"; the card shows the rocket on its own line. */
internal val Launch.missionName: String
    get() = name.substringAfter(" | ", missingDelimiterValue = name)

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_DAY = 86_400L
