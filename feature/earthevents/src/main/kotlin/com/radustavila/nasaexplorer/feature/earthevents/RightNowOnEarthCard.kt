package com.radustavila.nasaexplorer.feature.earthevents

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
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.model.EarthEventCategory

const val RIGHT_NOW_ON_EARTH_CARD_TEST_TAG = "right-now-on-earth-card"

/**
 * "Right now on Earth" for the Today screen: how many wildfires, storms, volcanoes... are open, from
 * the events cache. Draws nothing while the cache is empty, so a fresh install that has not synced
 * yet shows no empty card.
 */
@Composable
fun RightNowOnEarthCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RightNowOnEarthViewModel = hiltViewModel(),
) {
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    RightNowOnEarthCardContent(counts = counts, onClick = onClick, modifier = modifier)
}

@Composable
internal fun RightNowOnEarthCardContent(
    counts: List<Pair<EarthEventCategory, Int>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (counts.isEmpty()) return

    Card(onClick = onClick, modifier = modifier.fillMaxWidth().testTag(RIGHT_NOW_ON_EARTH_CARD_TEST_TAG)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    SectionTitle(stringResource(R.string.earth_events_card_title))
                }
                counts.take(MAX_LINES).forEach { (category, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryDot(category)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = pluralStringResource(category.countRes, count, count),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                val others = counts.drop(MAX_LINES).sumOf { it.second }
                if (others > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.earth_events_count_others, others, others),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.earth_events_card_open),
            )
        }
    }
}

private const val MAX_LINES = 3
