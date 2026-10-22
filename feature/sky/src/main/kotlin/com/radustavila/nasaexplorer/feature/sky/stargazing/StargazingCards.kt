package com.radustavila.nasaexplorer.feature.sky.stargazing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.FreshnessLabel
import com.radustavila.nasaexplorer.core.designsystem.stargazing.StargazingText
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.StargazingNight
import com.radustavila.nasaexplorer.core.model.StargazingRating
import com.radustavila.nasaexplorer.feature.sky.R
import com.radustavila.nasaexplorer.feature.sky.component.SkySection

const val STARGAZING_CARD_TEST_TAG = "stargazing-card"

fun stargazingNightTestTag(index: Int) = "stargazing-night-$index"

/** The stargazing outlook at the top of Sky tonight: tonight in full, then the next nights. */
@Composable
fun StargazingForecastCard(modifier: Modifier = Modifier, viewModel: StargazingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StargazingForecastCardContent(uiState = uiState, modifier = modifier)
}

@Composable
fun StargazingForecastCardContent(uiState: StargazingUiState, modifier: Modifier = Modifier) {
    // Sky tonight asks for the place itself; the card has nothing to add until there is one.
    if (!uiState.isLoaded || !uiState.hasLocation) return
    SkySection(
        title = stringResource(R.string.sky_stargazing_title),
        modifier = modifier.testTag(STARGAZING_CARD_TEST_TAG),
    ) {
        val nights = uiState.forecast?.nights.orEmpty()
        val tonight = nights.firstOrNull()
        if (tonight == null) {
            Text(stringResource(R.string.sky_stargazing_computing), style = MaterialTheme.typography.bodyMedium)
        } else {
            TonightRow(tonight, Modifier.testTag(stargazingNightTestTag(0)))
        }
        if (nights.size > 1) {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                nights.drop(1).forEachIndexed { index, night ->
                    LaterNight(night, Modifier.weight(1f).testTag(stargazingNightTestTag(index + 1)))
                }
            }
        }
        FreshnessLabel(uiState.freshness)
        MetNorwayCredit()
    }
}

@Composable
private fun TonightRow(night: StargazingNight, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val formatter = rememberUnitFormatter()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ScoreBadge(night, size = 56.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = night.rating?.let { rating ->
                    stringResource(R.string.sky_stargazing_tonight_rating, ratingLabel(rating))
                } ?: stringResource(R.string.sky_stargazing_tonight),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(StargazingText.summary(resources, formatter, night), style = MaterialTheme.typography.bodyMedium)
            night.meanCloudCover?.let { cover ->
                Text(
                    text = stringResource(R.string.sky_stargazing_cloud_cover, cover.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LaterNight(night: StargazingNight, modifier: Modifier = Modifier) {
    val formatter = rememberUnitFormatter()
    val evening = night.night.sunset ?: night.night.darkStart
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = evening?.let(formatter::weekday).orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScoreBadge(night, size = 40.dp)
        Text(
            text = night.rating?.let { ratingLabel(it) }
                ?: stringResource(R.string.sky_stargazing_no_score),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

/** The score in a disc coloured by its rating; a dash when the night has none. */
@Composable
private fun ScoreBadge(night: StargazingNight, size: Dp) {
    val score = night.score
    val rating = night.rating
    val (container, content) = ratingColors(rating)
    val description = if (score != null && rating != null) {
        stringResource(R.string.sky_stargazing_score_description, score, ratingLabel(rating))
    } else {
        stringResource(R.string.sky_stargazing_no_score)
    }
    Box(
        modifier = Modifier
            .size(size)
            .background(container, CircleShape)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = score?.toString() ?: stringResource(R.string.sky_no_event),
            style = if (size >= 56.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            color = content,
        )
    }
}

@Composable
private fun ratingColors(rating: StargazingRating?): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (rating) {
        StargazingRating.EXCELLENT -> colors.primary to colors.onPrimary
        StargazingRating.GOOD -> colors.tertiaryContainer to colors.onTertiaryContainer
        StargazingRating.FAIR -> colors.secondaryContainer to colors.onSecondaryContainer
        StargazingRating.POOR, null -> colors.surfaceVariant to colors.onSurfaceVariant
    }
}

@Composable
internal fun ratingLabel(rating: StargazingRating): String = stringResource(StargazingText.ratingRes(rating))

@Composable
internal fun BodyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

/** MET Norway's CC BY 4.0 credit, linked as its terms ask. */
@Composable
internal fun MetNorwayCredit(modifier: Modifier = Modifier) {
    val credit = stringResource(R.string.sky_stargazing_credit)
    val linkColor = MaterialTheme.colorScheme.primary
    Text(
        text = buildAnnotatedString {
            withLink(
                LinkAnnotation.Url(
                    url = StargazingText.CREDIT_URL,
                    styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                ),
            ) { append(credit) }
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}
