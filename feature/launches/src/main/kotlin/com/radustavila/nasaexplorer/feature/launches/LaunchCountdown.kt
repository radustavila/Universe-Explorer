package com.radustavila.nasaexplorer.feature.launches

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

/**
 * Mission-control style countdown to [net]: `T-2d 03:04:05`, `T-03:04:05` inside a day, `T+00:12:30`
 * after lift-off.
 */
internal fun countdownText(net: Instant, now: Instant): String {
    val remaining = Duration.between(now, net)
    val sign = if (remaining.isNegative) "+" else "-"
    // Duration.toHoursPart() and friends need API 31; plain arithmetic works from the min SDK.
    val totalSeconds = remaining.abs().seconds
    val days = totalSeconds / SECONDS_PER_DAY
    val clock = String.format(
        Locale.ROOT,
        "%02d:%02d:%02d",
        totalSeconds % SECONDS_PER_DAY / SECONDS_PER_HOUR,
        totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE,
        totalSeconds % SECONDS_PER_MINUTE,
    )
    return if (days > 0) "T$sign${days}d $clock" else "T$sign$clock"
}

/** Launch Library 2 names launches "Rocket | Mission"; the card shows the rocket on its own line. */
internal val Launch.missionName: String
    get() = name.substringAfter(" | ", missingDelimiterValue = name)

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_DAY = 86_400L
