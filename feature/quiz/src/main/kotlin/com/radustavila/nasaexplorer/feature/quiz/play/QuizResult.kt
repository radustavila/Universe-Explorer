package com.radustavila.nasaexplorer.feature.quiz.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.adaptive.centeringPadding
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.model.QuizBadge
import com.radustavila.nasaexplorer.core.model.QuizOutcome
import com.radustavila.nasaexplorer.core.model.QuizTopic
import com.radustavila.nasaexplorer.feature.quiz.QuizShareText
import com.radustavila.nasaexplorer.feature.quiz.R
import com.radustavila.nasaexplorer.feature.quiz.appName
import com.radustavila.nasaexplorer.feature.quiz.description
import com.radustavila.nasaexplorer.feature.quiz.icon
import com.radustavila.nasaexplorer.feature.quiz.rememberTextSharer
import com.radustavila.nasaexplorer.feature.quiz.title

const val QUIZ_RESULT_TEST_TAG = "quiz-result"
const val QUIZ_SHARE_TEST_TAG = "quiz-share"

@Composable
internal fun QuizResult(
    uiState: QuizPlayUiState,
    outcome: QuizOutcome,
    onDone: () -> Unit,
    onPracticeAgain: (QuizTopic) -> Unit,
) {
    val mode = uiState.mode
    BoxWithConstraints {
        val sidePadding = centeringPadding(containerWidth = maxWidth) + 16.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(QUIZ_RESULT_TEST_TAG),
            contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "score") {
                Text(
                    text = stringResource(R.string.quiz_result_score, outcome.score, outcome.total),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item(key = "squares") { ResultSquares(uiState.results) }
            item(key = "message") {
                Text(
                    text = stringResource(resultMessage(outcome)),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            if (mode is QuizMode.Daily && outcome.streak > 0) {
                item(key = "streak") {
                    Text(
                        text = pluralStringResource(R.plurals.quiz_result_streak, outcome.streak, outcome.streak),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (outcome.newBadges.isNotEmpty()) {
                item(key = "badges") { NewBadges(outcome.newBadges) }
            }
            item(key = "actions") {
                ResultActions(uiState = uiState, outcome = outcome, onDone = onDone, onPracticeAgain = onPracticeAgain)
            }
        }
    }
}

@Composable
private fun ResultActions(
    uiState: QuizPlayUiState,
    outcome: QuizOutcome,
    onDone: () -> Unit,
    onPracticeAgain: (QuizTopic) -> Unit,
) {
    val resources = LocalResources.current
    val context = LocalContext.current
    val share = rememberTextSharer()
    val mode = uiState.mode
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val text = when (mode) {
                    is QuizMode.Daily -> QuizShareText.daily(resources, context.appName, mode.date, uiState.results)
                    is QuizMode.Practice ->
                        QuizShareText.practice(resources, context.appName, mode.topic, outcome.score, outcome.total)
                    null -> return@Button
                }
                share(text)
            },
            modifier = Modifier.fillMaxWidth().testTag(QUIZ_SHARE_TEST_TAG),
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.quiz_share))
        }
        if (mode is QuizMode.Practice) {
            OutlinedButton(onClick = { onPracticeAgain(mode.topic) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.quiz_result_practice_again))
            }
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.quiz_result_done))
        }
    }
}

private fun resultMessage(outcome: QuizOutcome): Int = when {
    outcome.total > 0 && outcome.score == outcome.total -> R.string.quiz_result_perfect
    outcome.score * 2 > outcome.total -> R.string.quiz_result_good
    else -> R.string.quiz_result_okay
}

/** One square per answer, as in the share text; decorative, the score above says it all. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ResultSquares(results: List<Boolean>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        results.forEach { right ->
            Box(
                Modifier
                    .size(20.dp)
                    .background(
                        if (right) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}

@Composable
private fun NewBadges(badges: Set<QuizBadge>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.quiz_result_new_badges))
            badges.sortedBy { it.ordinal }.forEach { badge ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(badge.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(badge.title()), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(badge.description()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
