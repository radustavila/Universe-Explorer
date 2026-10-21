package com.radustavila.nasaexplorer.feature.launches

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

const val NEXT_LAUNCHES_CARD_TEST_TAG = "next-launches-card"
const val LAUNCH_ROW_TEST_TAG = "launch-row"
const val LAUNCH_LIVESTREAM_BUTTON_TEST_TAG = "launch-livestream-button"

/**
 * The next few rocket launches with a live countdown, for the Today screen.
 *
 * Self-contained: it reads the launches cache through its own ViewModel and opens links in the
 * browser or the streaming app. It draws nothing while the cache is empty, so a fresh install that
 * has not synced yet (or never could) shows no empty card.
 */
@Composable
fun NextLaunchesCard(
    modifier: Modifier = Modifier,
    viewModel: NextLaunchesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (uiState.launches.isEmpty()) return

    // Which launches are listed changes on the scale of minutes; only the countdowns tick each
    // second, and they read the clock themselves so the rest of the card stays put.
    val minute by rememberTickingClock(MILLIS_PER_MINUTE)
    val second = rememberTickingClock(MILLIS_PER_SECOND)
    val launches = remember(uiState.launches, minute) { uiState.launches.nextLaunches(minute) }
    val context = LocalContext.current

    NextLaunchesCardContent(
        launches = launches,
        now = { second.value },
        onOpenUrl = context::openUrl,
        modifier = modifier,
    )
}

@Composable
internal fun NextLaunchesCardContent(
    launches: List<Launch>,
    now: () -> Instant,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (launches.isEmpty()) return

    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }

    Card(modifier = modifier.fillMaxWidth().testTag(NEXT_LAUNCHES_CARD_TEST_TAG)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.RocketLaunch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.launches_card_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        launches.forEachIndexed { index, launch ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            LaunchRow(launch = launch, now = now, dateFormatter = dateFormatter, onOpenUrl = onOpenUrl)
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun LaunchRow(
    launch: Launch,
    now: () -> Instant,
    dateFormatter: DateTimeFormatter,
    onOpenUrl: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(
                    if (launch.livestreamUrl != null) {
                        R.string.launches_open_livestream
                    } else {
                        R.string.launches_open_page
                    },
                ),
                role = Role.Button,
                onClick = { onOpenUrl(launch.linkUrl) },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(LAUNCH_ROW_TEST_TAG),
    ) {
        NasaAsyncImage(
            url = launch.imageUrl,
            contentDescription = null,
            fallbackIcon = Icons.Outlined.RocketLaunch,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = launch.missionName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(launch.rocket, launch.pad).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusChip(status = launch.status)
                if (launch.status == LaunchStatus.TBD) {
                    // A TBD launch has only a rough date; a ticking clock would be false precision.
                    Text(
                        text = stringResource(
                            R.string.launches_net_date,
                            dateFormatter.format(launch.net.atZone(ZoneId.systemDefault())),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                } else {
                    CountdownText(net = launch.net, now = now)
                    // When it lifts off in the user's own clock, beside mission control's.
                    Text(
                        text = rememberUnitFormatter().dayAndTime(launch.net),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        val livestreamUrl = launch.livestreamUrl
        if (livestreamUrl != null) {
            IconButton(
                onClick = { onOpenUrl(livestreamUrl) },
                modifier = Modifier.testTag(LAUNCH_LIVESTREAM_BUTTON_TEST_TAG),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayCircle,
                    contentDescription = stringResource(
                        R.string.launches_watch_livestream,
                        launch.missionName,
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** The ticking `T-` clock; the only part of the card that recomposes every second. */
@Composable
private fun CountdownText(net: Instant, now: () -> Instant) {
    Text(
        text = countdown(net = net, now = now()).text(),
        style = MaterialTheme.typography.labelLarge,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun StatusChip(status: LaunchStatus) {
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
    StatusLabel(text = stringResource(status.labelRes), container = container, content = content)
}

@Composable
private fun StatusLabel(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private val LaunchStatus.labelRes: Int
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

/**
 * The current time, updated on every [periodMillis] boundary while the card is on screen and the
 * app is at least started: no ticking behind another app or a locked screen.
 */
@Composable
private fun rememberTickingClock(periodMillis: Long): State<Instant> {
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

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // Nothing on the device can open the link; the card stays as it is.
    }
}

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_MINUTE = 60_000L

@Preview
@Composable
private fun NextLaunchesCardPreview() {
    val now = Instant.parse("2026-10-19T10:00:00Z")
    NasaTheme {
        NextLaunchesCardContent(
            launches = listOf(
                Launch(
                    id = "1",
                    name = "Falcon 9 Block 5 | Bandwagon 5",
                    net = now + Duration.ofMinutes(30),
                    status = LaunchStatus.GO,
                    rocket = "Falcon 9 Block 5",
                    pad = "Space Launch Complex 40, Cape Canaveral SFS, FL, USA",
                    livestreamUrl = "https://x.com/i/broadcasts/1nxeLMddZlEJX",
                    pageUrl = "https://ll.thespacedevs.com/2.3.0/launches/1/",
                ),
                Launch(
                    id = "2",
                    name = "H3-24 | Martian Moons eXploration (MMX)",
                    net = now + Duration.ofHours(9),
                    status = LaunchStatus.TBC,
                    rocket = "H3-24",
                    pad = "Yoshinobu Launch Complex LP-2, Tanegashima Space Center, Japan",
                    pageUrl = "https://ll.thespacedevs.com/2.3.0/launches/2/",
                ),
            ),
            now = { now },
            onOpenUrl = {},
        )
    }
}
