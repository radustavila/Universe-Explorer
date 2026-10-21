package com.radustavila.nasaexplorer.feature.widget.launch

import android.content.res.Resources
import androidx.annotation.StringRes
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.feature.widget.R
import com.radustavila.nasaexplorer.feature.widget.WidgetFormat
import java.time.Duration
import java.time.Instant

/** Everything the next-launch widget shows, already in words. */
internal data class NextLaunchWidgetState(
    /** The mission, without the rocket Launch Library 2 prefixes it with. */
    val mission: String,
    /** "SpaceX · Falcon 9 Block 5", or just the rocket when the provider is unknown. */
    val vehicle: String,
    /** The target time: "14:30" today, "Tue 21 Oct 14:30" otherwise. */
    val net: String,
    /** Minute-resolution countdown, "in 3 h 20 m"; a fallback where the live one is not shown. */
    val countdown: String,
    /**
     * Lift-off time while it is less than [LIVE_COUNTDOWN_WINDOW] ahead: the widget shows a
     * countdown that ticks on the launcher by itself. Null otherwise.
     */
    val liveCountdownTo: Instant?,
    /** "Go", "TBC", …; null when the status says nothing useful. */
    val status: String?,
    val contentDescription: String,
)

/**
 * Launches stay on the widget this long after their target time, so a lift-off does not vanish the
 * second it happens; the same grace the Today card gives.
 */
internal val LIFTOFF_GRACE: Duration = Duration.ofHours(1)

/**
 * The launcher's chronometer counts in hours above an hour ("27:04:10"), which reads well up to a
 * day; further out the minute text is enough, refreshed with the widget.
 */
internal val LIVE_COUNTDOWN_WINDOW: Duration = Duration.ofHours(24)

/** The soonest launch not long past, in words; null when none is cached. */
internal fun nextLaunchWidgetState(
    launches: List<Launch>,
    now: Instant,
    format: WidgetFormat,
    resources: Resources,
): NextLaunchWidgetState? {
    val launch = launches.filter { it.net.isAfter(now.minus(LIFTOFF_GRACE)) }.minByOrNull { it.net } ?: return null
    val mission = launch.name.substringAfter(" | ", missingDelimiterValue = launch.name)
    val vehicle = listOfNotNull(launch.provider, launch.rocket.takeIf(String::isNotBlank))
        .distinct()
        .joinToString(separator = " · ")
    val untilLiftoff = Duration.between(now, launch.net)
    val countdown = if (untilLiftoff.isNegative || untilLiftoff.isZero) {
        resources.getString(R.string.next_launch_widget_liftoff_time)
    } else {
        format.timeUntil(launch.net, now)
    }
    val net = format.dayAndTime(launch.net, now)
    val status = statusLabel(launch.status)?.let(resources::getString)

    return NextLaunchWidgetState(
        mission = mission,
        vehicle = vehicle,
        net = net,
        countdown = countdown,
        liveCountdownTo = launch.net.takeIf { !untilLiftoff.isNegative && untilLiftoff < LIVE_COUNTDOWN_WINDOW },
        status = status,
        contentDescription = listOfNotNull(
            mission,
            vehicle.takeIf(String::isNotEmpty),
            resources.getString(R.string.next_launch_widget_net_description, net, countdown),
            status,
        ).joinToString(separator = ". "),
    )
}

@StringRes
private fun statusLabel(status: LaunchStatus): Int? = when (status) {
    LaunchStatus.GO -> R.string.next_launch_widget_status_go
    LaunchStatus.TBC -> R.string.next_launch_widget_status_tbc
    LaunchStatus.TBD -> R.string.next_launch_widget_status_tbd
    LaunchStatus.HOLD -> R.string.next_launch_widget_status_hold
    LaunchStatus.IN_FLIGHT -> R.string.next_launch_widget_status_in_flight
    LaunchStatus.SUCCESS, LaunchStatus.FAILURE, LaunchStatus.PARTIAL_FAILURE, LaunchStatus.UNKNOWN -> null
}
