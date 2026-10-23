package com.radustavila.nasaexplorer.feature.launches.component

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.radustavila.nasaexplorer.core.designsystem.notification.NotificationPermissionToggle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.core.model.NetPrecision
import com.radustavila.nasaexplorer.feature.launches.R
import com.radustavila.nasaexplorer.feature.launches.countdown
import com.radustavila.nasaexplorer.feature.launches.text
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

const val LAUNCH_REMINDER_BELL_TEST_TAG = "launch-reminder-bell"

/** Known to the minute or hour and not "to be determined": worth a ticking countdown. */
internal val Launch.hasExactTime: Boolean
    get() = netPrecision.isExact && status != LaunchStatus.TBD

/** Launch Library 2's status, as a small coloured label. */
@Composable
internal fun LaunchStatusChip(status: LaunchStatus, modifier: Modifier = Modifier) {
    val (container, content) = when (status) {
        LaunchStatus.GO, LaunchStatus.IN_FLIGHT, LaunchStatus.SUCCESS ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer

        LaunchStatus.FAILURE, LaunchStatus.PARTIAL_FAILURE ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

        LaunchStatus.HOLD ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer

        LaunchStatus.TBD, LaunchStatus.TBC, LaunchStatus.UNKNOWN ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    StatusLabel(text = stringResource(status.labelRes), container = container, content = content, modifier = modifier)
}

@Composable
private fun StatusLabel(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

internal val LaunchStatus.labelRes: Int
    get() = when (this) {
        LaunchStatus.GO -> R.string.launches_status_go
        LaunchStatus.TBD -> R.string.launches_status_tbd
        LaunchStatus.TBC -> R.string.launches_status_tbc
        LaunchStatus.HOLD -> R.string.launches_status_hold
        LaunchStatus.IN_FLIGHT -> R.string.launches_status_in_flight
        LaunchStatus.SUCCESS -> R.string.launches_status_success
        LaunchStatus.FAILURE -> R.string.launches_status_failure
        LaunchStatus.PARTIAL_FAILURE -> R.string.launches_status_partial_failure
        LaunchStatus.UNKNOWN -> R.string.launches_status_unknown
    }

/** The ticking `T-` clock; the only part of a launch row that recomposes every second. */
@Composable
internal fun CountdownText(net: Instant, now: () -> Instant, modifier: Modifier = Modifier) {
    Text(
        text = countdown(net = net, now = now()).text(),
        style = MaterialTheme.typography.labelLarge,
        fontFamily = FontFamily.Monospace,
        modifier = modifier,
    )
}

/**
 * When a launch lifts off, as exactly as Launch Library 2 knows it: the user's local day and time
 * for a firm NET, else only the day, month, quarter or year ("NET Q4 2026"). Coarse dates are read
 * in UTC, as LL2 sets them (midnight of the month's or quarter's last day).
 */
@Composable
internal fun netText(launch: Launch): String {
    val locale = LocalConfiguration.current.locales[0]
    val units = rememberUnitFormatter()
    if (launch.hasExactTime) return units.dayAndTime(launch.net)
    val utc = launch.net.atOffset(ZoneOffset.UTC)
    val date = when (launch.netPrecision) {
        NetPrecision.SECOND, NetPrecision.MINUTE, NetPrecision.HOUR, NetPrecision.UNKNOWN ->
            remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
                .format(launch.net.atZone(ZoneId.systemDefault()))

        NetPrecision.DAY, NetPrecision.WEEK ->
            remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }.format(utc)

        NetPrecision.MONTH -> remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }.format(utc)
        NetPrecision.QUARTER -> stringResource(R.string.launches_net_quarter, (utc.monthValue - 1) / 3 + 1, utc.year)
        NetPrecision.HALF_YEAR -> stringResource(R.string.launches_net_half, (utc.monthValue - 1) / 6 + 1, utc.year)
        NetPrecision.YEAR -> utc.year.toString()
    }
    return stringResource(R.string.launches_net_date, date)
}

/** The bell that turns one launch's reminder on and off. */
@Composable
internal fun LaunchReminderBell(
    toggle: NotificationPermissionToggle,
    launchName: String,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = toggle.onClick, modifier = modifier.testTag(LAUNCH_REMINDER_BELL_TEST_TAG)) {
        if (toggle.isOn) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = stringResource(R.string.launches_reminder_on, launchName),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.NotificationsNone,
                contentDescription = stringResource(R.string.launches_reminder_off, launchName),
            )
        }
    }
}

/**
 * The current time, updated on every [periodMillis] boundary while the caller is on screen and the
 * app is at least started: no ticking behind another app or a locked screen.
 */
@Composable
internal fun rememberTickingClock(periodMillis: Long): State<Instant> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(initialValue = Instant.now(), lifecycle, periodMillis) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = Instant.now()
                delay(periodMillis - System.currentTimeMillis() % periodMillis)
            }
        }
    }
}

/** Opens a stream or page in whatever app handles it (YouTube, X, a browser). */
internal fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // Nothing on the device can open the link.
    }
}

internal const val MILLIS_PER_SECOND = 1_000L
internal const val MILLIS_PER_MINUTE = 60_000L
