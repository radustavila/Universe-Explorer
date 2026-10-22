package com.radustavila.nasaexplorer.feature.news.today

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
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.model.NewsItem
import com.radustavila.nasaexplorer.feature.news.LiveNowBanner
import com.radustavila.nasaexplorer.feature.news.NewsRow
import com.radustavila.nasaexplorer.feature.news.R
import com.radustavila.nasaexplorer.feature.news.rememberNewsItemOpener

const val LATEST_FROM_NASA_CARD_TEST_TAG = "latest-from-nasa-card"
const val LATEST_FROM_NASA_SEE_ALL_TEST_TAG = "latest-from-nasa-see-all"

/**
 * "Latest from NASA" for the Today screen: a live stream if one is on, then the three newest
 * stories, pictures or videos, each opening where it belongs (a Custom Tab, YouTube, or the Image
 * of the Day's screen). Draws nothing while the cache is empty, so a fresh install that has not
 * downloaded the feeds yet shows no empty card.
 */
@Composable
fun LatestFromNasaCard(
    onSeeAllClick: () -> Unit,
    onImageClick: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LatestFromNasaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LatestFromNasaCardContent(
        uiState = uiState,
        onItemClick = rememberNewsItemOpener(onImageClick),
        onSeeAllClick = onSeeAllClick,
        modifier = modifier,
    )
}

@Composable
internal fun LatestFromNasaCardContent(
    uiState: LatestFromNasaUiState,
    onItemClick: (NewsItem) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.live == null && uiState.latest.isEmpty()) return

    Card(modifier = modifier.fillMaxWidth().testTag(LATEST_FROM_NASA_CARD_TEST_TAG)) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Newspaper,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                SectionTitle(stringResource(R.string.news_card_title))
            }
            uiState.live?.let { live -> LiveNowBanner(item = live, onClick = { onItemClick(live) }) }
            uiState.latest.forEach { item ->
                NewsRow(item = item, now = uiState.now, onClick = { onItemClick(item) }, compact = true)
            }
            TextButton(
                onClick = onSeeAllClick,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag(LATEST_FROM_NASA_SEE_ALL_TEST_TAG),
            ) {
                Text(stringResource(R.string.news_card_see_all))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}
