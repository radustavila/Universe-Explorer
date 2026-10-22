package com.radustavila.nasaexplorer.feature.news

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.model.NewsItem
import com.radustavila.nasaexplorer.core.model.NewsKind
import java.time.Instant

fun newsRowTestTag(id: String) = "news-row-$id"

const val NEWS_LIVE_BANNER_TEST_TAG = "news-live-banner"

/**
 * One item in a list: picture on the left (a play mark on videos), then kind and age, title and
 * the picture's credit. [compact] is the Today card's smaller form, without the credit line, which
 * the item's own page and the News list show.
 */
@Composable
internal fun NewsRow(
    item: NewsItem,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(newsRowTestTag(item.id)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NewsThumbnail(
            item = item,
            width = if (compact) COMPACT_THUMBNAIL_WIDTH else THUMBNAIL_WIDTH,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KindAndAge(item, now)
            Text(
                text = item.title,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact) {
                item.imageCredit?.let { credit ->
                    Text(
                        text = stringResource(R.string.news_credit, credit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun KindAndAge(item: NewsItem, now: Instant, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (item.isLive) {
            LiveBadge()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = stringResource(
                R.string.news_kind_and_age,
                stringResource(item.kind.labelRes),
                item.publishedAt.formatAge(now),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NewsThumbnail(item: NewsItem, width: Dp) {
    Box(
        modifier = Modifier
            .size(width = width, height = width * THUMBNAIL_HEIGHT_RATIO)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        NasaAsyncImage(
            url = item.imageUrl,
            contentDescription = null,
            fallbackIcon = item.kind.icon,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.kind == NewsKind.VIDEO && item.imageUrl != null) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(2.dp),
            )
        }
    }
}

/** A small red "LIVE" mark, as video sites draw it. */
@Composable
internal fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.news_live_badge).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .background(LIVE_RED, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** The first live stream in the feed, above everything else: "Live now" and the stream's title. */
@Composable
internal fun LiveNowBanner(
    item: NewsItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag(NEWS_LIVE_BANNER_TEST_TAG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NewsThumbnail(item = item, width = COMPACT_THUMBNAIL_WIDTH)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveBadge()
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.news_live_now),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val THUMBNAIL_WIDTH = 112.dp
private val COMPACT_THUMBNAIL_WIDTH = 88.dp
private const val THUMBNAIL_HEIGHT_RATIO = 0.66f
private val LIVE_RED = Color(0xFFD32F2F)
