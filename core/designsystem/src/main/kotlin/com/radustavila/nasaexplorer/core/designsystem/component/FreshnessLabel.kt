package com.radustavila.nasaexplorer.core.designsystem.component

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.radustavila.nasaexplorer.core.designsystem.R
import com.radustavila.nasaexplorer.core.designsystem.util.formatDateTime
import com.radustavila.nasaexplorer.core.model.DataFreshness
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

const val FRESHNESS_LABEL_TEST_TAG = "freshness-label"

/**
 * One line saying how current the data on screen is: "Updated 5 min. ago", "Updated 2 hr. ago. The
 * last refresh failed." or, without a connection, "Offline · showing data from 14:03". Every data
 * screen shows it in the same words, fed by `ObserveDataFreshnessUseCase`.
 *
 * Draws nothing until the source has synced once: the screen's loading or empty state speaks then.
 * The relative time moves on by itself while the line is on screen.
 */
@Composable
fun FreshnessLabel(
    freshness: DataFreshness,
    modifier: Modifier = Modifier,
) {
    val updatedAt = freshness.lastUpdatedAt ?: return
    val now by rememberMinuteClock()
    val text = when {
        freshness.isOffline -> stringResource(R.string.freshness_offline, updatedAt.formatOfflineTime(now))
        freshness.lastRefreshFailed -> stringResource(R.string.freshness_updated_failed, updatedAt.formatSince(now))
        else -> stringResource(R.string.freshness_updated, updatedAt.formatSince(now))
    }
    val icon = when {
        freshness.isOffline -> Icons.Outlined.CloudOff
        freshness.lastRefreshFailed -> Icons.Outlined.SyncProblem
        else -> Icons.Outlined.Update
    }
    Row(
        modifier = modifier.testTag(FRESHNESS_LABEL_TEST_TAG),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "just now", "5 min. ago", "3 days ago" (localised), then the date itself after a week. */
@Composable
private fun Instant.formatSince(now: Instant): String {
    val age = Duration.between(this, now)
    return when {
        age < Duration.ofMinutes(1) -> stringResource(R.string.freshness_just_now)
        age < Duration.ofDays(RELATIVE_DAYS) -> DateUtils.getRelativeTimeSpanString(
            toEpochMilli(),
            now.toEpochMilli(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()

        else -> formatDateTime()
    }
}

/** The time alone when the data is from today, the date and time otherwise. */
private fun Instant.formatOfflineTime(now: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    if (atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) {
        timeFormatter.format(atZone(zone))
    } else {
        formatDateTime(zone)
    }

/** The current time, moved on at every minute boundary while the app is at least started. */
@Composable
private fun rememberMinuteClock(): State<Instant> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(initialValue = Instant.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = Instant.now()
                delay(MINUTE_MILLIS - System.currentTimeMillis() % MINUTE_MILLIS)
            }
        }
    }
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private const val RELATIVE_DAYS = 7L
private const val MINUTE_MILLIS = 60_000L
