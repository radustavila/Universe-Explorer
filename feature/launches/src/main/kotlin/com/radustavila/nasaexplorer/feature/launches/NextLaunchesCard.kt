package com.radustavila.nasaexplorer.feature.launches

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.FreshnessLabel
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.feature.launches.component.CountdownText
import com.radustavila.nasaexplorer.feature.launches.component.LaunchStatusChip
import com.radustavila.nasaexplorer.feature.launches.component.MILLIS_PER_MINUTE
import com.radustavila.nasaexplorer.feature.launches.component.MILLIS_PER_SECOND
import com.radustavila.nasaexplorer.feature.launches.component.hasExactTime
import com.radustavila.nasaexplorer.feature.launches.component.netText
import com.radustavila.nasaexplorer.feature.launches.component.openUrl
import com.radustavila.nasaexplorer.feature.launches.component.rememberTickingClock
import java.time.Duration
import java.time.Instant

const val NEXT_LAUNCHES_CARD_TEST_TAG = "next-launches-card"
const val LAUNCH_ROW_TEST_TAG = "launch-row"
const val LAUNCH_LIVESTREAM_BUTTON_TEST_TAG = "launch-livestream-button"
const val LAUNCHES_SEE_ALL_TEST_TAG = "launches-see-all"

/**
 * The next few rocket launches with a live countdown, for the Today screen.
 *
 * It reads the launches cache through its own ViewModel; a launch opens its detail page
 * ([onLaunchClick]), "See all" the Launches screen ([onSeeAllClick]), and the play button the
 * livestream in the streaming app. It draws nothing while the cache is empty, so a fresh install
 * that has not synced yet (or never could) shows no empty card.
 */
@Composable
fun NextLaunchesCard(
    onSeeAllClick: () -> Unit,
    onLaunchClick: (launchId: String) -> Unit,
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
        onLaunchClick = onLaunchClick,
        onSeeAllClick = onSeeAllClick,
        onOpenUrl = context::openUrl,
        modifier = modifier,
        freshness = uiState.freshness,
    )
}

@Composable
internal fun NextLaunchesCardContent(
    launches: List<Launch>,
    now: () -> Instant,
    onLaunchClick: (launchId: String) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSeeAllClick: (() -> Unit)? = null,
    freshness: DataFreshness = DataFreshness(),
) {
    if (launches.isEmpty()) return

    Card(modifier = modifier.fillMaxWidth().testTag(NEXT_LAUNCHES_CARD_TEST_TAG)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 0.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.RocketLaunch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            SectionTitle(text = stringResource(R.string.launches_card_title), modifier = Modifier.weight(1f))
            if (onSeeAllClick != null) {
                TextButton(onClick = onSeeAllClick, modifier = Modifier.testTag(LAUNCHES_SEE_ALL_TEST_TAG)) {
                    Text(stringResource(R.string.launches_see_all))
                }
            }
        }

        launches.forEachIndexed { index, launch ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            LaunchRow(launch = launch, now = now, onLaunchClick = onLaunchClick, onOpenUrl = onOpenUrl)
        }
        FreshnessLabel(freshness, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun LaunchRow(
    launch: Launch,
    now: () -> Instant,
    onLaunchClick: (launchId: String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.launches_open_page),
                role = Role.Button,
                onClick = { onLaunchClick(launch.id) },
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
                LaunchStatusChip(status = launch.status)
                if (!launch.hasExactTime) {
                    // A rough date only (TBD, or known to the month): a ticking clock would be false precision.
                    Text(
                        text = netText(launch),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                } else {
                    CountdownText(net = launch.net, now = now)
                    // When it lifts off in the user's own clock, beside mission control's.
                    Text(
                        text = netText(launch),
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
            onLaunchClick = {},
            onOpenUrl = {},
            onSeeAllClick = {},
        )
    }
}
