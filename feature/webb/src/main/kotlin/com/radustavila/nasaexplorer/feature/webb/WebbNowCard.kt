package com.radustavila.nasaexplorer.feature.webb

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.WebbGap
import com.radustavila.nasaexplorer.core.model.WebbNow
import com.radustavila.nasaexplorer.core.model.WebbVisit
import com.radustavila.nasaexplorer.feature.webb.component.instrumentLine
import com.radustavila.nasaexplorer.feature.webb.component.subjectLine
import com.radustavila.nasaexplorer.feature.webb.component.timeNear
import com.radustavila.nasaexplorer.feature.webb.component.title

const val WEBB_NOW_CARD_TEST_TAG = "webb-now-card"

/**
 * The Universe hub's "Webb right now" card (F24): the visit scheduled now (or the slew or pause Webb
 * is in), its instrument and times, and the next three visits. [onOpen] opens the week;
 * [onTargetClick] searches the gallery for a target. Works offline from the last daily download.
 */
@Composable
fun WebbNowCard(
    onOpen: () -> Unit,
    onTargetClick: (query: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebbViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WebbNowCardContent(uiState, onOpen, onTargetClick, modifier)
}

@Composable
internal fun WebbNowCardContent(
    uiState: WebbUiState,
    onOpen: () -> Unit,
    onTargetClick: (query: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isLoaded) return
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(WEBB_NOW_CARD_TEST_TAG)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            CardHeader(onOpen)
            val now = uiState.now
            if (now == null) {
                BodyText(stringResource(R.string.webb_card_none), Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            } else {
                NowSection(now, onTargetClick)
                UpNext(now, onTargetClick)
                if (now.isStale) StaleNote(now, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun CardHeader(onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
        Icon(
            imageVector = Icons.Outlined.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        SectionTitle(stringResource(R.string.webb_card_title), modifier = Modifier.weight(1f))
        IconButton(onClick = onOpen) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.webb_card_open),
            )
        }
    }
}

@Composable
private fun NowSection(now: WebbNow, onTargetClick: (String) -> Unit) {
    val units = rememberUnitFormatter()
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val current = now.current
        if (current != null) {
            Text(
                stringResource(R.string.webb_scheduled_now),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            TargetName(current, onTargetClick)
            BodyText(current.instrumentLine())
            current.subjectLine()?.let { SecondaryText(it) }
            SecondaryText(
                stringResource(
                    R.string.webb_started_ends,
                    units.timeNear(current.start, now.now),
                    units.timeNear(current.end, now.now),
                ),
            )
        } else {
            GapText(now, units)
        }
    }
}

@Composable
private fun GapText(now: WebbNow, units: UnitFormatter) {
    val next = now.upNext.firstOrNull()
    val headline = when (now.gap) {
        WebbGap.SLEWING -> stringResource(R.string.webb_slewing)
        WebbGap.NOT_STARTED -> stringResource(R.string.webb_not_started, units.timeNear(now.scheduleStart, now.now))
        WebbGap.SCHEDULE_ENDED -> stringResource(R.string.webb_ended)
        WebbGap.IDLE, null -> stringResource(R.string.webb_idle)
    }
    Text(headline, style = MaterialTheme.typography.titleMedium)
    if (next != null && now.gap != WebbGap.NOT_STARTED) {
        SecondaryText(stringResource(R.string.webb_next_at, units.timeNear(next.start, now.now), next.title()))
    }
}

/** The target's name, which searches the gallery for it; calibration visits are not searchable. */
@Composable
internal fun TargetName(visit: WebbVisit, onTargetClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val query = visit.searchQuery
    val title = visit.title()
    if (query.isNullOrBlank()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.clickable(
            onClickLabel = stringResource(R.string.webb_search_target, title),
            role = Role.Button,
        ) { onTargetClick(query) },
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun UpNext(now: WebbNow, onTargetClick: (String) -> Unit) {
    if (now.upNext.isEmpty()) return
    val units = rememberUnitFormatter()
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    Text(
        stringResource(R.string.webb_up_next),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    now.upNext.forEach { visit ->
        val query = visit.searchQuery
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (query != null) Modifier.clickable { onTargetClick(query) } else Modifier)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                units.timeNear(visit.start, now.now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                listOf(visit.title(), visit.instrumentLine()).joinToString(stringResource(R.string.webb_separator)),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The note for a schedule more than eight days old: a newer week is missing. */
@Composable
internal fun StaleNote(now: WebbNow, modifier: Modifier = Modifier) {
    val units = rememberUnitFormatter()
    Text(
        stringResource(R.string.webb_stale, units.day(now.scheduleStart)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}

@Composable
private fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}

@Composable
private fun SecondaryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
