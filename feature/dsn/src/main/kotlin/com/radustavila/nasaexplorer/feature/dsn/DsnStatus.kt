package com.radustavila.nasaexplorer.feature.dsn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.component.MessageState
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.DsnSnapshot

/* How current the network's state is: live, unavailable (with the last good state), or not there yet. */

@Composable
internal fun NoSnapshot(uiState: DsnUiState) {
    when {
        uiState.freshness.isOffline -> OfflineState(
            title = stringResource(R.string.dsn_offline_title),
            description = stringResource(R.string.dsn_offline_description),
        )

        uiState.freshness.lastRefreshFailed -> MessageState(
            icon = Icons.Outlined.ErrorOutline,
            title = stringResource(R.string.dsn_unavailable_title),
            description = stringResource(R.string.dsn_unavailable_description),
        )

        else -> Connecting()
    }
}

@Composable
private fun Connecting(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.dsn_loading), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun LiveStatus(snapshot: DsnSnapshot, uiState: DsnUiState) {
    val units = rememberUnitFormatter()
    val freshness = uiState.freshness
    val (text, color) = when {
        freshness.isOffline ->
            stringResource(R.string.dsn_status_offline, units.dateTime(snapshot.observedAt)) to
                MaterialTheme.colorScheme.outline
        freshness.lastRefreshFailed ->
            stringResource(R.string.dsn_status_unavailable, units.dateTime(snapshot.observedAt)) to
                MaterialTheme.colorScheme.error
        else -> stringResource(R.string.dsn_status_live, units.time(snapshot.observedAt)) to LIVE_GREEN
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
        val inContact = snapshot.activeLinks.map { it.link.spacecraft.code }.distinct().size
        Text(
            text = pluralStringResource(R.plurals.dsn_in_contact_count, inContact, inContact),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.dsn_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The status dot while the feed is live; the theme has no "success" colour. */
internal val LIVE_GREEN = Color(0xFF4CAF50)
